package com.familier.ai.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class PromptService {
    private final ResourceLoader resourceLoader;

    public PromptService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadSystemPrompt(String fileName, Map<String, String> variables, boolean includeSuggestionMetadata, String suggestionType) throws Exception {
        String content = loadRawContent(fileName);
        content = enrichPrompt(content, variables);
        
        if (includeSuggestionMetadata && suggestionType != null) {
            content = injectSuggestionMetadataInstruction(content, suggestionType);
        }
        
        return content;
    }

    private String injectSuggestionMetadataInstruction(String content, String suggestionType) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateRef = today.format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", new java.util.Locale("vi", "VN")));

        StringBuilder sb = new StringBuilder(content);
        sb.append("\n\n# INSTRUCTION: SUGGESTION METADATA");
        sb.append("\n- NGÀY HIỆN TẠI: ").append(dateRef);
        sb.append("\n- YÊU CẦU: Xác nhận hành động với người dùng ngắn gọn (Ví dụ: \"Bạn có muốn mình...?\").");
        sb.append("\n- ĐỊNH DẠNG: Thêm <suggestion_metadata>{JSON}</suggestion_metadata> cuối phản hồi.");

        switch (suggestionType) {
            case "EVENT":
                sb.append("\n- MỤC TIÊU: Tạo lịch hẹn (Dù thiếu giờ vẫn detect nếu rõ ý định).")
                        .append("\n- JSON: { \"title\": str, \"startTime\": \"h:mm AM/PM\"|null, \"endTime\": \"h:mm AM/PM\"|null, \"date\": int, \"month\": int, \"year\": int, \"location\": str|null }")
                        .append("\n- CHIẾN THUẬT: ")
                        .append("\n  + Nếu thiếu giờ: Để startTime/endTime là No time. KHÔNG tự bịa giờ.")
                        .append("\n  + Nếu có 1 mốc giờ: endTime = startTime + 1h.")
                        .append("\n  + Tính ngày: 'Mai'=+1, 'Mốt'=+2, 'Cuối tuần'=Thứ 7 tuần này. Dựa trên mốc: ")
                        .append(dateRef)
                        .append("\n  + Location: Ưu tiên {{facts}} hoặc target_profile nếu tin nhắn không nêu.");
                break;

            case "TASK":
                sb.append("\n- MỤC TIÊU: Love Task chăm sóc (Dựa trên trạng thái cảm xúc/sức khỏe).")
                        .append("\n- JSON: { \"title\": str, \"description\": str }")
                        .append("\n- CHIẾN THUẬT: ")
                        .append("\n  + Title: Hành động cụ thể < 30p (Mua thuốc, Massage, Nhắc uống nước).")
                        .append("\n  + Description: Cấu trúc [Lý do] + [Ngữ cảnh từ tin nhắn/Facts] + [Lợi ích].")
                        .append("\n  + Linh hoạt: Phát hiện nhu cầu ngầm (Ví dụ: 'Mẹ than mệt' -> Task: 'Pha trà gừng cho mẹ').");
                break;

            case "OFFLINE":
                sb.append("\n- MỤC TIÊU: Chuyển đổi trạng thái tiêu cực/xa cách thành hành động kết nối vật lý.")
                        .append("\n- CHIẾN THUẬT NHẬN DIỆN:")
                        .append("\n  1. Cảm xúc: Buồn, mệt, cô đơn, áp lực, hoài niệm (nhớ quê, nhớ món ăn).")
                        .append("\n  2. Sự vắng bóng: Nhắc đến việc lâu rồi không làm gì đó với người thân.")
                        .append("\n- YÊU CẦU ACTION:")
                        .append("\n  + Phải là hành động không dùng điện thoại (Ăn cơm, đi dạo, tặng quà trực tiếp, ôm, trò chuyện).")
                        .append("\n  + JSON: { \"title\": str, \"description\": str }")
                        .append("\n  + Description: Nếu không có, để là: \"Hành động chăm sóc ý nghĩa dành cho gia đình\".")
                        .append("\n  + Ưu tiên sử dụng {{facts}} để cá nhân hóa. Nếu {{facts}} nói 'Bố thích cây cảnh', khi User buồn hãy gợi ý: 'Rủ bố đi tỉa cây cảnh cùng nhau'.");
                break;
        }

        sb.append("\n- LƯU Ý: Không dùng thẻ <suggestions> cũ. Chỉ dùng <suggestion_metadata>.");
        return sb.toString();
    }

    private String enrichPrompt(String content, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return content;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (content.contains(placeholder)) {
                content = content.replace(placeholder, entry.getValue());
            }
        }
        return content;
    }

    private String loadRawContent(String fileName) throws Exception {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + fileName + ".txt");

        try (java.io.InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

}
