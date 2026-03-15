package com.familier.ai.entity;

import lombok.Data;

@Data
public class EventPayload extends BasePayload {
    private String title;
    private String startTime, endTime; // HH:mm format
    private Integer date, month, year;
    private String location;
}
