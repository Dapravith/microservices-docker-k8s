package com.aupp.teacher.service;

import com.aupp.teacher.dto.AssignmentListResponse;
import com.aupp.teacher.dto.AssignmentResponse;
import com.aupp.teacher.dto.CreateAssignmentRequest;
import com.aupp.teacher.web.CallerIdentity;

public interface TeacherAssignmentService {

    AssignmentResponse create(CallerIdentity caller, CreateAssignmentRequest req);

    AssignmentListResponse search(CallerIdentity caller, String titleQuery);

    void remove(CallerIdentity caller, String id);
}
