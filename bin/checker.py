#!/usr/bin/env python3
"""Periodic QA checker: runs sonar/run-analysis.sh, evaluates thresholds, logs and alerts."""

import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:
    try:
        import tomli as tomllib  # type: ignore[no-redef]
    except ModuleNotFoundError:
        print("ERROR: Python < 3.11 requires 'tomli': pip install tomli", file=sys.stderr)
        sys.exit(1)

try:
    import requests
    _has_requests = True
except ModuleNotFoundError:
    _has_requests = False

BASE_DIR = Path(__file__).parent.parent   # bin/ -> project root
CONFIG_PATH = BASE_DIR / "config.toml"
LOG_PATH = BASE_DIR / "log" / "qa-loop.log"
REPORTS_DIR = BASE_DIR / "reports"
LATEST_JSON = REPORTS_DIR / "latest.json"


# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

def load_config() -> dict:
    with open(CONFIG_PATH, "rb") as f:
        return tomllib.load(f)


# ---------------------------------------------------------------------------
# Analysis
# ---------------------------------------------------------------------------

def run_analysis(project_dir: Path) -> tuple[int, str]:
    script = BASE_DIR / "bin" / "run-analysis.sh"
    cmd = [str(script), str(project_dir)]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    combined = result.stdout + "\n" + result.stderr
    return result.returncode, combined


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------

def parse_coverage(output: str) -> float | None:
    # sonar-scanner prints e.g. "INFO: Sensor JaCoCo XML Report Importer [jacoco]"
    # and later "INFO: Coverage: 85.2%" or similar patterns.
    patterns = [
        r"Coverage[:\s]+([0-9]+(?:\.[0-9]+)?)\s*%",
        r"overall_line_coverage[\"']?\s*[:=]\s*[\"']?([0-9]+(?:\.[0-9]+)?)",
        r"line coverage:\s*([0-9]+(?:\.[0-9]+)?)\s*%",
    ]
    for pat in patterns:
        m = re.search(pat, output, re.IGNORECASE)
        if m:
            return float(m.group(1))
    return None


def count_warn_error_lines(output: str) -> int:
    return sum(
        1 for line in output.splitlines()
        if re.search(r"^\s*(WARN|WARNING|ERROR)\b", line, re.IGNORECASE)
    )


# ---------------------------------------------------------------------------
# Reports
# ---------------------------------------------------------------------------

def write_markdown_report(
    ts: datetime,
    coverage: float | None,
    warnings: int,
    breach: bool,
    config: dict,
    scanner_output: str,
) -> Path:
    REPORTS_DIR.mkdir(exist_ok=True)
    stamp = ts.strftime("%Y-%m-%d_%H-%M")
    path = REPORTS_DIR / f"{stamp}.md"

    min_cov = config["thresholds"]["min_coverage_pct"]
    max_warn = config["thresholds"]["max_warnings"]

    cov_val = f"{coverage:.1f}%" if coverage is not None else "n/a"
    cov_status = (
        "n/a" if coverage is None
        else ("OK" if coverage >= min_cov else "BELOW threshold")
    )
    warn_status = "OK" if warnings <= max_warn else "ABOVE threshold"

    tail_lines = scanner_output.strip().splitlines()[-50:]
    tail = "\n".join(tail_lines)

    content = f"""# QA Report — {ts.strftime("%Y-%m-%d %H:%M")} UTC

**Overall status: {"BREACH" if breach else "OK"}**

| Metric | Value | Threshold | Status |
|---|---|---|---|
| Coverage | {cov_val} | ≥ {min_cov}% | {cov_status} |
| Sonar warnings | {warnings} | ≤ {max_warn} | {warn_status} |

## sonar-scanner output (last 50 lines)

```
{tail}
```
"""
    path.write_text(content)
    return path


def write_latest_json(
    ts: datetime,
    coverage: float | None,
    warnings: int,
    breach: bool,
    config: dict,
) -> None:
    REPORTS_DIR.mkdir(exist_ok=True)
    data = {
        "timestamp": ts.isoformat(),
        "coverage_pct": coverage,
        "warnings": warnings,
        "breach": breach,
        "thresholds": {
            "min_coverage_pct": config["thresholds"]["min_coverage_pct"],
            "max_warnings": config["thresholds"]["max_warnings"],
        },
    }
    LATEST_JSON.write_text(json.dumps(data, indent=2))


