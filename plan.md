# QA Loop — Periodic Test Coverage & Sonar Warning Checker

## Goal

A Python script that runs on a cron schedule, invokes `sonar-scanner`, parses its output for test
coverage and warnings, writes a local Markdown/JSON report, prints a summary to stdout, and posts a
Slack notification when thresholds are breached.

---

## Decisions

| Concern           | Choice                                                           |
|-------------------|------------------------------------------------------------------|
| Sonar data source | `sonar-scanner` CLI — parse stdout/stderr                        |
| Coverage source   | Pulled from Sonar output (no separate tool)                      |
| Language          | Python 3                                                         |
| Output            | Local report file + append-only run log + stdout + Slack webhook |
| Scheduling        | System cron (`crontab`)                                          |

---

## Architecture

```
cron
 └─ bin/checker.py
      ├─ run sonar-scanner (subprocess)
      ├─ parse output
      │    ├─ coverage %
      │    └─ warnings / issues count
      ├─ compare against thresholds (config.toml)
      ├─ write report  →  reports/YYYY-MM-DD_HH-MM.md  +  latest.json
      ├─ append one-line entry  →  qa-loop.log
      ├─ print summary to stdout
      └─ if threshold breached → POST to Slack webhook
```

---

## File Layout

```
qa-loop/
├─ bin/
│   ├─ checker.py          # main entry point
│   ├─ start-sonar.sh
│   ├─ stop-sonar.sh
│   ├─ setup-sonar.sh
│   └─ run-analysis.sh
├─ install/
│   └─ install-cron.sh     # registers bin/checker.py as a cron job
├─ log/
│   └─ qa-loop.log         # append-only run history (plain text)
├─ reports/
│   ├─ latest.json         # always overwritten, machine-readable
│   └─ YYYY-MM-DD_HH-MM.md
├─ sonar-data/             # bind-mounted into container (gitignored)
│   ├─ data/
│   ├─ logs/
│   ├─ extensions/
│   └─ conf/
└─ config.toml             # all configuration
```

---

## config.toml

```toml
[sonar]
project_key = "my-org_my-project"
# sonar-scanner picks up sonar-project.properties from the repo root;
# this key is used to label reports only.
scanner_args = []         # extra args forwarded to sonar-scanner

[thresholds]
min_coverage_pct = 80.0   # alert if coverage falls below this
max_warnings = 0      # alert if Sonar issues > this

[slack]
webhook_url = ""          # set to "" to disable Slack

[schedule]
# For reference — actual scheduling is done via crontab
cron_expression = "0 * * * *"   # every hour
```

---

## Implementation Plan

### Step 1 — Project scaffold

- Create `qa-loop/` directory structure
- Write `config.toml` with defaults
- Add `.gitignore` (`reports/`, `*.pyc`, `__pycache__/`)

### Step 2 — `checker.py` core

1. **Load config** — parse `config.toml` with `tomllib` (stdlib ≥ 3.11) or `tomli`
2. **Run sonar-scanner** —
   `subprocess.run(["sonar-scanner", ...], capture_output=True, text=True, timeout=600)`
3. **Parse output**
    - Coverage: look for line matching `INFO: Coverage: XX.X%` or `overall_line_coverage` in the
      JSON task output written to `.scannerwork/report-task.txt`
    - Warnings/issues: count lines matching `WARN` / `ERROR` in stdout; optionally query the
      SonarQube Web API (`/api/measures/component`) if a `SONAR_TOKEN` env var is present for richer
      data
4. **Evaluate thresholds** — produce a `breach: bool` flag
5. **Write report** — `reports/YYYY-MM-DD_HH-MM.md` (human) + `reports/latest.json` (machine)
6. **Append to log** — one line appended to `qa-loop.log` on every run (see format below)
7. **Print summary** — always write to stdout regardless of breach
8. **Slack alert** — POST JSON payload to webhook URL only when `breach=True`

### Step 3 — Report format

**Markdown (`YYYY-MM-DD_HH-MM.md`)**

```
# QA Report — 2026-04-26 14:00

| Metric | Value | Threshold | Status |
|---|---|---|---|
| Coverage | 78.3 % | ≥ 80 % | ❌ BELOW |
| Sonar warnings | 3 | ≤ 0 | ❌ ABOVE |

## Raw sonar-scanner output (last 50 lines)
...
```

**JSON (`latest.json`)**

```json
{
  "timestamp": "2026-04-26T14:00:00Z",
  "coverage_pct": 78.3,
  "warnings": 3,
  "breach": true,
  "thresholds": {
    "min_coverage_pct": 80.0,
    "max_warnings": 0
  }
}
```

### Step 4 — Run log (`qa-loop.log`)

A plain-text file that grows indefinitely (one line per run). Never truncated by the tool — rotate
externally with `logrotate` if needed.

**Format — one line per run:**

```
2026-04-26T14:00:01Z  OK      coverage=85.2%  warnings=0   report=reports/2026-04-26_14-00.md
2026-04-26T15:00:03Z  BREACH  coverage=78.3%  warnings=3   report=reports/2026-04-26_15-00.md  slack=sent
2026-04-26T16:00:02Z  ERROR   sonar-scanner exited with code 1
```

Fields:

- **timestamp** — ISO-8601 UTC
- **status** — `OK`, `BREACH`, or `ERROR`
- **coverage** — parsed value or `n/a` if sonar-scanner failed
- **warnings** — parsed count or `n/a`
- **report** — relative path to the Markdown report written this run
- **slack** — `sent` when a Slack notification was posted (only present on `BREACH`)
- **error message** — appended on `ERROR` status instead of metric fields

Written by a dedicated `log_run()` function so it is always the last step and is never skipped even
if Slack POST fails.

### Step 5 — Slack notification

Payload:

```json
{
  "text": ":warning: QA check failed on 2026-04-26 14:00\n• Coverage: 78.3% (threshold ≥80%)\n• Warnings: 3 (threshold ≤0)"
}
```

Only sent when `breach=True`. Green "all-clear" messages are printed to stdout but not sent to
Slack (avoids noise).

### Step 6 — Cron setup

Add to crontab:

```
0 * * * * cd /path/to/qa-loop && /usr/bin/python3 bin/checker.py >> log/qa-loop.log 2>&1
```

`install/install-cron.sh` reads `config.toml` and registers the job automatically.

---

## Dependencies

| Library    | Purpose                     | Install                |
|------------|-----------------------------|------------------------|
| `tomllib`  | Parse config (stdlib 3.11+) | —                      |
| `tomli`    | Fallback for Python < 3.11  | `pip install tomli`    |
| `requests` | Slack webhook POST          | `pip install requests` |

No other third-party dependencies. `sonar-scanner` must be on `PATH`.

---

## Open Questions / Future Work

- If `SONAR_TOKEN` + `SONAR_HOST_URL` are set, optionally call the Web API for richer per-rule issue
  breakdowns instead of parsing CLI text.
- Add a `--dry-run` flag that skips the Slack POST and report write.
- Retention policy: auto-delete timestamped reports older than N days.
