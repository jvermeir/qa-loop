#!/usr/bin/env bash
# Creates symlinks in ~/.claude/commands/ for all custom slash commands in commands/.
# Usage: ./install/install-commands.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMMANDS_SRC="$ROOT_DIR/commands"
COMMANDS_DST="$HOME/.claude/commands"

mkdir -p "$COMMANDS_DST"

for src in "$COMMANDS_SRC"/*.md; do
    name="$(basename "$src")"
    dst="$COMMANDS_DST/$name"
    ln -sf "$src" "$dst"
    echo "Linked: $dst -> $src"
done
