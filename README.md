# QA Loop

Periodic checker that runs SonarQube analysis and alerts when test coverage or warning counts breach
configured thresholds.

---

## First-time setup

### 1. Start the SonarQube server

```bash
./bin/start-sonar.sh
```

This creates the bind-mount folders under `sonar-data/`, starts the `sonarqube:community` Docker
container, and waits until the server is ready (~60 s on first launch). Open `http://localhost:9000`
to confirm.

### 2. Configure admin credentials, project, and token

```bash
./bin/setup-sonar.sh
```

The script will prompt for:

- A new admin password (min 12 chars — replaces the default `admin`/`admin`)
- A project key (e.g. `my-org_my-project`)
- A project display name
- A token name (e.g. `qa-loop-token`)

At the end it prints the generated token and the exact lines to paste into `config.toml`.

### 3. Edit config.toml

```toml
[sonar]
project_key = "my-org_my-project"
host_url = "http://localhost:9000"
token = "sqp_..."          # token printed by setup-sonar.sh

[thresholds]
min_coverage_pct = 80.0          # alert if coverage drops below this
max_warnings = 0             # alert if Sonar issue count exceeds this

[slack]
webhook_url = ""                 # optional — leave empty to disable
```

---

## Day-to-day: starting and stopping the server

The SonarQube container preserves all data in `sonar-data/` between restarts.

```bash
# Start (or resume) the server
./bin/start-sonar.sh

# Stop the server
./bin/stop-sonar.sh
```

---

## Running an analysis

```bash
# Analyse a specific project directory
./bin/run-analysis.sh /path/to/your/project

# Analyse the current directory
./bin/run-analysis.sh

# Override the token without editing config.toml
SONAR_TOKEN=sqp_... ./bin/run-analysis.sh /path/to/your/project
```

The script runs `sonarsource/sonar-scanner-cli` in Docker, mounts the target directory, and reads
`project_key`, `host_url`, `token`, and `scanner_args` from `config.toml`. Results appear at:

```
http://localhost:9000/dashboard?id=<project_key>
```

---

## Periodic checker

`checker.py` is designed to run on a cron schedule. It invokes sonar-scanner, parses the output,
writes a report, appends a line to `qa-loop.log`, and posts a Slack alert if thresholds are
breached.

### Run once manually

```bash
python3 bin/checker.py
```

### Install as a cron job

```bash
./install/install-cron.sh              # uses schedule.cron_expression from config.toml
./install/install-cron.sh "0 8 * * *"  # or pass an expression directly (daily at 08:00)
```

---

## Claude Code slash commands

The `commands/` directory contains Claude Code slash commands for this project (e.g. `/run-analysis`).
Symlink them into `~/.claude/commands/` so Claude Code picks them up:

```bash
./install/install-commands.sh
```

This creates `~/.claude/commands/<name>.md → <repo>/commands/<name>.md` for every `.md` file in
`commands/`. Re-run after adding new command files.

### Log format

Every run appends one line to `log/qa-loop.log`:

```
2026-04-26T14:00:01Z  OK      coverage=85.2%   warnings=0     report=reports/2026-04-26_14-00.md
2026-04-26T15:00:03Z  BREACH  coverage=78.3%   warnings=3     report=reports/2026-04-26_15-00.md  slack=sent
2026-04-26T16:00:02Z  ERROR   sonar-scanner exited with code 1
```

Per-run Markdown reports are saved to `reports/YYYY-MM-DD_HH-MM.md`. The latest result is always
available as `reports/latest.json`.

---

## File layout

```
qa-loop/
├─ bin/
│   ├─ checker.py          # periodic checker entry point
│   ├─ start-sonar.sh      # start Docker container
│   ├─ stop-sonar.sh       # stop Docker container
│   ├─ setup-sonar.sh      # first-time admin + project + token setup
│   └─ run-analysis.sh     # run sonar-scanner in Docker
├─ commands/
│   └─ run-analysis.md     # /run-analysis slash command for Claude Code
├─ install/
│   ├─ install-cron.sh     # registers bin/checker.py as a cron job
│   └─ install-commands.sh # symlinks commands/ into ~/.claude/commands/
├─ log/
│   └─ qa-loop.log         # append-only run history
├─ reports/
│   ├─ latest.json         # machine-readable result of last run
│   └─ YYYY-MM-DD_HH-MM.md
├─ sonar-data/             # bind-mounted into the container (gitignored)
│   ├─ data/               # SonarQube database
│   ├─ logs/               # server logs
│   ├─ extensions/         # plugins
│   └─ conf/               # sonar.properties overrides
└─ config.toml             # all configuration
```

## Dependencies

| Tool         | Purpose                                 |
|--------------|-----------------------------------------|
| Docker       | Runs SonarQube server and sonar-scanner |
| Python 3.11+ | `checker.py` (uses stdlib `tomllib`)    |
| `requests`   | Slack webhook — `pip install requests`  |
