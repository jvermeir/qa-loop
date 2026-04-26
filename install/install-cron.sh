#!/usr/bin/env bash
# Registers bin/checker.py as a cron job.
# Usage: ./install/install-cron.sh [cron-expression]
# Default schedule is taken from config.toml; override by passing an expression.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON="$(command -v python3)"
CHECKER="$ROOT_DIR/bin/checker.py"
LOG="$ROOT_DIR/log/qa-loop.log"

# Read cron expression from config.toml if not passed as argument
if [[ $# -ge 1 ]]; then
    CRON_EXPR="$1"
else
    CRON_EXPR=$(python3 -c "
import sys
try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib
with open('$ROOT_DIR/config.toml', 'rb') as f:
    c = tomllib.load(f)
print(c.get('schedule', {}).get('cron_expression', '0 * * * *'))
")
fi

CRON_LINE="$CRON_EXPR cd '$ROOT_DIR' && $PYTHON '$CHECKER' >> '$LOG' 2>&1"

# Remove any existing entry for this checker, then add the new one
(crontab -l 2>/dev/null | grep -v "$CHECKER"; echo "$CRON_LINE") | crontab -

echo "Cron job installed:"
echo "  $CRON_LINE"
