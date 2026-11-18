package dev.guardflow.server.distributed;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis + Lua 脚本。生产路径一律用 redis.call('TIME') 取服务端时间；
 * 测试可通过 CLOCK_KEY 注入逻辑时间，以便复用 T4 的测试向量。
 */
public final class LuaScripts {

    public static final String CLOCK_KEY = "guardflow:test:clock";

    public static final RedisScript<String> TOKEN_BUCKET_ACQUIRE = script("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local override = redis.call('GET', KEYS[2])
            local now
            if override then
              now = tonumber(override)
            else
              local time = redis.call('TIME')
              now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            end
            local state = redis.call('HMGET', key, 'tokens', 'last')
            local tokens = tonumber(state[1])
            local last = tonumber(state[2])
            if tokens == nil then
              tokens = capacity
              last = now
            end
            local elapsed = now - last
            if elapsed > 0 then
              tokens = math.min(capacity, tokens + elapsed * rate / 1000.0)
              last = now
            end
            local allowed = 0
            for i = 1, permits do
              if tokens >= 1.0 then
                tokens = tokens - 1.0
                allowed = allowed + 1
              else
                break
              end
            end
            redis.call('HSET', key, 'tokens', tokens, 'last', last)
            redis.call('PEXPIRE', key, 3600000)
            return tostring(allowed) .. '|' .. tostring(tokens)
            """);

    public static final RedisScript<String> TOKEN_BUCKET_SNAPSHOT = script("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local override = redis.call('GET', KEYS[2])
            local now
            if override then
              now = tonumber(override)
            else
              local time = redis.call('TIME')
              now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            end
            local state = redis.call('HMGET', key, 'tokens', 'last')
            local tokens = tonumber(state[1])
            local last = tonumber(state[2])
            if tokens == nil then
              return tostring(capacity)
            end
            local elapsed = now - last
            if elapsed > 0 then
              tokens = math.min(capacity, tokens + elapsed * rate / 1000.0)
            end
            return tostring(tokens)
            """);

    public static final RedisScript<String> LEAKY_BUCKET_ACQUIRE = script("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local override = redis.call('GET', KEYS[2])
            local now
            if override then
              now = tonumber(override)
            else
              local time = redis.call('TIME')
              now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            end
            local interval = 1000.0 / rate
            local state = redis.call('HMGET', key, 'queued', 'last')
            local queued = tonumber(state[1])
            local last = tonumber(state[2])
            if queued == nil then
              queued = 0
              last = 0
            end
            if now > last then
              local current = math.floor(now / interval)
              local previous = math.floor(last / interval)
              queued = math.max(0, queued - (current - previous))
              last = now
            end
            local allowed = 0
            for i = 1, permits do
              if queued < capacity then
                queued = queued + 1
                allowed = allowed + 1
              else
                break
              end
            end
            redis.call('HSET', key, 'queued', queued, 'last', last)
            redis.call('PEXPIRE', key, 3600000)
            return tostring(allowed) .. '|' .. tostring(queued)
            """);

    public static final RedisScript<String> LEAKY_BUCKET_SNAPSHOT = script("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local override = redis.call('GET', KEYS[2])
            local now
            if override then
              now = tonumber(override)
            else
              local time = redis.call('TIME')
              now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            end
            local interval = 1000.0 / rate
            local state = redis.call('HMGET', key, 'queued', 'last')
            local queued = tonumber(state[1])
            local last = tonumber(state[2])
            if queued == nil then
              return '0'
            end
            if now > last then
              local current = math.floor(now / interval)
              local previous = math.floor(last / interval)
              queued = math.max(0, queued - (current - previous))
            end
            return tostring(queued)
            """);

    private static RedisScript<String> script(String text) {
        return new DefaultRedisScript<>(text, String.class);
    }

    private LuaScripts() {
    }
}
