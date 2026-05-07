package com.aupp.student.service.impl;

import com.aupp.student.domain.Assignment;
import com.aupp.student.domain.AssignmentStatus;
import com.aupp.student.dto.AssignmentListResponse;
import com.aupp.student.dto.AssignmentResponse;
import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.dto.UpdateAssignmentRequest;
import com.aupp.student.exception.AssignmentNotFoundException;
import com.aupp.student.exception.MissingCallerIdentityException;
import com.aupp.student.repository.AssignmentRepository;
import com.aupp.student.service.AssignmentService;
import com.aupp.student.web.CallerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AssignmentServiceImpl implements AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentServiceImpl.class);
    private static final Sort BY_CREATED_DESC = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AssignmentRepository repo;

    public AssignmentServiceImpl(AssignmentRepository repo) {
        this.repo = repo;
    }

    @Override
    public AssignmentResponse submit(CallerIdentity caller, SubmitAssignmentRequest req) {
        requireCaller(caller);
        Assignment saved = repo.save(Assignment.builder()
                .studentEmail(caller.email())
                .title(req.title())
                .content(req.content())
                .status(AssignmentStatus.SUBMITTED)
                .build());
        log.info("submit assignment id={} by {}", saved.getId(), caller.email());
        return AssignmentResponse.of(saved);
    }

    @Override
    public AssignmentListResponse listMine(CallerIdentity caller) {
        requireCaller(caller);
        List<Assignment> mine = repo.findByStudentEmail(caller.email(), BY_CREATED_DESC);
        return new AssignmentListResponse(mine.size(), mine.stream().map(AssignmentResponse::of).toList());
    }

    @Override
    public AssignmentResponse updateLatest(CallerIdentity caller, UpdateAssignmentRequest req) {
        requireCaller(caller);
        Assignment latest = repo.findFirstByStudentEmailOrderByCreatedAtDesc(caller.email())
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment to update for " + caller.email()));
        applyPatch(latest, req);
        Assignment saved = repo.save(latest);
        log.info("updated assignment id={} by {}", saved.getId(), caller.email());
        return AssignmentResponse.of(saved);
    }

    @Override
    public AssignmentResponse resubmitLatest(CallerIdentity caller, UpdateAssignmentRequest req) {
        requireCaller(caller);
        Assignment latest = repo.findFirstByStudentEmailOrderByCreatedAtDesc(caller.email())
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment to resubmit for " + caller.email()));
        applyPatch(latest, req);
        latest.setStatus(AssignmentStatus.RESUBMITTED);
        Assignment saved = repo.save(latest);
        log.info("resubmitted assignment id={} by {}", saved.getId(), caller.email());
        return AssignmentResponse.of(saved);
    }

    private static void applyPatch(Assignment target, UpdateAssignmentRequest req) {
        if (StringUtils.hasText(req.title())) {
            target.setTitle(req.title());
        }
        if (req.content() != null) {
            target.setContent(req.content());
        }
    }

    private static void requireCaller(CallerIdentity caller) {
        if (caller == null || !StringUtils.hasText(caller.email())) {
            throw new MissingCallerIdentityException("caller identity (X-User-Email) missing");
        }
    }
}
