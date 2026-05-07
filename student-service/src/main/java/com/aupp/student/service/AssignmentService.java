package com.aupp.student.service;

import com.aupp.student.domain.Assignment;
import com.aupp.student.dto.AssignmentListResponse;
import com.aupp.student.dto.AssignmentResponse;
import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.dto.UpdateAssignmentRequest;
import com.aupp.student.exception.AssignmentNotFoundException;
import com.aupp.student.exception.MissingCallerIdentityException;
import com.aupp.student.repository.AssignmentRepository;
import com.aupp.student.web.CallerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final AssignmentRepository repo;

    public AssignmentService(AssignmentRepository repo) {
        this.repo = repo;
    }

    public AssignmentResponse submit(CallerIdentity caller, SubmitAssignmentRequest req) {
        requireCaller(caller);
        Assignment saved = repo.save(Assignment.builder()
                .studentEmail(caller.email())
                .title(req.title())
                .content(req.content())
                .status("SUBMITTED")
                .build());
        log.info("submit assignment id={} by {}", saved.getId(), caller.email());
        return AssignmentResponse.of(saved);
    }

    public AssignmentListResponse listMine(CallerIdentity caller) {
        requireCaller(caller);
        List<Assignment> mine = repo.findByStudentEmail(caller.email(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return new AssignmentListResponse(mine.size(), mine.stream().map(AssignmentResponse::of).toList());
    }

    public AssignmentResponse updateLatest(CallerIdentity caller, UpdateAssignmentRequest req) {
        requireCaller(caller);
        Assignment latest = repo.findFirstByStudentEmailOrderByCreatedAtDesc(caller.email())
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment to update for " + caller.email()));
        if (StringUtils.hasText(req.title())) {
            latest.setTitle(req.title());
        }
        if (req.content() != null) {
            latest.setContent(req.content());
        }
        Assignment saved = repo.save(latest);
        log.info("updated assignment id={} by {}", saved.getId(), caller.email());
        return AssignmentResponse.of(saved);
    }

    public AssignmentResponse resubmitLatest(CallerIdentity caller, UpdateAssignmentRequest req) {
        requireCaller(caller);
        Assignment latest = repo.findFirstByStudentEmailOrderByCreatedAtDesc(caller.email())
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment to resubmit for " + caller.email()));
        if (StringUtils.hasText(req.title())) {
            latest.setTitle(req.title());
        }
        if (req.content() != null) {
            latest.setContent(req.content());
        }
        latest.setStatus("RESUBMITTED");
        Assignment saved = repo.save(latest);
        log.info("resubmitted assignment id={} by {}", saved.getId(), caller.email());
        return AssignmentResponse.of(saved);
    }

    private void requireCaller(CallerIdentity caller) {
        if (caller == null || !StringUtils.hasText(caller.email())) {
            throw new MissingCallerIdentityException("caller identity (X-User-Email) missing");
        }
    }
}
