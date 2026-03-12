package com.project.familierapi.shared.validation;

import com.project.familierapi.schedule.dto.CreateEventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, CreateEventRequest> {

    @Override
    public boolean isValid(CreateEventRequest request, ConstraintValidatorContext context) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            return true;
        }
        return request.getEndTime().isAfter(request.getStartTime());
    }
}
