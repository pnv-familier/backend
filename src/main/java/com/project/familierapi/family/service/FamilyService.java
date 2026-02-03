package com.project.familierapi.family.service;

import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.repository.FamilyRepository;
import com.project.familierapi.family.exception.FamilyCreationException;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class FamilyService {
    private final FamilyRepository familyRepository;
    
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBERS = "0123456789";

    public FamilyService(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    public Family createFamily(String name, String userId) {
        if (familyRepository.existsByUserId(userId)) {
            throw new FamilyCreationException("User already has a family. Cannot create another family.");
        }

        Family family = new Family();
        family.setName(name);
        family.setUserId(userId);
        
        String code;
        do {
            code = generateCode();
        } while (familyRepository.existsByInviteCode(code));
        
        family.setInviteCode(code);
        return familyRepository.save(family);
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