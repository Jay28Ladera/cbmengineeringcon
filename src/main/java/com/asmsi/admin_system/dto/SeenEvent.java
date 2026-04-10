package com.asmsi.admin_system.dto;

import lombok.Data;

@Data
public class SeenEvent {
    private String from; // who saw
    private String to;   // whose messages were seen
}