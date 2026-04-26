#!/usr/bin/env bash
# Runs sonar-scanner in Docker against a target project directory.
#
# Usage:
#   ./bin/run-analysis.sh /path/to/project
#   ./bin/run-analysis.sh          # defaults to current directory
#
# Reads host_url, token, project_key, and scanner_args from config.toml.
# Override token via env: SONAR_TOKEN=xxx ./bin/run-analysis.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG="$ROOT_DIR/config.toml"
IMAGE="sonarsource/sonar-scanner-cli"

# ---------------------------------------------------------------------------
# Read config.toml
# ---------------------------------------------------------------------------
read_toml() {
    python3 - "$CONFIG" "$1" <<'EOF'
import sys
try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib
with open(sys.argv[1], "rb") as f:
    c = tomllib.load(f)
keys = sys.argv[2].split(".")
val = c
for k in keys:
    val = val[k]
if isinstance(val, list):
    print("\n".join(val))
else:
    print(val)
EOF
}

PROJECT_KEY="$(read_toml sonar.project_key)"
HOST_URL="$(read_toml sonar.host_url)"
TOKEN="${SONAR_TOKEN:-$(read_toml sonar.token)}"

# Read extra scanner_args into an array (one per line from read_toml)
EXTRA_ARGS=()
while IFS= read -r line; do
    EXTRA_ARGS+=("$line")
done < <(read_toml sonar.scanner_args 2>/dev/null || true)

# ---------------------------------------------------------------------------
# Validate
# ---------------------------------------------------------------------------
if [[ -z "$TOKEN" ]]; then
    echo "ERROR: No token configured."
    echo "  Run ./bin/setup-sonar.sh, then set token in config.toml, or:"
    echo "  SONAR_TOKEN=<token> ./bin/run-analysis.sh"
    exit 1
fi

# ---------------------------------------------------------------------------
# Target project directory
# ---------------------------------------------------------------------------
TARGET_DIR="${1:-$(pwd)}"
TARGET_DIR="$(cd "$TARGET_DIR" && pwd)"

if [[ ! -d "$TARGET_DIR" ]]; then
    echo "ERROR: Directory not found: $TARGET_DIR"
    exit 1
fi

# ---------------------------------------------------------------------------
# Translate host URL for Docker networking
#   localhost → host.docker.internal  (so the container can reach the host)
# ---------------------------------------------------------------------------
DOCKER_HOST_URL="${HOST_URL/localhost/host.docker.internal}"
DOCKER_HOST_URL="${DOCKER_HOST_URL/127.0.0.1/host.docker.internal}"

# ---------------------------------------------------------------------------
# Build docker run command
# ---------------------------------------------------------------------------
DOCKER_ARGS=(
    run --rm
    -e "SONAR_HOST_URL=$DOCKER_HOST_URL"
    -e "SONAR_TOKEN=$TOKEN"
    -v "$TARGET_DIR:/usr/src"
    "$IMAGE"
    -Dsonar.projectKey="$PROJECT_KEY"
    -Dsonar.sources=.
)

# Append extra args from config (filter out host/token if duplicated)
for arg in "${EXTRA_ARGS[@]}"; do
    [[ -z "$arg" ]] && continue
    [[ "$arg" == *sonar.host.url* ]] && continue
    [[ "$arg" == *sonar.token* ]]    && continue
    DOCKER_ARGS+=("$arg")
done

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
echo "=== sonar-scanner (Docker) ==="
echo "Project : $PROJECT_KEY"
echo "Source  : $TARGET_DIR"
echo "Server  : $HOST_URL  (→ $DOCKER_HOST_URL inside container)"
echo ""

docker "${DOCKER_ARGS[@]}"

echo ""
echo "Analysis complete. View results at $HOST_URL/dashboard?id=$PROJECT_KEY"
