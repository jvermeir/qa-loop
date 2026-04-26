# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

QA Loop is a scheduled quality monitor: it runs SonarQube analysis on a target project directory, compares results against configured thresholds, writes reports, and sends a Slack alert on breach. It is driven by cron and designed to run unattended.

## Commands

```bash
# Start / stop the SonarQube Docker container (data persists in sonar-data/)
./bin/start-sonar.sh
./bin/stop-sonar.sh

# First-time setup: set admin password, create project, generate token
./bin/setup-sonar.sh

# Run sonar-scanner manually against a directory
./bin/run-analysis.sh /path/to/project
SONAR_TOKEN=sqp_... ./bin/run-analysis.sh /path/to/project   # token override

# Run the full checker once (same as what cron calls)
python3 bin/checker.py

# Install checker.py as a cron job (reads schedule from config.toml, or pass expression)
./install/install-cron.sh
./install/install-cron.sh "0 8 * * *"
```

## Architecture

```
cron
 └─ bin/checker.py
     ├─ load config.toml
     ├─ subprocess → bin/run-analysis.sh <project_dir>
     │                └─ docker run sonarsource/sonar-scanner-cli
     ├─ parse scanner stdout/stderr (regex — NOT the Sonar web API)
     ├─ compare against thresholds
     ├─ write reports/YYYY-MM-DD_HH-MM.md  and  reports/latest.json
     ├─ append one line to log/qa-loop.log
     └─ POST Slack webhook (only on BREACH)
```

**Key design decisions:**
- Coverage and warning counts are extracted by regex from sonar-scanner CLI output, not by querying the SonarQube REST API. This keeps the dependency surface minimal but means the regex patterns in `checker.py` are fragile to scanner output format changes.
- `config.toml` in the repo root is the single source of truth for Sonar credentials, thresholds, and Slack webhook. `run-analysis.sh` reads the same file independently of `checker.py`.
- Slack fires only on BREACH status, never on OK, to stay quiet.
- `reports/latest.json` is always overwritten; `reports/YYYY-MM-DD_HH-MM.md` files and `log/qa-loop.log` are append-only and never truncated by this tool.
- Python 3.11+ is required (`tomllib` is stdlib). For older Python, `tomli` must be installed.

## Configuration

All config lives in `config.toml` (see `config.toml.sample`). Fields used at runtime:

| Key | Used by |
|-----|---------|
| `sonar.project_key` | both `run-analysis.sh` and `checker.py` |
| `sonar.host_url` | both |
| `sonar.token` | both (overridable via `SONAR_TOKEN` env var) |
| `sonar.project_dir` | `checker.py` only (the directory to analyse) |
| `sonar.scanner_args` | `run-analysis.sh` (forwarded verbatim to sonar-scanner) |
| `thresholds.min_coverage_pct` | `checker.py` |
| `thresholds.max_warnings` | `checker.py` |
| `slack.webhook_url` | `checker.py` |
| `schedule.cron_expression` | `install-cron.sh` only (not read at analysis time) |

## Docker networking note

`run-analysis.sh` automatically rewrites `localhost` → `host.docker.internal` in the host URL so the sonar-scanner container can reach the SonarQube container on the host machine. If the Sonar server is not on localhost, this rewrite is a no-op.
