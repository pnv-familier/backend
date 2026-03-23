package com.project.familierapi.notification.dto;

import lombok.Data;

@Data
public class SavePushTokenRequest {
    private String token;
    private String platform;
}
