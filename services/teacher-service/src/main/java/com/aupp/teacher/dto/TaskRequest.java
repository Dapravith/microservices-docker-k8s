package com.aupp.teacher.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 80) String course,
        @NotNull @FutureOrPresent LocalDate dueDate,
        @Min(1) @Max(1000) int maxScore
) {
}
