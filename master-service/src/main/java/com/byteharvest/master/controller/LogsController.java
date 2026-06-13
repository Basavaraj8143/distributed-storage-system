package com.byteharvest.master.controller;

import com.byteharvest.master.service.EventLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class LogsController {

    private final EventLogService eventLogService;

    public LogsController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "ALL") String level,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return eventLogService.getRecent(level, limit);
    }
}