# ---------------------------------------------------------------------------
# Log
# ---------------------------------------------------------------------------

def log_run(
    ts: datetime,
    status: str,
    coverage: float | None,
    warnings: int | None,
    report_path: Path | None,
    slack_sent: bool,
    error_msg: str = "",
) -> None:
    LOG_PATH.parent.mkdir(exist_ok=True)
    stamp = ts.strftime("%Y-%m-%dT%H:%M:%SZ")

    if status == "ERROR":
        line = f"{stamp}  ERROR   {error_msg}"
    else:
        cov_str = f"{coverage:.1f}%" if coverage is not None else "n/a"
        warn_str = str(warnings) if warnings is not None else "n/a"
        rel_report = report_path.relative_to(BASE_DIR) if report_path else "n/a"
        slack_str = "  slack=sent" if slack_sent else ""
        line = (
            f"{stamp}  {status:<7} "
            f"coverage={cov_str:<8} "
            f"warnings={warn_str:<5} "
            f"report={rel_report}"
            f"{slack_str}"
        )

    with open(LOG_PATH, "a") as f:
        f.write(line + "\n")

    print(line)


# ---------------------------------------------------------------------------
# Slack
# ---------------------------------------------------------------------------

def post_slack(webhook_url: str, ts: datetime, coverage: float | None, warnings: int) -> bool:
    if not webhook_url or not _has_requests:
        return False

    cov_str = f"{coverage:.1f}%" if coverage is not None else "n/a"
    text = (
        f":warning: *QA check BREACH* — {ts.strftime('%Y-%m-%d %H:%M')} UTC\n"
        f"• Coverage: {cov_str}\n"
        f"• Sonar warnings: {warnings}"
    )
    try:
        resp = requests.post(webhook_url, json={"text": text}, timeout=10)
        return resp.status_code == 200
    except Exception as exc:
        print(f"Slack POST failed: {exc}", file=sys.stderr)
        return False


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    config = load_config()
    ts = datetime.now(timezone.utc).replace(microsecond=0)

    raw_dir = config["sonar"].get("project_dir", ".")
    project_dir = (BASE_DIR / raw_dir).resolve()

    print(f"[{ts.isoformat()}] Running analysis on {project_dir} …")

    try:
        exit_code, output = run_analysis(project_dir)
    except FileNotFoundError:
        msg = "sonar/run-analysis.sh not found"
        print(f"ERROR: {msg}", file=sys.stderr)
        log_run(ts, "ERROR", None, None, None, False, error_msg=msg)
        sys.exit(1)
    except subprocess.TimeoutExpired:
        msg = "run-analysis.sh timed out after 600s"
        print(f"ERROR: {msg}", file=sys.stderr)
        log_run(ts, "ERROR", None, None, None, False, error_msg=msg)
        sys.exit(1)

    if exit_code != 0:
        msg = f"run-analysis.sh exited with code {exit_code}"
        print(f"ERROR: {msg}", file=sys.stderr)
        log_run(ts, "ERROR", None, None, None, False, error_msg=msg)
        sys.exit(1)

    coverage = parse_coverage(output)
    warnings = count_warn_error_lines(output)

    min_cov = config["thresholds"]["min_coverage_pct"]
    max_warn = config["thresholds"]["max_warnings"]

    cov_breach = coverage is not None and coverage < min_cov
    warn_breach = warnings > max_warn
    breach = cov_breach or warn_breach

    report_path = write_markdown_report(ts, coverage, warnings, breach, config, output)
    write_latest_json(ts, coverage, warnings, breach, config)

    slack_sent = False
    if breach:
        webhook = config["slack"].get("webhook_url", "")
        slack_sent = post_slack(webhook, ts, coverage, warnings)

    status = "BREACH" if breach else "OK"
    log_run(ts, status, coverage, warnings, report_path, slack_sent)


if __name__ == "__main__":
    main()
