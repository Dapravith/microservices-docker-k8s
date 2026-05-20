package com.aupp.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmissionRequest(
        @NotBlank String taskId,
        @NotBlank @Size(max = 4000) String answer
) {
}
