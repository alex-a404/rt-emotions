import cv2
import mediapipe as mp
from deepface import DeepFace
import concurrent.futures
import math
from collections import deque
import json
from typing import List, Dict, Any
from confluent_kafka import Producer
import threading
import queue
import logging
import os
import streamlit as st
import time

st.title("Emotion Recognition")
st_frame = st.empty()

# ----------------- Logging -----------------
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s"
)
log = logging.getLogger("emotion_producer")

# -------------- MediaPipe Hands setup --------------
mp_hands = mp.solutions.hands
mp_drawing = mp.solutions.drawing_utils

hands = mp_hands.Hands(
    static_image_mode=False,
    max_num_hands=2,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)

# Added: MediaPipe Face Detection for quick verification of Haar detections
mp_face_detection = mp.solutions.face_detection
face_detector_mp = mp_face_detection.FaceDetection(min_detection_confidence=0.5)

# finger indices for counting
FINGER_TIPS = [8, 12, 16, 20]  # index, middle, ring, pinky
FINGER_PIPS = [6, 10, 14, 18]
THUMB_TIP = 4
THUMB_IP = 3


def count_fingers(hand_landmarks, hand_label):
    fingers = 0
    for tip_idx, pip_idx in zip(FINGER_TIPS, FINGER_PIPS):
        tip_y = hand_landmarks.landmark[tip_idx].y
        pip_y = hand_landmarks.landmark[pip_idx].y
        if tip_y < pip_y:
            fingers += 1
    thumb_tip_x = hand_landmarks.landmark[THUMB_TIP].x
    thumb_ip_x = hand_landmarks.landmark[THUMB_IP].x
    if hand_label == "Right":
        if thumb_tip_x < thumb_ip_x:
            fingers += 1
    else:
        if thumb_tip_x > thumb_ip_x:
            fingers += 1
    return fingers


# optional smoothing for finger counts (reduce flicker)
FINGER_SMOOTH_HISTORY = 5

# -------------- Face detection + DeepFace async setup --------------
face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
)
executor = concurrent.futures.ThreadPoolExecutor(max_workers=2)

tracked_faces = {}
next_track_id = 0

# parameters
REANALYZE_FRAMES = 30  # re-run DeepFace for a tracked face after this many frames
TRACKER_DISAPPEAR_FRAMES = 60  # remove track if not seen for this many frames
DISTANCE_MATCH_THRESHOLD = 100  # px - used to match new detections to existing tracks
HAND_FACE_MAP_THRESHOLD = 150  # px - max distance to map a hand to a face

frame_index = 0


