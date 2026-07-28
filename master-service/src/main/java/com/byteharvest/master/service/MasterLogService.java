package com.byteharvest.master.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class MasterLogService {

    private static final int MAX_LINES = 2000;

    private final Path logFilePath;

    public MasterLogService(@Value("${logging.file.name:logs/master-service.log}") String logFileName) {
        this.logFilePath = Path.of(logFileName);
    }

    public String tail(int lines) {
        int safeLines = Math.max(1, Math.min(lines, MAX_LINES));

        if (!Files.exists(logFilePath)) {
            return "Master log file is not available yet: " + logFilePath.toAbsolutePath();
        }

        try {
            List<String> allLines = Files.readAllLines(logFilePath, StandardCharsets.UTF_8);
            int start = Math.max(0, allLines.size() - safeLines);
            return String.join(System.lineSeparator(), allLines.subList(start, allLines.size()));
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read master log file", exception);
        }
    }
}
