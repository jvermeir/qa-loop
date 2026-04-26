# Run SonarQube Analysis

Run a SonarQube analysis on a target project and report the results.

## Steps

1. Confirm the SonarQube server is running by checking `http://localhost:9000/api/system/status`. If it is not up, run `./bin/start-sonar.sh` and wait until the server reports `UP`.

2. Determine the target project directory:
   - Use `$ARGUMENTS` if set (this is the path the user passed, e.g. `/run-analysis /path/to/project`).
   - Otherwise default to the current working directory.
   - Resolve to an absolute path and store as `TARGET_DIR`.
   - Derive the Sonar project key from the directory's basename: `PROJECT_KEY=$(basename "$TARGET_DIR")`.

3. Run the analysis, passing the resolved path explicitly:
   ```bash
   TARGET_DIR="${ARGUMENTS:-$(pwd)}"
   TARGET_DIR="$(cd "$TARGET_DIR" && pwd)"
   PROJECT_KEY="$(basename "$TARGET_DIR")"
   ./bin/run-analysis.sh "$TARGET_DIR"
   ```

4. After the scanner exits, fetch the current metrics from the SonarQube API and summarise the results:
   ```bash
   # Read host and token from qa-loop config; use the derived PROJECT_KEY for the target project
   HOST=$(python3 -c "import tomllib; c=tomllib.load(open('config.toml','rb')); print(c['sonar']['host_url'])")
   TOKEN=$(python3 -c "import tomllib; c=tomllib.load(open('config.toml','rb')); print(c['sonar']['token'])")
   curl -s -u "$TOKEN:" \
     "$HOST/api/measures/component?component=$PROJECT_KEY&metricKeys=coverage,bugs,vulnerabilities,code_smells,duplicated_lines_density"
   ```

5. Compare the fetched metrics against the thresholds in `config.toml`:
   - `thresholds.min_coverage_pct` — alert if coverage is below this value
   - `thresholds.max_warnings`     — alert if bugs + vulnerabilities + code_smells exceeds this value

6. Report back in a concise table:

   | Metric              | Value | Threshold | Status |
   |---------------------|-------|-----------|--------|
   | Coverage (%)        | …     | ≥ X %     | ✓ / ✗  |
   | Bugs                | …     | —         |        |
   | Vulnerabilities     | …     | —         |        |
   | Code smells         | …     | —         |        |
   | Total warnings      | …     | ≤ Y       | ✓ / ✗  |
   | Duplications (%)    | …     | —         |        |

   State clearly whether all thresholds pass or which ones are breached.

7. Provide the dashboard URL so the user can explore details:
   `<host_url>/dashboard?id=<project_key>`
