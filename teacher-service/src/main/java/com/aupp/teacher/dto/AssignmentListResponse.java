package com.aupp.teacher.dto;

import java.util.List;

public record AssignmentListResponse(int count, List<AssignmentResponse> assignments) {}
