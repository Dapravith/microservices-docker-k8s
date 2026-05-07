package com.aupp.student.service;

import com.aupp.student.dto.AssignmentListResponse;
import com.aupp.student.dto.AssignmentResponse;
import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.dto.UpdateAssignmentRequest;
import com.aupp.student.web.CallerIdentity;

public interface AssignmentService {

    AssignmentResponse submit(CallerIdentity caller, SubmitAssignmentRequest req);

    AssignmentListResponse listMine(CallerIdentity caller);

    AssignmentResponse updateLatest(CallerIdentity caller, UpdateAssignmentRequest req);

    AssignmentResponse resubmitLatest(CallerIdentity caller, UpdateAssignmentRequest req);
}
