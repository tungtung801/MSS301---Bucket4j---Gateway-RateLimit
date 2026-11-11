#!/usr/bin/env bash
# Bắn 40 request liên tiếp qua Gateway (cổng 8080). Đổi sang 8081 để thử Bucket4j trực tiếp.
URL="${1:-http://localhost:8080/api/tickets}"
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -D /tmp/h.txt "$URL")
  remain=$(grep -i "x-ratelimit-remaining" /tmp/h.txt | tr -d '\r')
  retry=$(grep -i "retry-after" /tmp/h.txt | tr -d '\r')
  echo "request $i -> $code  $remain  $retry"
done
