FROM python:3.11-slim

# Install system dependencies for OpenCV, MediaPipe, camera and librdkafka
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxrender1 \
    libxext6 \
    ffmpeg \
    v4l-utils \
    librdkafka-dev \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Ensure pip is recent
RUN python -m pip install --upgrade pip

# Set working directory
WORKDIR /app

# Copy requirements and install Python dependencies
COPY recognition/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# Copy app code
COPY recognition/ /app
COPY configs/ /app/src

# Create a Streamlit config file (so it never shows first-run msg)
RUN mkdir -p /root/.streamlit && \
    echo "[browser]\n" \
         "gatherUsageStats = false\n" \
         "[server]\n" \
         "headless = true\n" \
    > /root/.streamlit/config.toml

# Expose Streamlit port
EXPOSE 8501

# Environment
ENV PYTHONUNBUFFERED=1

# Default command: run Streamlit and bind to 0.0.0.0 so host can reach it
CMD ["streamlit", "run", "src/emotion.py", "--server.port=8501", "--server.address=0.0.0.0"]
