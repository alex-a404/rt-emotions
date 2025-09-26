-- source table
CREATE TABLE emotions (
  id INT,
  emotion STRING
) WITH (
  'connector' = 'kafka',
  'format' = 'json',
  'scan.startup.mode' = 'earliest-offset'
);

-- sink table
CREATE TABLE emotion_counts (
  emotion STRING,
  cnt BIGINT
) WITH (
  'connector' = 'print' -- writes results to stdout/log
);

-- continuous operation into sink table
INSERT INTO emotion_counts
SELECT
  emotion,
  COUNT(*) AS cnt
FROM emotions
GROUP BY emotion;
