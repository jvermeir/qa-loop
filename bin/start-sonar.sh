#!/usr/bin/env bash
# Starts a local SonarQube server in Docker with bind-mounted local folders.
# Run once; subsequent runs restart an existing stopped container.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SONAR_DATA_DIR="$SCRIPT_DIR/../sonar-data"

DATA_DIR="$SONAR_DATA_DIR/data"
LOGS_DIR="$SONAR_DATA_DIR/logs"
EXTENSIONS_DIR="$SONAR_DATA_DIR/extensions"
CONF_DIR="$SONAR_DATA_DIR/conf"

CONTAINER_NAME="sonarqube-qa-loop"
IMAGE="sonarqube:community"
PORT=9000

# ---------------------------------------------------------------------------
# Create folders
# ---------------------------------------------------------------------------
mkdir -p "$DATA_DIR" "$LOGS_DIR" "$EXTENSIONS_DIR" "$CONF_DIR"

# SonarQube runs as uid 1000 inside the container; the mounted dirs must be
# writable by that uid. On macOS (Docker Desktop) this is handled by the VM,
# but on Linux you may need: sudo chown -R 1000:1000 sonar-data/
echo "Folders:"
echo "  data:       $DATA_DIR"
echo "  logs:       $LOGS_DIR"
echo "  extensions: $EXTENSIONS_DIR"
echo "  conf:       $CONF_DIR"
echo ""

# ---------------------------------------------------------------------------
# Start or restart container
# ---------------------------------------------------------------------------
if docker inspect "$CONTAINER_NAME" &>/dev/null; then
    STATUS="$(docker inspect -f '{{.State.Status}}' "$CONTAINER_NAME")"
    if [[ "$STATUS" == "running" ]]; then
        echo "SonarQube is already running (container: $CONTAINER_NAME)."
        echo "Open http://localhost:$PORT"
        exit 0
    else
        echo "Restarting existing container ($STATUS → running) …"
        docker start "$CONTAINER_NAME"
    fi
else
    echo "Creating and starting container $CONTAINER_NAME …"
    docker run -d \
        --name "$CONTAINER_NAME" \
        -p "$PORT:9000" \
        -v "$DATA_DIR:/opt/sonarqube/data" \
        -v "$LOGS_DIR:/opt/sonarqube/logs" \
        -v "$EXTENSIONS_DIR:/opt/sonarqube/extensions" \
        -v "$CONF_DIR:/opt/sonarqube/conf" \
        "$IMAGE"
fi

# ---------------------------------------------------------------------------
# Wait for server to be ready
# ---------------------------------------------------------------------------
echo ""
echo "Waiting for SonarQube to become ready (this takes ~60s on first start) …"
MAX_WAIT=120
ELAPSED=0
until curl -sf "http://localhost:$PORT/api/system/status" \
        | grep -q '"status":"UP"' 2>/dev/null; do
    if [[ $ELAPSED -ge $MAX_WAIT ]]; then
        echo "Timed out after ${MAX_WAIT}s. Check logs: $LOGS_DIR"
        exit 1
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
    echo "  … ${ELAPSED}s"
done

echo ""
echo "SonarQube is up: http://localhost:$PORT"
echo "Default credentials: admin / admin  (you will be prompted to change on first login)"