def euclidean(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def centroid_of_bbox(bbox):
    x, y, w, h = bbox
    return (int(x + w / 2), int(y + h / 2))


def map_hands_to_faces(hands_positions: List, face_positions: List):
    pairs = []
    for h_idx, (n_fingers, hand_pos) in enumerate(hands_positions):
        for face_id, face_pos in face_positions:
            dist = euclidean(hand_pos, face_pos)
            pairs.append((dist, h_idx, face_id, n_fingers))

    pairs.sort(key=lambda x: x[0])

    assigned_hands = set()
    face_hand_map: Dict[Any, List[int]] = {fid: [] for fid, _ in face_positions}

    for _, h_idx, face_id, n_fingers in pairs:
        if h_idx in assigned_hands:
            continue
        if len(face_hand_map[face_id]) >= 2:
            continue
        face_hand_map[face_id].append(n_fingers)
        assigned_hands.add(h_idx)

    return face_hand_map


def analyze_face_async(track_id, face_roi_bgr):
    # convert roi to RGB for DeepFace
    try:
        roi = cv2.resize(face_roi_bgr, (224, 224))
        roi_rgb = cv2.cvtColor(roi, cv2.COLOR_BGR2RGB)
        result = DeepFace.analyze(roi_rgb, actions=["emotion"], enforce_detection=True)
        if isinstance(result, list) and len(result) > 0 and "emotion" in result[0]:
            emotions = result[0]["emotion"]
        elif isinstance(result, dict) and "emotion" in result:
            emotions = result["emotion"]
        else:
            emotions = {}
        if emotions:
            dominant = max(emotions, key=emotions.get)
        else:
            dominant = "unknown"
    except Exception:
        dominant = "not_face"
    return (track_id, dominant)


def submit_face_for_analysis(track_id, face_roi):
    if track_id not in tracked_faces:
        return
    tracked_faces[track_id]["processing"] = True
    fut = executor.submit(analyze_face_async, track_id, face_roi)

    def _callback(future):
        try:
            tid, dominant = future.result()
            if tid in tracked_faces:
                if dominant == "not_face":
                    try:
                        del tracked_faces[tid]
                    except KeyError:
                        pass
                else:
                    tracked_faces[tid]["emotion"] = dominant
                    tracked_faces[tid]["last_analyzed_frame"] = frame_index
                    tracked_faces[tid]["processing"] = False
        except Exception:
            if track_id in tracked_faces:
                tracked_faces[track_id]["processing"] = False
            log.exception("Analysis callback error")

    fut.add_done_callback(lambda f: _callback(f))


def read_config(path):
    config = {}
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if len(line) != 0 and line[0] != "#":
                parameter, value = line.strip().split("=", 1)
                config[parameter] = value.strip()
    return config


# ---------------- Kafka producer (single instance + background thread) ----------------
PRODUCER_QUEUE_MAX = 400
produce_queue = queue.Queue(maxsize=PRODUCER_QUEUE_MAX)
shutdown_event = threading.Event()
producer = None
producer_thread = None


def delivery_report(err, msg):
    if err is not None:
        log.warning(f"Delivery failed for key={msg.key()}: {err}")
    else:
        # successful delivery -> no-op or small debug
        pass


def producer_worker():
    global producer
    while not shutdown_event.is_set() or not produce_queue.empty():
        try:
            topic, key, value = produce_queue.get(timeout=0.05)
        except queue.Empty:
            if producer is not None:
                producer.poll(0)
            continue

        sent = False
        attempts = 0
        while not sent and attempts < 3:
            try:
                producer.produce(topic, key=key, value=value, callback=delivery_report)
                sent = True
            except BufferError:
                # librdkafka internal queue full; give it a chance to send
                if producer is not None:
                    producer.poll(0.01)
                attempts += 1
            except Exception:
                log.exception("Unexpected exception in producer_worker while producing")
                sent = True  # drop after logging to avoid infinite loop
        # service events so callbacks fire
        if producer is not None:
            producer.poll(0)
        produce_queue.task_done()


# ------------------- Main application -------------------

cap = cv2.VideoCapture(0)
if not cap.isOpened():
    raise RuntimeError("Could not open camera")

properties_path = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "camera_feed.properties"
)
config = read_config(properties_path)
# low-latency defaults (only if not set in config file)
config.setdefault("acks", "1")
config.setdefault("queue.buffering.max.ms", "10")
config.setdefault("socket.nagle.disable", "true")
config.setdefault("compression.type", "none")

# create single producer and start worker thread
producer = Producer(config)
producer_thread = threading.Thread(target=producer_worker, daemon=True)
producer_thread.start()

