package com.aupp.teacher.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class TeacherRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String department;

    @NotNull @Size(min = 1, message = "at least one course")
    private List<String> courses;

    @NotNull @Min(0) @Max(60)
    private Integer yearsOfExperience;
}
