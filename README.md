# Spring EloqKV Benchmark

A Spring Boot 3.3 + JMH 1.37 + Gatling 3.10 benchmark suite that measures
[EloqKV](https://github.com/eloqdata/eloqkv) performance through the standard
Spring Data Redis / Lettuce stack.  Because EloqKV speaks the Redis
Serialization Protocol (RESP), **zero application code is modified** — only
`spring.data.redis.host` changes.

## What is EloqKV?

EloqKV is a Redis-protocol-compatible distributed key-value store that uses
NVMe SSDs instead of DRAM for primary storage. It delivers sub-millisecond
command latency while reducing infrastructure cost by **10× or more** for
datasets that exceed available RAM.

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.8+ |
| EloqKV | running on `ELOQKV_HOST:6379` |

Install Java 21 and Maven on Ubuntu 24.04:

```bash
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk maven
```

Run EloqKV via Docker (local testing):

```bash
docker pull eloqdata/eloq-dev-ci-ubuntu2404:latest
docker run -p 6379:6379 eloqdata/eloq-dev-ci-ubuntu2404
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
benchmark:
  eloqkv:
    host: 10.128.15.205   # ← your EloqKV host
    port: 6379
```

Or pass at runtime: `--benchmark.eloqkv.host=<host>`

## Building

```bash
mvn clean package -DskipTests
```

This produces two artifacts in `target/`:

| Artifact | Purpose |
|----------|---------|
| `benchmarks.jar` | JMH fat jar (main class: `org.openjdk.jmh.Main`) |
| `spring-eloqkv-benchmark-*-exec.jar` | Spring Boot uber-jar for Gatling |

## Running benchmarks

### Option A — all-in-one script

```bash
./run-benchmarks.sh
```

Runs connectivity check → JMH → starts Spring Boot → Gatling → cleanup.
Results written to `results/`.

### Option B — JMH only (direct Lettuce, no HTTP overhead)

```bash
java -jar target/benchmarks.jar \
  -wi 3 -w 5 \
  -i 5 -r 10 \
  -f 1 \
  -rf json -rff results/jmh-results.json
```

| Flag | Meaning |
|------|---------|
| `-wi 3 -w 5` | 3 warmup iterations × 5 s each |
| `-i 5 -r 10` | 5 measurement iterations × 10 s each |
| `-f 1` | 1 JVM fork |
| `-rf json` | JSON result file |

Run a single benchmark:

```bash
java -jar target/benchmarks.jar ".*stringGet.*" -wi 3 -w 5 -i 5 -r 10 -f 1
```

### Option C — Spring Boot app + Gatling load test

Start the Spring Boot app (targets EloqKV internally):

```bash
java -jar target/spring-eloqkv-benchmark-*-exec.jar
```

Wait for `Started BenchmarkApplication`, then in a second terminal:

```bash
cd gatling
mvn gatling:test
```

Reports appear in `gatling/target/gatling/`.

### Option D — Manual Spring REST smoke test

```bash
# Ping
curl http://localhost:8080/api/ping

# SET / GET
curl -X POST "http://localhost:8080/api/set?key=hello&value=world"
curl "http://localhost:8080/api/get?key=hello"

# Hash
curl -X POST "http://localhost:8080/api/hset?key=user:1&field=name&value=Alice"
curl "http://localhost:8080/api/hget?key=user:1&field=name"

# Sorted set
curl -X POST "http://localhost:8080/api/zadd?key=scores&score=42&member=alice"
```

## Benchmark coverage

### JMH benchmarks (`EloqKVBenchmark.java`)

| Benchmark | Data structure | What it measures |
|-----------|---------------|-----------------|
| `stringSet` | String | blind SET (new key each call) |
| `stringGet` | String | GET on a pre-warmed key |
| `stringSetGet` | String | SET then GET round-trip |
| `stringIncr` | String | atomic INCR counter |
| `stringSetex` | String | SET with TTL (SETEX) |
| `hashHset` | Hash | HSET single field |
| `hashHget` | Hash | HGET single field |
| `hashHgetall` | Hash | HGETALL (10-field hash) |
| `listLpush` | List | LPUSH |
| `listLrange` | List | LRANGE 0–9 |
| `setSadd` | Set | SADD random member |
| `setSismember` | Set | SISMEMBER |
| `zsetZadd` | Sorted Set | ZADD random score |
| `zsetZrange` | Sorted Set | ZRANGE 0–9 |
| `zsetZrank` | Sorted Set | ZRANK |
| `pipelinedWrites` | String | 10 async SETs per call (Lettuce async API) |

All benchmarks run in **Throughput** and **AverageTime** modes.

### Gatling simulation (`EloqKVSimulation.scala`)

Mixed concurrent load through Spring Boot REST:

| Scenario | Target rate |
|----------|------------|
| SET + GET pairs | 30 req/s sustained |
| HSET + HGET pairs | 15 req/s sustained |
| ZADD | 10 req/s sustained |
| Random write | 20 req/s sustained |

Assertions: max response time < 2 000 ms · success rate > 99%.

## Measured results (GCP us-central1, EloqKV cache mode)

### JMH — single Lettuce connection, synchronous

| Command | Throughput (ops/ms) | Avg latency (ms/op) |
|---------|--------------------|--------------------|
| GET | 7.810 | 0.128 |
| SET | 7.870 | 0.127 |
| INCR | 7.880 | 0.127 |
| HGET | 7.638 | 0.116 |
| HSET | 8.051 | 0.133 |
| HGETALL | 7.514 | 0.126 |
| LPUSH | 7.827 | 0.128 |
| LRANGE | 7.880 | 0.127 |
| SADD | 7.538 | 0.133 |
| SISMEMBER | 7.799 | 0.128 |
| ZADD | 7.785 | 0.128 |
| ZRANGE | 8.120 | 0.123 |
| ZRANK | 7.577 | 0.132 |
| SETEX | 4.615 | 0.217 |
| Pipeline 10×SET | 4.163 batch/ms | 0.024 per write |

### Gatling — HTTP through Spring Boot REST

| Metric | Value |
|--------|-------|
| P50 | 1 ms |
| P75 | 1 ms |
| P95 | 2 ms |
| P99 | 3 ms |
| Max | 43 ms |
| Success rate | 100% |

## Project structure

```
spring-benchmark/
├── pom.xml                          # Spring Boot + JMH + Gatling deps
├── run-benchmarks.sh                # All-in-one runner
├── src/main/
│   ├── java/com/benchmark/
│   │   ├── BenchmarkApplication.java
│   │   ├── config/RedisConfig.java  # Lettuce pool (50 connections)
│   │   ├── controller/BenchmarkController.java
│   │   ├── jmh/EloqKVBenchmark.java # 16 JMH benchmarks
│   │   └── service/EloqKVService.java
│   └── resources/application.yml
└── gatling/
    ├── pom.xml
    └── src/test/scala/benchmark/EloqKVSimulation.scala
```

## License

MIT
