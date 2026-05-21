package com.benchmark.jmh;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
public class EloqKVBenchmark {

    private static final String HOST = "10.128.15.205";
    private static final int PORT = 6379;

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;

    private String testKey;
    private String hashKey;
    private String listKey;
    private String setKey;
    private String zsetKey;

    @Setup(Level.Trial)
    public void setup() {
        RedisURI uri = RedisURI.builder()
            .withHost(HOST)
            .withPort(PORT)
            .withTimeout(Duration.ofSeconds(5))
            .build();
        client = RedisClient.create(uri);
        connection = client.connect();
        commands = connection.sync();

        // Pre-populate test data
        testKey = "jmh:string:" + UUID.randomUUID();
        hashKey = "jmh:hash:" + UUID.randomUUID();
        listKey = "jmh:list:" + UUID.randomUUID();
        setKey  = "jmh:set:"  + UUID.randomUUID();
        zsetKey = "jmh:zset:" + UUID.randomUUID();

        commands.set(testKey, "benchmark-value");

        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            fields.put("field" + i, "value" + i);
        }
        commands.hmset(hashKey, fields);

        for (int i = 0; i < 10; i++) {
            commands.lpush(listKey, "item" + i);
            commands.sadd(setKey, "member" + i);
            commands.zadd(zsetKey, i, "member" + i);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        commands.del(testKey, hashKey, listKey, setKey, zsetKey);
        connection.close();
        client.shutdown();
    }

    // ---- String benchmarks ----

    @Benchmark
    public void stringSet(Blackhole bh) {
        String k = "jmh:w:" + UUID.randomUUID();
        commands.set(k, "value");
        bh.consume(k);
    }

    @Benchmark
    public void stringGet(Blackhole bh) {
        bh.consume(commands.get(testKey));
    }

    @Benchmark
    public void stringSetGet(Blackhole bh) {
        String k = "jmh:sg:" + UUID.randomUUID();
        commands.set(k, "value");
        bh.consume(commands.get(k));
    }

    @Benchmark
    public void stringIncr(Blackhole bh) {
        bh.consume(commands.incr("jmh:counter"));
    }

    @Benchmark
    public void stringSetex(Blackhole bh) {
        String k = "jmh:ex:" + UUID.randomUUID();
        bh.consume(commands.setex(k, 60, "expire-value"));
    }

    // ---- Hash benchmarks ----

    @Benchmark
    public void hashHset(Blackhole bh) {
        bh.consume(commands.hset(hashKey, "bench-field", "bench-value"));
    }

    @Benchmark
    public void hashHget(Blackhole bh) {
        bh.consume(commands.hget(hashKey, "field0"));
    }

    @Benchmark
    public void hashHgetall(Blackhole bh) {
        bh.consume(commands.hgetall(hashKey));
    }

    // ---- List benchmarks ----

    @Benchmark
    public void listLpush(Blackhole bh) {
        bh.consume(commands.lpush(listKey, "pushed-value"));
    }

    @Benchmark
    public void listLrange(Blackhole bh) {
        bh.consume(commands.lrange(listKey, 0, 9));
    }

    // ---- Set benchmarks ----

    @Benchmark
    public void setSadd(Blackhole bh) {
        bh.consume(commands.sadd(setKey, UUID.randomUUID().toString()));
    }

    @Benchmark
    public void setSismember(Blackhole bh) {
        bh.consume(commands.sismember(setKey, "member0"));
    }

    // ---- Sorted Set benchmarks ----

    @Benchmark
    public void zsetZadd(Blackhole bh) {
        bh.consume(commands.zadd(zsetKey, Math.random() * 1000, UUID.randomUUID().toString()));
    }

    @Benchmark
    public void zsetZrange(Blackhole bh) {
        bh.consume(commands.zrange(zsetKey, 0, 9));
    }

    @Benchmark
    public void zsetZrank(Blackhole bh) {
        bh.consume(commands.zrank(zsetKey, "member0"));
    }

    // ---- Pipeline benchmark ----

    @Benchmark
    public void pipelinedWrites(Blackhole bh) throws Exception {
        var async = connection.async();
        var futures = new java.util.ArrayList<>();
        String prefix = "jmh:pipe:" + UUID.randomUUID() + ":";
        for (int i = 0; i < 10; i++) {
            futures.add(async.set(prefix + i, "v" + i));
        }
        for (var f : futures) {
            bh.consume(((java.util.concurrent.CompletableFuture<?>) f).get());
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(EloqKVBenchmark.class.getSimpleName())
            .warmupIterations(3)
            .warmupTime(TimeValue.seconds(5))
            .measurementIterations(5)
            .measurementTime(TimeValue.seconds(10))
            .forks(1)
            .jvmArgsAppend("-Xms512m", "-Xmx1g")
            .build();
        new Runner(opt).run();
    }
}
