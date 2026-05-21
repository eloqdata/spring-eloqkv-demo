#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ELOQKV_HOST="10.128.15.205"
ELOQKV_PORT="6379"

log() { echo "[$(date +%H:%M:%S)] $*"; }

# ── 1. Check EloqKV connectivity ──────────────────────────────────────────────
log "Checking EloqKV connectivity at ${ELOQKV_HOST}:${ELOQKV_PORT}..."
if command -v redis-cli &>/dev/null; then
    redis-cli -h "$ELOQKV_HOST" -p "$ELOQKV_PORT" PING || { log "ERROR: Cannot reach EloqKV"; exit 1; }
else
    nc -z -w5 "$ELOQKV_HOST" "$ELOQKV_PORT" || { log "ERROR: Cannot reach EloqKV port"; exit 1; }
fi
log "EloqKV is reachable."

# ── 2. Build ───────────────────────────────────────────────────────────────────
log "Building project..."
cd "$PROJECT_DIR"
mvn clean package -q -DskipTests
log "Build complete."

# ── 3. JMH benchmarks ─────────────────────────────────────────────────────────
log "Running JMH benchmarks..."
java -jar target/benchmarks.jar \
    -wi 3 -w 5 -i 5 -r 10 -f 1 \
    -rf json -rff "$PROJECT_DIR/results/jmh-results.json" \
    -o "$PROJECT_DIR/results/jmh-output.txt" \
    2>&1 | tee "$PROJECT_DIR/results/jmh-console.log"
log "JMH benchmarks complete. Results in results/jmh-results.json"

# ── 4. Start Spring Boot app (background, for Gatling) ────────────────────────
log "Starting Spring Boot app for Gatling..."
mkdir -p "$PROJECT_DIR/results"
java -jar target/spring-eloqkv-benchmark-*.jar \
    --server.port=8080 \
    --benchmark.eloqkv.host="$ELOQKV_HOST" \
    --benchmark.eloqkv.port="$ELOQKV_PORT" \
    > "$PROJECT_DIR/results/spring-app.log" 2>&1 &
APP_PID=$!
log "Spring Boot PID: $APP_PID"

# Wait for app to be ready
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/api/ping &>/dev/null; then
        log "Spring Boot is ready."
        break
    fi
    sleep 2
    if [[ $i -eq 30 ]]; then
        log "ERROR: Spring Boot did not start in time."
        kill $APP_PID 2>/dev/null || true
        exit 1
    fi
done

# ── 5. Gatling load test ───────────────────────────────────────────────────────
log "Running Gatling load test..."
cd "$PROJECT_DIR/gatling"
mvn gatling:test -q 2>&1 | tee "$PROJECT_DIR/results/gatling-console.log" || true
log "Gatling complete. Reports in gatling/target/gatling-results/"

# ── 6. Cleanup ─────────────────────────────────────────────────────────────────
log "Stopping Spring Boot..."
kill $APP_PID 2>/dev/null || true

log "All benchmarks complete!"
log "Results:"
log "  JMH JSON:    $PROJECT_DIR/results/jmh-results.json"
log "  JMH text:    $PROJECT_DIR/results/jmh-output.txt"
log "  Gatling:     $PROJECT_DIR/gatling/target/gatling-results/"
