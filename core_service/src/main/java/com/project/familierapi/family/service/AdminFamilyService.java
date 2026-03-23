package com.project.familierapi.family.service;

import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.family.domain.FamilyRole;
import com.project.familierapi.family.dto.AdminCreateFamilyRequest;
import com.project.familierapi.family.dto.AdminFamilyResponse;
import com.project.familierapi.family.exception.FamilyCreationException;
import com.project.familierapi.family.repository.FamilyMemberRepository;
import com.project.familierapi.family.repository.FamilyRepository;
import com.project.familierapi.schedule.service.HolidayInitializerService;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AdminFamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final HolidayInitializerService holidayInitializerService;

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBERS = "0123456789";

    @Transactional(readOnly = true)
    public Page<AdminFamilyResponse> getFamilies(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return familyRepository.searchFamilies(kw, pageable).map(this::toResponse);
    }

    @Transactional
    public AdminFamilyResponse createFamily(AdminCreateFamilyRequest request) {
        User owner = userRepository.findByEmail(request.getOwnerEmail())
                .orElseThrow(() -> new NoSuchElementException("User with email '" + request.getOwnerEmail() + "' not found"));

        if (familyRepository.existsByUser(owner)) {
            throw new FamilyCreationException("This user already owns a family");
        }

        Family family = new Family();
        family.setName(request.getFamilyName());
        family.setUser(owner);

        String code;
        Random random = new Random();
        do {
            StringBuilder sb = new StringBuilder("FAM-");
            for (int i = 0; i < 3; i++) sb.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
            sb.append("-");
            for (int i = 0; i < 4; i++) sb.append(NUMBERS.charAt(random.nextInt(NUMBERS.length())));
            code = sb.toString();
        } while (familyRepository.existsByInviteCode(code));

        family.setInviteCode(code);
        Family saved = familyRepository.save(family);

        familyMemberRepository.save(FamilyMember.builder()
                .family(saved)
                .user(owner)
                .role(FamilyRole.ADMIN)
                .nickname(owner.getFullName())
                .build());

        holidayInitializerService.initializeVietnameseHolidays2026(saved);

        return toResponse(saved);
    }

    private AdminFamilyResponse toResponse(Family family) {
        int memberCount = familyMemberRepository.findByFamilyIdOrderByJoinedAt(family.getId()).size();
        return AdminFamilyResponse.builder()
                .familyId(family.getId())
                .familyName(family.getName())
                .ownerEmail(family.getUser().getEmail())
                .memberCount(memberCount)
                .createdAt(family.getCreatedAt())
                .status("ACTIVE")
                .build();
    }
}
