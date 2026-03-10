package com.project.familierapi.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @NotBlank(message = "Date of birth is required")
    private String dateOfBirth;
    
    @NotBlank(message = "Gender is required")
    private String gender;
    
    @Size(max = 5, message = "Maximum 5 hobbies allowed")
    private List<String> hobbies;
}
