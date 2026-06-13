package com.civic.action.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionModeService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String ELECTION_MODE_KEY = "CONFIG_ELECTION_MODE";
    private boolean fallbackElectionMode = false; // Fallback in-memory state

    public boolean isElectionModeActive() {
        try {
            String val = redisTemplate.opsForValue().get(ELECTION_MODE_KEY);
            return val != null && Boolean.parseBoolean(val);
        } catch (Exception e) {
            log.warn("Redis lookup failed for Election Mode config, using fallback state: {}", fallbackElectionMode);
            return fallbackElectionMode;
        }
    }

    public void setElectionMode(boolean active) {
        try {
            redisTemplate.opsForValue().set(ELECTION_MODE_KEY, String.valueOf(active));
            log.info("Set Election Mode state to: {} in Redis", active);
        } catch (Exception e) {
            log.warn("Failed to set Election Mode state in Redis, updating fallback. Error: {}", e.getMessage());
        }
        fallbackElectionMode = active;
    }
}
