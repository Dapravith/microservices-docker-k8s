package com.aupp.student.service;

import com.aupp.student.dto.AssignmentResponse;
import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.dto.UpdateAssignmentRequest;
import com.aupp.student.web.CallerIdentity;

import java.util.List;

public interface AssignmentService {

    AssignmentResponse submit(CallerIdentity caller, SubmitAssignmentRequest req);

    List<AssignmentResponse> listMine(CallerIdentity caller);

    AssignmentResponse updateLatest(CallerIdentity caller, UpdateAssignmentRequest req);

    AssignmentResponse resubmitLatest(CallerIdentity caller, UpdateAssignmentRequest req);
}
