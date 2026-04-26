#!/usr/bin/env bash
# First-time SonarQube setup:
#   1. Changes the default admin password
#   2. Creates a project
#   3. Generates a user token
#   4. Prints the token (copy it into config.toml)
#
# Usage: ./bin/setup-sonar.sh
# Requires: curl, running SonarQube at http://localhost:9000

set -euo pipefail

HOST="http://localhost:9000"
DEFAULT_USER="admin"
DEFAULT_PASS="admin"

# ---------------------------------------------------------------------------
# Check server is up
# ---------------------------------------------------------------------------
if ! curl -sf "$HOST/api/system/status" | grep -q '"status":"UP"'; then
    echo "ERROR: SonarQube is not running at $HOST"
    echo "Run: ./bin/start-sonar.sh"
    exit 1
fi

# ---------------------------------------------------------------------------
# Prompt for new admin password and project key
# ---------------------------------------------------------------------------
echo ""
echo "=== SonarQube first-time setup ==="
echo ""

read -rsp "New admin password (min 12 chars): " NEW_PASS
echo ""
read -rsp "Confirm password: " NEW_PASS2
echo ""

if [[ "$NEW_PASS" != "$NEW_PASS2" ]]; then
    echo "ERROR: Passwords do not match."
    exit 1
fi

if [[ ${#NEW_PASS} -lt 12 ]]; then
    echo "ERROR: Password must be at least 12 characters."
    exit 1
fi

read -rp "Project key (e.g. my-org_my-project): " PROJECT_KEY
read -rp "Project display name: " PROJECT_NAME
read -rp "Token name (e.g. qa-loop-token): " TOKEN_NAME

# ---------------------------------------------------------------------------
# Step 1 — Change admin password
# ---------------------------------------------------------------------------
echo ""
echo "[1/3] Changing admin password …"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "$DEFAULT_USER:$DEFAULT_PASS" \
    -X POST "$HOST/api/users/change_password" \
    --data-urlencode "login=$DEFAULT_USER" \
    --data-urlencode "password=$NEW_PASS" \
    --data-urlencode "previousPassword=$DEFAULT_PASS")

if [[ "$HTTP_STATUS" != "204" ]]; then
    echo "ERROR: Failed to change password (HTTP $HTTP_STATUS)."
    echo "If you already changed it, re-run with the current password set as DEFAULT_PASS in this script."
    exit 1
fi
echo "     Password changed."

# Use new password for subsequent requests
AUTH="-u $DEFAULT_USER:$NEW_PASS"

# ---------------------------------------------------------------------------
# Step 2 — Create project
# ---------------------------------------------------------------------------
echo "[2/3] Creating project '$PROJECT_KEY' …"
HTTP_STATUS=$(curl -s -o /tmp/sq_create_resp.json -w "%{http_code}" \
    $AUTH \
    -X POST "$HOST/api/projects/create" \
    --data-urlencode "project=$PROJECT_KEY" \
    --data-urlencode "name=$PROJECT_NAME")

if [[ "$HTTP_STATUS" != "200" ]]; then
    EXISTING=$(grep -o '"key":"[^"]*"' /tmp/sq_create_resp.json 2>/dev/null || true)
    if echo "$EXISTING" | grep -q "$PROJECT_KEY"; then
        echo "     Project already exists — continuing."
    else
        echo "ERROR: Failed to create project (HTTP $HTTP_STATUS)."
        cat /tmp/sq_create_resp.json
        exit 1
    fi
else
    echo "     Project created."
fi

# ---------------------------------------------------------------------------
# Step 3 — Generate user token
# ---------------------------------------------------------------------------
echo "[3/3] Generating token '$TOKEN_NAME' …"
TOKEN_RESP=$(curl -s \
    $AUTH \
    -X POST "$HOST/api/user_tokens/generate" \
    --data-urlencode "name=$TOKEN_NAME")

TOKEN=$(echo "$TOKEN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$TOKEN" ]]; then
    echo "ERROR: Failed to generate token."
    echo "$TOKEN_RESP"
    exit 1
fi

# ---------------------------------------------------------------------------
# Done — print summary
# ---------------------------------------------------------------------------
echo ""
echo "=== Setup complete ==="
echo ""
echo "SonarQube URL : $HOST"
echo "Project key   : $PROJECT_KEY"
echo "Token         : $TOKEN"
echo ""
echo "Add the following to config.toml:"
echo ""
echo "  [sonar]"
echo "  project_key  = \"$PROJECT_KEY\""
echo "  scanner_args = ["
echo "    \"-Dsonar.host.url=$HOST\","
echo "    \"-Dsonar.token=$TOKEN\","
echo "    \"-Dsonar.sources=.\","
echo "  ]"
echo ""
echo "IMPORTANT: Copy the token now — it will not be shown again."
