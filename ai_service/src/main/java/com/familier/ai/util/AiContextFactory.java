package com.familier.ai.util;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.familier.ai.entity.AiContextVars;

@Service
public class AiContextFactory {

    public Map<String, String> createCurrentContext(Long userId) {
        // TODO: 1. Lấy dữ liệu thật từ Database (Single Source of Truth)
        // User user = userService.findById(userId);

        // TODO: 2. Map dữ liệu vào bộ biến tiêu chuẩn
        Map<String, String> context = new HashMap<>();
        context.put(AiContextVars.USER_NAME, "Lê Kỳ Bá");
        context.put(AiContextVars.USER_ROLE, "Sinh viên năm cuối");
        context.put(AiContextVars.FAMILY_NAME, "Gia đình Lê Kỳ");
        context.put(AiContextVars.CURRENT_TIME, LocalDateTime.now().toString());

        return context;
    }
}