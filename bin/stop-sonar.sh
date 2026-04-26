#!/usr/bin/env bash
# Stops the SonarQube container (data is preserved in sonar-data/).

set -euo pipefail

CONTAINER_NAME="sonarqube-qa-loop"

if ! docker inspect "$CONTAINER_NAME" &>/dev/null; then
    echo "Container $CONTAINER_NAME does not exist."
    exit 0
fi

STATUS="$(docker inspect -f '{{.State.Status}}' "$CONTAINER_NAME")"
if [[ "$STATUS" != "running" ]]; then
    echo "Container is already stopped (status: $STATUS)."
    exit 0
fi

docker stop "$CONTAINER_NAME"
echo "SonarQube stopped. Data is preserved in sonar-data/."
echo "Run bin/start-sonar.sh to restart."
