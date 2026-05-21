package com.benchmark.controller;

import com.benchmark.service.EloqKVService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BenchmarkController {

    private final EloqKVService kv;

    public BenchmarkController(EloqKVService kv) {
        this.kv = kv;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        Map<String, String> result = new HashMap<>();
        result.put("pong", kv.ping());
        return result;
    }

    @PostMapping("/set")
    public Map<String, String> set(@RequestParam String key, @RequestParam String value) {
        kv.set(key, value);
        return Map.of("status", "OK", "key", key);
    }

    @GetMapping("/get")
    public Map<String, String> get(@RequestParam String key) {
        String value = kv.get(key);
        return Map.of("key", key, "value", value != null ? value : "");
    }

    @DeleteMapping("/del")
    public Map<String, Object> del(@RequestParam String key) {
        Boolean deleted = kv.del(key);
        return Map.of("key", key, "deleted", Boolean.TRUE.equals(deleted));
    }

    @PostMapping("/hset")
    public Map<String, String> hset(@RequestParam String key,
                                     @RequestParam String field,
                                     @RequestParam String value) {
        kv.hset(key, field, value);
        return Map.of("status", "OK");
    }

    @GetMapping("/hget")
    public Map<String, Object> hget(@RequestParam String key, @RequestParam String field) {
        Object value = kv.hget(key, field);
        return Map.of("key", key, "field", field, "value", value != null ? value : "");
    }

    @PostMapping("/zadd")
    public Map<String, String> zadd(@RequestParam String key,
                                     @RequestParam double score,
                                     @RequestParam String member) {
        kv.zadd(key, score, member);
        return Map.of("status", "OK");
    }

    // Convenience endpoint: write a random KV and return it
    @GetMapping("/random-write")
    public Map<String, String> randomWrite() {
        String key = "bench:" + UUID.randomUUID();
        String value = "val-" + System.currentTimeMillis();
        kv.set(key, value);
        return Map.of("key", key, "value", value);
    }
}
