package com.byteharvest.master.controller;

import com.byteharvest.master.service.EventLogService;
import com.byteharvest.master.service.MasterLogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class LogsController {

    private final EventLogService eventLogService;
    private final MasterLogService masterLogService;

    public LogsController(EventLogService eventLogService, MasterLogService masterLogService) {
        this.eventLogService = eventLogService;
        this.masterLogService = masterLogService;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "ALL") String level,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return eventLogService.getRecent(level, limit);
    }

    @GetMapping(value = "/logs/master", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getMasterLogs(@RequestParam(defaultValue = "500") int lines) {
        return masterLogService.tail(lines);
    }
}
