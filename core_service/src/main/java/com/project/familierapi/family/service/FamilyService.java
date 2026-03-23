package com.project.familierapi.family.service;

import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.family.domain.FamilyRole;
import com.project.familierapi.family.domain.Relationship;
import com.project.familierapi.family.dto.MyFamilyResponseDto;
import com.project.familierapi.family.dto.FamilyMemberDto;
import com.project.familierapi.family.dto.FamilyMemberListResponseDto;
import com.project.familierapi.family.dto.FamilyPreviewDto;
import com.project.familierapi.family.repository.FamilyMemberRepository;
import com.project.familierapi.family.repository.FamilyRepository;
import com.project.familierapi.family.exception.FamilyCreationException;
import com.project.familierapi.family.exception.InvalidFamilyCodeException;
import com.project.familierapi.family.exception.UserAlreadyInFamilyException;
import com.project.familierapi.user.domain.User;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FamilyService {
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final com.project.familierapi.schedule.service.HolidayInitializerService holidayInitializerService;
    private final RelationshipInferenceService relationshipInferenceService;

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBERS = "0123456789";

    public FamilyService(FamilyRepository familyRepository, FamilyMemberRepository familyMemberRepository, UserRepository userRepository, com.project.familierapi.schedule.service.HolidayInitializerService holidayInitializerService, RelationshipInferenceService relationshipInferenceService) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.holidayInitializerService = holidayInitializerService;
        this.relationshipInferenceService = relationshipInferenceService;
    }

    @Transactional
    public Family createFamily(String name, User user) {
        if (familyRepository.existsByUser(user)) {
            throw new FamilyCreationException("User already has a family. Cannot create another family.");
        }

        Family family = new Family();
        family.setName(name);
        family.setUser(user);

        String code;
        do {
            code = generateCode();
        } while (familyRepository.existsByInviteCode(code));

        family.setInviteCode(code);
        Family savedFamily = familyRepository.save(family);

        FamilyMember familyMember = FamilyMember.builder()
                .family(savedFamily)
                .user(user)
                .role(FamilyRole.ADMIN)
                .nickname(user.getFullName())
                .build();
        familyMemberRepository.save(familyMember);

        // Initialize Vietnamese holidays for the new family
        holidayInitializerService.initializeVietnameseHolidays2026(savedFamily);

        return savedFamily;
    }

    @Transactional
    public MyFamilyResponseDto getMyFamily(User user) {
        FamilyMember familyMember = familyMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NoSuchElementException("User is not part of any family."));

        Family family = familyMember.getFamily();

        return new MyFamilyResponseDto(
                family.getId(),
                family.getName(),
                family.getInviteCode(),
                familyMember.getNickname(),
                familyMember.getRole(),
                familyMember.getJoinedAt()
        );
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder("FAM-");
        Random random = new Random();

        for (int i = 0; i < 3; i++) {
            sb.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }

        sb.append("-");

        for (int i = 0; i < 4; i++) {
            sb.append(NUMBERS.charAt(random.nextInt(NUMBERS.length())));
        }

        return sb.toString();
    }

    @Transactional
    public MyFamilyResponseDto joinFamily(String inviteCode, String relationshipStr, User user) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new InvalidFamilyCodeException("Please enter a family code");
        }

        String cleanCode = inviteCode.replaceAll("\\s+", "").toUpperCase();

        if (familyMemberRepository.findByUserId(user.getId()).isPresent()) {
            throw new UserAlreadyInFamilyException("You are already in a family. Leave current family first?");
        }

        Family family = familyRepository.findByInviteCode(cleanCode)
                .orElseThrow(() -> new InvalidFamilyCodeException("Invalid code. Please check and try again"));

        Relationship relationship;
        try {
            relationship = Relationship.valueOf(relationshipStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid relationship value");
        }

        FamilyMember familyMember = FamilyMember.builder()
                .family(family)
                .user(user)
                .role(FamilyRole.MEMBER)
                .nickname(user.getFullName())
                .relationship(relationship)
                .build();
        familyMemberRepository.save(familyMember);

        String adminId = family.getUser().getId();
        relationshipInferenceService.generateFamilyNetwork(user.getId(), family.getId(), adminId, relationship);

        return new MyFamilyResponseDto(
                family.getId(),
                family.getName(),
                family.getInviteCode(),
                familyMember.getNickname(),
                familyMember.getRole(),
                familyMember.getJoinedAt()
        );
    }

    @Transactional
    public FamilyMemberListResponseDto getFamilyMembers(User user) {
        FamilyMember currentUserMember = familyMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NoSuchElementException("User is not part of any family."));

        List<FamilyMember> members = familyMemberRepository.findByFamilyIdOrderByJoinedAt(currentUserMember.getFamily().getId());
        
        List<FamilyMemberDto> memberDtos = members.stream()
                .map(member -> new FamilyMemberDto(
                        member.getUser().getId(),
                        member.getNickname(),
                        member.getUser().getAvatarUrl(),
                        member.getRole(),
                        member.getJoinedAt()
                ))
                .collect(Collectors.toList());

        return new FamilyMemberListResponseDto(memberDtos, members.size() == 1, currentUserMember.getFamily().getCreatedAt());
    }

    @Transactional
    public FamilyPreviewDto getFamilyPreview(String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new InvalidFamilyCodeException("Please enter a family code");
        }

        String cleanCode = inviteCode.replaceAll("\\s+", "").toUpperCase();
        Family family = familyRepository.findByInviteCode(cleanCode)
                .orElseThrow(() -> new InvalidFamilyCodeException("Invalid family code"));

        List<FamilyMember> members = familyMemberRepository.findByFamilyIdOrderByJoinedAt(family.getId());
        int memberCount = members.size();

        return FamilyPreviewDto.builder()
                .familyId(family.getId())
                .familyName(family.getName())
                .admin(FamilyPreviewDto.AdminInfo.builder()
                        .fullName(family.getUser().getFullName())
                        .avatarUrl(family.getUser().getAvatarUrl())
                        .build())
                .memberCount(memberCount)
                .build();
    }

    @Transactional
    public com.project.familierapi.family.dto.FamilyMembersForMentionDto getMembersForMention(User user) {
        FamilyMember currentUserMember = familyMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NoSuchElementException("User is not part of any family."));

        List<FamilyMember> members = familyMemberRepository.findByFamilyIdOrderByJoinedAt(currentUserMember.getFamily().getId());
        
        List<com.project.familierapi.family.dto.FamilyMembersForMentionDto.MemberInfo> memberInfos = members.stream()
                .filter(member -> !member.getUser().getId().equals(user.getId())) // Exclude current user
                .map(member -> com.project.familierapi.family.dto.FamilyMembersForMentionDto.MemberInfo.builder()
                        .email(member.getUser().getEmail())
                        .fullName(member.getUser().getFullName())
                        .build())
                .collect(Collectors.toList());

        return com.project.familierapi.family.dto.FamilyMembersForMentionDto.builder()
                .members(memberInfos)
                .build();
    }
}