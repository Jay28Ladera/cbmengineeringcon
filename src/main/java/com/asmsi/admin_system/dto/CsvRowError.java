package com.asmsi.admin_system.dto;

public record CsvRowError(int line, String column, String value, String message) {}
