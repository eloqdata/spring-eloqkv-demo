package com.benchmark.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class EloqKVService {

    private final StringRedisTemplate redis;

    public EloqKVService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // String ops
    public void set(String key, String value) {
        redis.opsForValue().set(key, value);
    }

    public void setEx(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public Boolean del(String key) {
        return redis.delete(key);
    }

    public Long incr(String key) {
        return redis.opsForValue().increment(key);
    }

    // Hash ops
    public void hset(String key, String field, String value) {
        redis.opsForHash().put(key, field, value);
    }

    public void hmset(String key, Map<String, String> fields) {
        redis.opsForHash().putAll(key, fields);
    }

    public Object hget(String key, String field) {
        return redis.opsForHash().get(key, field);
    }

    public Map<Object, Object> hgetAll(String key) {
        return redis.opsForHash().entries(key);
    }

    // List ops
    public void lpush(String key, String... values) {
        redis.opsForList().leftPushAll(key, values);
    }

    public String lpop(String key) {
        return redis.opsForList().leftPop(key);
    }

    public List<String> lrange(String key, long start, long end) {
        return redis.opsForList().range(key, start, end);
    }

    // Set ops
    public void sadd(String key, String... members) {
        redis.opsForSet().add(key, members);
    }

    public Set<String> smembers(String key) {
        return redis.opsForSet().members(key);
    }

    public Boolean sismember(String key, String member) {
        return redis.opsForSet().isMember(key, member);
    }

    // Sorted Set ops
    public void zadd(String key, double score, String member) {
        redis.opsForZSet().add(key, member, score);
    }

    public Set<String> zrange(String key, long start, long end) {
        return redis.opsForZSet().range(key, start, end);
    }

    public Long zrank(String key, String member) {
        return redis.opsForZSet().rank(key, member);
    }

    // Expiry
    public Boolean expire(String key, long seconds) {
        return redis.expire(key, seconds, TimeUnit.SECONDS);
    }

    public Boolean exists(String key) {
        return redis.hasKey(key);
    }

    // Ping
    public String ping() {
        return redis.getConnectionFactory()
            .getConnection()
            .ping();
    }
}