try:
    while True:
        ret, frame = cap.read()
        if not ret:
            break

        frame_index += 1
        h, w, _ = frame.shape

        # 1) Hand detection + finger counting
        frame_rgb_for_mp = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = hands.process(frame_rgb_for_mp)

        finger_count = []
        per_hand_smooth = {}
        hands_positions = []

        if results.multi_hand_landmarks and results.multi_handedness:
            for hand_landmarks, hand_handedness in zip(
                results.multi_hand_landmarks, results.multi_handedness
            ):
                hand_label = hand_handedness.classification[0].label
                fingers = count_fingers(hand_landmarks, hand_label)
                finger_count.append(fingers)

                wrist = hand_landmarks.landmark[0]
                x_pos = int(wrist.x * w)
                y_pos = int(wrist.y * h) - 30
                if y_pos < 20:
                    y_pos = int(wrist.y * h) + 30

                key = (round(x_pos / 40), round(y_pos / 40))
                if key not in per_hand_smooth:
                    per_hand_smooth[key] = deque(maxlen=FINGER_SMOOTH_HISTORY)
                per_hand_smooth[key].append(fingers)
                smoothed = int(
                    round(sum(per_hand_smooth[key]) / len(per_hand_smooth[key]))
                )

                hands_positions.append((smoothed, (x_pos, y_pos)))

                mp_drawing.draw_landmarks(
                    frame, hand_landmarks, mp_hands.HAND_CONNECTIONS
                )
                cv2.putText(
                    frame,
                    f"{hand_label}: {smoothed}",
                    (x_pos, y_pos),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.9,
                    (0, 255, 0),
                    2,
                    cv2.LINE_AA,
                )

        # 2) Face detection (Haar cascade)
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        detected = face_cascade.detectMultiScale(
            gray, scaleFactor=1.1, minNeighbors=6, minSize=(60, 60)
        )

        seen_track_ids = set()

        for x, y, fw, fh in detected:
            x0 = max(x, 0)
            y0 = max(y, 0)
            x1 = min(x + fw, w)
            y1 = min(y + fh, h)
            roi = frame[y0:y1, x0:x1].copy()
            if roi.size == 0:
                continue

            roi_rgb = cv2.cvtColor(roi, cv2.COLOR_BGR2RGB)
            mp_results = face_detector_mp.process(roi_rgb)
            if not (mp_results and mp_results.detections):
                continue

            cx, cy = centroid_of_bbox((x, y, fw, fh))

            best_tid = None
            best_dist = None
            for tid, info in tracked_faces.items():
                dist = euclidean((cx, cy), info["centroid"])
                if best_dist is None or dist < best_dist:
                    best_dist = dist
                    best_tid = tid

            if (
                best_tid is not None
                and best_dist is not None
                and best_dist < DISTANCE_MATCH_THRESHOLD
            ):
                tid = best_tid
                tracked_faces[tid]["bbox"] = (x, y, fw, fh)
                tracked_faces[tid]["centroid"] = (cx, cy)
                tracked_faces[tid]["last_seen_frame"] = frame_index
                seen_track_ids.add(tid)
            else:
                tid = next_track_id
                next_track_id += 1
                tracked_faces[tid] = {
                    "bbox": (x, y, fw, fh),
                    "centroid": (cx, cy),
                    "emotion": "neutral",
                    "processing": False,
                    "last_analyzed_frame": -9999,
                    "last_seen_frame": frame_index,
                }
                seen_track_ids.add(tid)

            info = tracked_faces.get(tid)
            if info is None:
                continue
            needs_analysis = (not info.get("processing", False)) and (
                frame_index - info.get("last_analyzed_frame", -9999) >= REANALYZE_FRAMES
            )
            if needs_analysis:
                pad = int(min(fw, fh) * 0.15)
                xa = max(x - pad, 0)
                ya = max(y - pad, 0)
                xb = min(x + fw + pad, w)
                yb = min(y + fh + pad, h)
                face_roi = frame[ya:yb, xa:xb].copy()
                submit_face_for_analysis(tid, face_roi)

        to_delete = []
        for tid, info in list(tracked_faces.items()):
            if frame_index - info.get("last_seen_frame", 0) > TRACKER_DISAPPEAR_FRAMES:
                to_delete.append(tid)
        for tid in to_delete:
            del tracked_faces[tid]

        faces_positions = []
        emotions_dict = {}

        for tid, info in tracked_faces.items():
            x, y, fw, fh = info["bbox"]
            emotion = info.get("emotion", "neutral")
            emotions_dict[tid] = emotion.upper()
            cv2.rectangle(frame, (x, y), (x + fw, y + fh), (0, 0, 255), 2)
            label = f"{emotion}"
            cv2.putText(
                frame, label, (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 0, 255), 2
            )
            faces_positions.append((tid, info["centroid"]))

        cv2.putText(
            frame,
            f"People: {len(tracked_faces)}",
            (10, 80),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.9,
            (255, 0, 0),
            2,
            cv2.LINE_AA,
        )

        # convert to RGB for Streamlit
        frame_rgb_display = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        st_frame.image(frame_rgb_display, channels="RGB")

        facemap = map_hands_to_faces(hands_positions, faces_positions)

        msg = []
        for tid, emotion in emotions_dict.items():
            msg.append({"id": tid, "emotion": emotion, "fingers": facemap.get(tid, [])})

        # include a timestamp for latency measurement
        payload = json.dumps(msg)

        # non-blocking enqueue; drop if queue full (keeps camera loop low-latency)
        try:
            produce_queue.put_nowait(("emotions.rt.v2", str(frame_index), payload))
        except queue.Full:
            # drop message - measure dropped count in real app if needed
            log.warning("Producer queue full: dropping message")

        # small sleep to yield CPU and allow Streamlit to serve updates
        time.sleep(0.02)

finally:
    # cleanup pipeline
    shutdown_event.set()
    # wait a short while for worker to drain
    if producer_thread is not None:
        producer_thread.join(timeout=2.0)

    # flush outstanding messages (only at shutdown)
    if producer is not None:
        try:
            producer.flush(5)
        except Exception:
            log.exception("Producer flush failed")

    cap.release()
    # no cv2.destroyAllWindows() because we don't open local windows
    hands.close()
    face_detector_mp.close()
    executor.shutdown(wait=False)
    log.info("Shutdown complete")
