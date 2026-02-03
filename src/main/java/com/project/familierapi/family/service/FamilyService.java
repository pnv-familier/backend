package com.project.familierapi.family.service;

import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.family.domain.FamilyRole;
import com.project.familierapi.family.dto.MyFamilyResponseDto;
import com.project.familierapi.family.repository.FamilyMemberRepository;
import com.project.familierapi.family.repository.FamilyRepository;
import com.project.familierapi.family.exception.FamilyCreationException;
import com.project.familierapi.user.domain.User;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.NoSuchElementException;
import java.util.Random;

@Service
public class FamilyService {
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBERS = "0123456789";

    public FamilyService(FamilyRepository familyRepository, FamilyMemberRepository familyMemberRepository, UserRepository userRepository) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
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

        return savedFamily;
    }

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
}