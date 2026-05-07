package com.aupp.student.dto;

import java.util.List;

public record AssignmentListResponse(int count, List<AssignmentResponse> assignments) {}
