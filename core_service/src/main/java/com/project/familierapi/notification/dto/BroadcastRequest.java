package com.project.familierapi.notification.dto;

import lombok.Data;

@Data
public class BroadcastRequest {
    private String senderEmail;
    private String senderName;
    private String emotion;
    private String context;
    private String subType;
}
