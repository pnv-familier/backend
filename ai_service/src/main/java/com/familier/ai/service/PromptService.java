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
        int currentYear = java.time.Year.now().getValue();
        
        String instruction = "\n\n# SUGGESTION METADATA INSTRUCTION\n" +
                "Bạn đã phát hiện một hành động tiềm năng. Hãy hỏi người dùng xác nhận trong văn bản một cách ngắn gọn (ví dụ: \"Bạn có muốn mình...?\").\n" +
                "Ở cuối phần hồi của bạn, thêm thẻ <suggestion_metadata> chứa JSON object theo cấu trúc sau:\n\n";
        
        switch (suggestionType) {
            case "EVENT":
                instruction += "{ \"title\": string, \"startTime\": \"h:mm AM/PM\", \"endTime\": \"h:mm AM/PM\", \"date\": int, \"month\": int, \"year\": int, \"location\": string|null }\n\n" +
                        "MỤC ĐÍCH: Tạo sự kiện có thời gian và địa điểm cụ thể\n\n" +
                        "HƯỚNG DẪN EXTRACT:\n" +
                        "- title: Tóm tắt sự kiện từ tin nhắn (ví dụ: \"Đưa bố đi khám\")\n" +
                        "- startTime/endTime: Parse từ tin nhắn theo format \"h:mm AM/PM\" (1-12 AM/PM, không dùng 24h). Ví dụ: \"9:00 AM\", \"2:30 PM\". Nếu chỉ có 1 giờ, endTime = startTime + 1 giờ\n" +
                        "- date/month/year: Tính từ ngày hiện tại. \"Mai\" = hôm nay + 1 ngày, \"Thứ 7\" = tính đến thứ 7 tuần này\n" +
                        "  LƯU Ý: Năm hiện tại là " + currentYear + "\n" +
                        "- location: Extract từ tin nhắn HOẶC SỬ DỤNG {{facts}}/{{TARGET_PROFILE_WITH_RELATION}} để suggest địa điểm phù hợp với sở thích\n\n" +
                        "VÍ DỤ:\n" +
                        "- User: \"Mai 9h đưa bố đi bệnh viện\"\n" +
                        "  → { \"title\": \"Đưa bố đi khám\", \"startTime\": \"9:00 AM\", \"endTime\": \"10:00 AM\", \"date\": 15, \"month\": 1, \"year\": " + currentYear + ", \"location\": \"Bệnh viện\" }\n\n" +
                        "- User: \"Thứ 7 này 6h tối đi ăn với gia đình\"\n" +
                        "  {{facts}}: \"Bố thích ăn hải sản\"\n" +
                        "  → { \"title\": \"Ăn tối cùng gia đình\", \"startTime\": \"6:00 PM\", \"endTime\": \"8:00 PM\", \"date\": 18, \"month\": 1, \"year\": " + currentYear + ", \"location\": \"Nhà hàng hải sản\" }\n\n" +
                        "VALIDATION:\n" +
                        "- startTime phải < endTime\n" +
                        "- date hợp lệ (1-31), month (1-12)\n" +
                        "- Nếu thiếu thông tin quan trọng (thời gian/ngày), HỎI user trước khi tạo metadata";
                break;
            case "TASK":
                instruction += "{ \"assigneeEmail\": string, \"title\": string, \"description\": string }\n\n" +
                        "MỤC ĐÍCH: Tạo công việc chăm sóc nhẹ nhàng cho thành viên cụ thể\n\n" +
                        "HƯỚNG DẪN EXTRACT:\n" +
                        "- assigneeEmail: Backend tự động inject (KHÔNG cần điền)\n" +
                        "- title: Công việc NHẸ NHÀNG, cụ thể, dễ thực hiện (ví dụ: \"Complete homework\", \"Uống nước ấm\", \"Đi dạo 15 phút\", \"Gọi điện cho mẹ\")\n" +
                        "  * Tránh: Công việc nặng nề, mơ hồ, hoặc quá phức tạp\n" +
                        "  * Ưu tiên: Hành động cụ thể, có thể hoàn thành trong 30 phút\n" +
                        "- description: MỤC ĐÍCH + LÝ DO + NGỮ CẢNH. Cấu trúc:\n" +
                        "  \"Tạo love task này cho gia đình bạn vì [TÊN USER HIỆN TẠI] [NGỮ CẢNH/LÝ DO]. [CHI TIẾT THÊM từ {{facts}} nếu có]\"\n" +
                        "  * Ví dụ: \"Tạo love task này cho gia đình bạn vì Minh đang bận công việc và cần thư giãn. Một bước đi nhẹ nhàng sẽ giúp Minh giải tỏa căng thẳng.\"\n" +
                        "  * Ví dụ: \"Tạo love task này cho gia đình bạn vì Hương vừa nói rằng cô ấy mệt mỏi. Uống nước ấm sẽ giúp cô ấy cảm thấy tốt hơn.\"\n\n" +
                        "VÍ DỤ:\n" +
                        "- User: \"Hôm nay mệt quá\"\n" +
                        "  → { \"title\": \"Uống nước ấm\", \"description\": \"Tạo love task này cho gia đình bạn vì bạn đang mệt mỏi. Uống nước ấm sẽ giúp bạn thư giãn và phục hồi năng lượng.\" }\n\n" +
                        "- User: \"Stress công việc quá, không biết làm gì\"\n" +
                        "  {{facts}}: \"Bạn thích nghe nhạc\"\n" +
                        "  → { \"title\": \"Nghe nhạc yêu thích 15 phút\", \"description\": \"Tạo love task này cho gia đình bạn vì bạn đang stress công việc. Nghe nhạc yêu thích sẽ giúp bạn giải tỏa căng thẳng và tái tập trung.\" }\n\n" +
                        "- User: \"Mẹ vừa nói mẹ đau lưng\"\n" +
                        "  → { \"title\": \"Massage lưng cho mẹ\", \"description\": \"Tạo love task này cho gia đình bạn vì mẹ đang đau lưng. Một bàn tay nhẹ nhàng sẽ giúp mẹ cảm thấy thoải mái hơn.\" }\n\n" +
                        "VALIDATION:\n" +
                        "- title phải ngắn gọn, cụ thể, dễ thực hiện\n" +
                        "- description phải có: LÝ DO (vì sao) + NGỮ CẢNH (người dùng đang như thế nào) + LỢI ÍCH (sẽ giúp gì)\n" +
                        "- Không tạo task quá phức tạp hoặc mất thời gian";
                break;
            case "OFFLINE":
                instruction += "{ \"action\": string }\n\n" +
                        "MỤC ĐÍCH: Khuyến khích người dùng kết nối THỰC TẾ với gia đình (không qua AI)\n\n" +
                        "HƯỚNG DẪN:\n" +
                        "- action phải là hành động CỤ THỂ, THỰC TẾ mà user có thể làm NGAY\n" +
                        "- SỬ DỤNG {{facts}} và {{TARGET_PROFILE_WITH_RELATION}} để suggest hành động phù hợp với sở thích/hoàn cảnh\n" +
                        "- Tập trung vào kết nối trực tiếp: gọi điện, gặp mặt, cùng làm việc gì đó\n\n" +
                        "VÍ DỤ:\n" +
                        "- User: \"Hôm nay mệt quá\"\n" +
                        "  {{facts}}: \"Mẹ thích nấu ăn\", \"Bố thích câu cá\"\n" +
                        "  → { \"action\": \"Gọi điện cho mẹ tâm sự hoặc xuống bếp nấu món mẹ thích cùng nhau. Kết nối thực tế sẽ giúp bạn thư giãn hơn.\" }\n\n" +
                        "- User: \"Stress công việc quá\"\n" +
                        "  {{facts}}: \"Bố hay đi câu cá cuối tuần\"\n" +
                        "  → { \"action\": \"Rủ bố đi câu cá cuối tuần này. Dành thời gian bên nhau sẽ giúp bạn giải tỏa căng thẳng.\" }\n\n" +
                        "- User: \"Cảm thấy cô đơn\"\n" +
                        "  → { \"action\": \"Gọi điện cho gia đình hoặc về nhà ăn cơm cùng nhau. Đôi khi chỉ cần ngồi bên nhau cũng đủ ấm lòng rồi.\" }";
                break;
        }
        
        instruction += "\n\nLưu ý: KHÔNG bao gồm thẻ <suggestions> cũ nữa. Chỉ dùng <suggestion_metadata>.";
        
        return content + instruction;
    }

    private String enrichPrompt(String content, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return content;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (content.contains(placeholder)) {
                content = content.replace(placeholder, entry.getValue());
            }
        }
        System.out.println("This is a prompt after chat" + content);
        return content;
    }

    private String loadRawContent(String fileName) throws Exception {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + fileName + ".txt");

        try (java.io.InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

}
