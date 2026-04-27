# Auto-Fix SonarQube Issues

Run `bin/auto-fix.py` to automatically fix SonarQube issues using a local Ollama model.

The script:
- Runs analysis, fetches open issues sorted by severity (BLOCKER → CRITICAL → MAJOR → MINOR → INFO)
- Calls the Ollama model to fix each source file, iterating until clean or the iteration limit is reached
- Issues requiring human judgment get a `// TODO [NEEDS HUMAN REVIEW]: <reason>` comment
- Commits all changes on a new git branch
- Writes a markdown report to `log/qa-loop-<timestamp>.md`

## Steps

1. Confirm SonarQube is running:
   ```bash
   curl -s http://localhost:9000/api/system/status
   ```
   Expected: `{"status":"UP",...}`. If not, start it and wait:
   ```bash
   ./bin/start-sonar.sh
   ```

2. Confirm Ollama is running and the model is available:
   ```bash
   curl -s http://localhost:11434/api/tags | python3 -m json.tool
   ```
   If Ollama is not running: `ollama serve`
   To pull the default model: `ollama pull qwen2.5-coder`
   Change the model in `config.toml` under `[ollama]` if needed.

3. Optionally export a Sonar token override:
   ```bash
   export SONAR_TOKEN=<sonar-token>   # optional; falls back to config.toml
   ```

4. Run the auto-fixer. Use `$ARGUMENTS` if the user passed a path or flags:
   ```bash
   TARGET_DIR="${ARGUMENTS:-}"

   if [[ -n "$TARGET_DIR" ]]; then
     python3 bin/auto-fix.py "$TARGET_DIR"
   else
     python3 bin/auto-fix.py
   fi
   ```

   Common flag examples:
   ```bash
   # Limit iterations (default is 5, or ollama.max_iterations in config.toml):
   python3 bin/auto-fix.py --max-iterations 3

   # Fix a specific subdirectory:
   python3 bin/auto-fix.py java-qa-demo

   # Custom branch name:
   python3 bin/auto-fix.py --branch fix/sonar-cleanup

   # Override SonarQube project key (when it differs from the directory basename):
   python3 bin/auto-fix.py --project-key my-custom-key
   ```

5. Monitor output. The script prints progress per iteration:
   ```
   Branch: auto-fix/2026-04-27-10-30
   Target: /path/to/project  (key: java-qa-demo)
   Model:  qwen2.5-coder @ http://localhost:11434  |  max iterations: 5

   Running initial analysis...

   === Iteration 1/5 ===
   Open issues: 23
     Fixing src/main/java/com/example/demo/util/PasswordUtils.java (5 issues)...
     Fixing src/main/java/com/example/demo/infrastructure/DatabaseConnector.java (3 issues)...
     Re-scanning...
     Fixed: 8  |  Remaining: 15
   ...
   ```

6. After the run, display the report:
   ```bash
   REPORT=$(ls log/qa-loop-*.md | sort | tail -1)
   cat "$REPORT"
   ```

7. Review any human-review TODOs in the changed files:
   ```bash
   git diff HEAD~1 | grep "NEEDS HUMAN REVIEW"
   ```

8. Share the dashboard URL printed at the end to explore full details in SonarQube.
