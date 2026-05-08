package com.aupp.student.dto;

import jakarta.validation.constraints.Size;

public record UpdateAssignmentRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String content
) {}
