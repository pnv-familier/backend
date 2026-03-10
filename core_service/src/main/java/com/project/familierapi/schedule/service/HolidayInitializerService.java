package com.project.familierapi.schedule.service;

import com.project.familierapi.family.domain.Family;
import com.project.familierapi.schedule.domain.FamilyEvent;
import com.project.familierapi.schedule.repository.FamilyEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HolidayInitializerService {
    private final FamilyEventRepository eventRepository;

    @Transactional
    public void initializeVietnameseHolidays2026(Family family) {
        List<FamilyEvent> holidays = new ArrayList<>();

        holidays.add(createEvent(family, "Lễ Mừng Thọ - Tết Nguyên Đán",
                "Ngày lễ truyền thống để chúc phúc cho người cao tuổi trong gia đình. Đây là dịp để con cháu thể hiện lòng hiếu thảo và mong ông bà sống lâu trăm tuổi.",
                LocalDateTime.of(2026, 2, 17, 8, 0),
                LocalDateTime.of(2026, 2, 17, 20, 0),
                "Nhà riêng"));

        holidays.add(createEvent(family, "Ngày Quốc tế Phụ nữ 8/3",
                "Ngày tôn vinh phụ nữ trên toàn thế giới. Đây là dịp để tặng quà và bày tỏ lòng biết ơn đến mẹ, bà, vợ và các thành viên nữ trong gia đình.",
                LocalDateTime.of(2026, 3, 8, 0, 0),
                LocalDateTime.of(2026, 3, 8, 23, 59),
                ""));

        holidays.add(createEvent(family, "Ngày của Mẹ - Mother's Day",
                "Ngày quốc tế để tôn vinh và tri ân công lao sinh thành, dưỡng dục của người mẹ. Hãy dành thời gian bên mẹ và bày tỏ tình yêu thương.",
                LocalDateTime.of(2026, 5, 10, 0, 0),
                LocalDateTime.of(2026, 5, 10, 23, 59),
                ""));

        holidays.add(createEvent(family, "Ngày Quốc tế Thiếu nhi 1/6",
                "Ngày dành cho trẻ em trên toàn thế giới. Đây là dịp để tổ chức các hoạt động vui chơi và tặng quà cho con cái.",
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 23, 59),
                ""));

        holidays.add(createEvent(family, "Ngày của Cha - Father's Day",
                "Ngày quốc tế để tôn vinh và tri ân công lao của người cha. Hãy dành thời gian bên cha và bày tỏ lòng biết ơn.",
                LocalDateTime.of(2026, 6, 21, 0, 0),
                LocalDateTime.of(2026, 6, 21, 23, 59),
                ""));

        holidays.add(createEvent(family, "Ngày Gia đình Việt Nam 28/6",
                "Ngày lễ để tất cả các thành viên trong gia đình sum họp, chia sẻ và gắn kết tình cảm. Đây là dịp để gia đình quây quần bên nhau.",
                LocalDateTime.of(2026, 6, 28, 0, 0),
                LocalDateTime.of(2026, 6, 28, 23, 59),
                "Nhà riêng"));

        holidays.add(createEvent(family, "Lễ Vu Lan - Ngày Báo hiếu",
                "Ngày lễ quan trọng nhất trong văn hóa Việt Nam để con cái bày tỏ lòng biết ơn sâu sắc đến cha mẹ và tổ tiên. Nhằm ngày 15/7 Âm lịch.",
                LocalDateTime.of(2026, 8, 28, 0, 0),
                LocalDateTime.of(2026, 8, 28, 23, 59),
                "Chùa hoặc nhà riêng"));

        holidays.add(createEvent(family, "Tết Trung Thu - Tết Đoàn viên",
                "Tết của trẻ em và là dịp để gia đình sum họp, đoàn viên. Nhằm ngày 15/8 Âm lịch. Hãy cùng nhau thưởng trăng, ăn bánh trung thu và vui chơi.",
                LocalDateTime.of(2026, 9, 25, 18, 0),
                LocalDateTime.of(2026, 9, 25, 22, 0),
                "Nhà riêng"));

        holidays.add(createEvent(family, "Ngày Quốc tế Người cao tuổi 1/10",
                "Ngày để tri ân công lao của ông bà, người cao tuổi trong gia đình. Hãy dành thời gian thăm hỏi và chăm sóc ông bà.",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 23, 59),
                ""));

        holidays.add(createEvent(family, "Ngày Phụ nữ Việt Nam 20/10",
                "Ngày tôn vinh phụ nữ Việt Nam. Đây là dịp để tặng quà và bày tỏ lòng biết ơn đến mẹ, bà, vợ và các thành viên nữ trong gia đình.",
                LocalDateTime.of(2026, 10, 20, 0, 0),
                LocalDateTime.of(2026, 10, 20, 23, 59),
                ""));

        eventRepository.saveAll(holidays);
    }

    private FamilyEvent createEvent(Family family, String title, String description,
                                    LocalDateTime startTime, LocalDateTime endTime, String location) {
        return FamilyEvent.builder()
                .family(family)
                .creator(family.getUser())
                .title(title)
                .description(description)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .build();
    }
}