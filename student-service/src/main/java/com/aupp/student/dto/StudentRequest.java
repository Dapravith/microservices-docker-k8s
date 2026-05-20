package com.aupp.student.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StudentRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String major;

    @NotNull @Min(1) @Max(8)
    private Integer year;

    @NotNull @DecimalMin("0.0") @DecimalMax("4.0")
    private Double gpa;
}
