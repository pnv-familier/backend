package com.familier.ai.config;

import com.familier.ai.entity.*;
import com.familier.ai.repository.SuggestionRepository;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class SuggestionDataInitializer implements ApplicationRunner {

    private final SuggestionRepository suggestionRepository;

    public SuggestionDataInitializer(SuggestionRepository suggestionRepository) {
        this.suggestionRepository = suggestionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        suggestionRepository.count()
                .filter(count -> count == 0)
                .flatMapMany(count -> suggestionRepository.saveAll(seedData()))
                .subscribe();
    }

    private List<Suggestion> seedData() {
        String email = "A@gmail.com";
        Instant now = Instant.now();
        Instant expiry = now.plus(7, ChronoUnit.DAYS);

        return List.of(
                Suggestion.builder()
                        .type(SuggestionType.TASK)
                        .receiverEmail(email)
                        .title("Nau bua toi cung nhau")
                        .description("Hay danh thoi gian nau mot bua an dac biet cung nguoi than toi nay.")
                        .payload(new Document("type", "TASK")
                                .append("assigneeEmail", email)
                                .append("title", "Nau bua toi cung nhau")
                                .append("description", "Chuan bi mon an yeu thich cua ca gia dinh"))
                        .status(SuggestionStatus.PENDING)
                        .createdAt(now)
                        .expiredAt(expiry)
                        .triggerContext("Gia dinh ban chua co bua an chung trong 3 ngay qua.")
                        .build(),

                Suggestion.builder()
                        .type(SuggestionType.EVENT)
                        .receiverEmail(email)
                        .title("Da ngoai cuoi tuan")
                        .description("Mot buoi da ngoai se giup ca gia dinh gan ket hon sau tuan lam viec ban ron.")
                        .payload(new Document("type", "EVENT")
                                .append("title", "Da ngoai cuoi tuan")
                                .append("startTime", "09:00")
                                .append("endTime", "17:00")
                                .append("date", 22)
                                .append("month", 3)
                                .append("year", 2026)
                                .append("location", "Cong vien Gia Dinh"))
                        .status(SuggestionStatus.PENDING)
                        .createdAt(now.minus(1, ChronoUnit.DAYS))
                        .expiredAt(expiry)
                        .triggerContext("Cuoi tuan nay ca gia dinh deu ranh theo lich.")
                        .build(),

                Suggestion.builder()
                        .type(SuggestionType.OFFLINE)
                        .receiverEmail(email)
                        .title("Di dao buoi toi")
                        .description("Mot buoi di dao ngan cung nhau se giup giam stress va tang ket noi.")
                        .payload(new Document("type", "OFFLINE")
                                .append("action", "Di dao quanh khu pho 30 phut sau bua toi"))
                        .status(SuggestionStatus.ACCEPTED)
                        .createdAt(now.minus(2, ChronoUnit.DAYS))
                        .expiredAt(expiry)
                        .triggerContext("Ban va gia dinh it van dong ngoai troi trong tuan nay.")
                        .build(),

                Suggestion.builder()
                        .type(SuggestionType.TASK)
                        .receiverEmail(email)
                        .title("Viet thu tay cho ba me")
                        .description("Mot la thu tay chan thanh se khien ba me cam thay duoc yeu thuong hon bat ky tin nhan nao.")
                        .payload(new Document("type", "TASK")
                                .append("assigneeEmail", email)
                                .append("title", "Viet thu tay cho ba me")
                                .append("description", "The hien tinh cam truc tiep voi ba me"))
                        .status(SuggestionStatus.PENDING)
                        .createdAt(now.minus(3, ChronoUnit.DAYS))
                        .expiredAt(expiry)
                        .triggerContext("Da lau ban chua the hien tinh cam truc tiep voi ba me.")
                        .build()
        );
    }
}
