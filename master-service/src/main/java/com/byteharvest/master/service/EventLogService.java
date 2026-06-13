package com.byteharvest.master.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class EventLogService {

    private static final int MAX_EVENTS = 1000;
    private final Deque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();

    public void info(String component, String message) {
        add("INFO", component, message);
    }

    public void warn(String component, String message) {
        add("WARN", component, message);
    }

    public void error(String component, String message) {
        add("ERROR", component, message);
    }

    public List<Map<String, Object>> getRecent(String level, int limit) {
        String normalizedLevel = level == null ? "" : level.trim().toUpperCase();
        int safeLimit = Math.max(1, Math.min(limit, 500));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> event : events) {
            if (!normalizedLevel.isEmpty() && !"ALL".equals(normalizedLevel)) {
                String eventLevel = String.valueOf(event.get("level")).toUpperCase();
                if (!eventLevel.equals(normalizedLevel)) {
                    continue;
                }
            }
            result.add(event);
            if (result.size() >= safeLimit) {
                break;
            }
        }
        return result;
    }

    private void add(String level, String component, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("level", level);
        event.put("component", component);
        event.put("message", message);
        events.addFirst(event);

        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }
}

