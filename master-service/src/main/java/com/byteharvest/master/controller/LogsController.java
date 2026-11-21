package com.byteharvest.master.controller;

import com.byteharvest.master.service.EventLogService;
import com.byteharvest.master.service.MasterLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Logs", description = "Recent events and the master log file")
public class LogsController {

    private final EventLogService eventLogService;
    private final MasterLogService masterLogService;

    public LogsController(EventLogService eventLogService, MasterLogService masterLogService) {
        this.eventLogService = eventLogService;
        this.masterLogService = masterLogService;
    }

    @Operation(summary = "Recent event logs", description = "In-memory events (uploads, heartbeats, repairs). Lost on restart.")
    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs(
            @Parameter(description = "ALL, INFO, WARN or ERROR") @RequestParam(defaultValue = "ALL") String level,
            @Parameter(description = "Maximum number of events") @RequestParam(defaultValue = "100") int limit
    ) {
        return eventLogService.getRecent(level, limit);
    }

    @Operation(summary = "Master log file tail", description = "Last lines of logs/master-service.log as plain text.")
    @GetMapping(value = "/logs/master", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getMasterLogs(
            @Parameter(description = "Number of lines from the end") @RequestParam(defaultValue = "500") int lines) {
        return masterLogService.tail(lines);
    }
}
