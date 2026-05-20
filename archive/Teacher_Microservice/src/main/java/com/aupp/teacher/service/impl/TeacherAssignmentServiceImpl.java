package com.aupp.teacher.service.impl;

import com.aupp.teacher.domain.TeacherAssignment;
import com.aupp.teacher.dto.AssignmentResponse;
import com.aupp.teacher.dto.CreateAssignmentRequest;
import com.aupp.teacher.exception.AssignmentNotFoundException;
import com.aupp.teacher.exception.MissingCallerIdentityException;
import com.aupp.teacher.repository.TeacherAssignmentRepository;
import com.aupp.teacher.service.TeacherAssignmentService;
import com.aupp.teacher.web.CallerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TeacherAssignmentServiceImpl.class);
    private static final Sort BY_CREATED_DESC = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TeacherAssignmentRepository repo;

    public TeacherAssignmentServiceImpl(TeacherAssignmentRepository repo) {
        this.repo = repo;
    }

    @Override
    public AssignmentResponse create(CallerIdentity caller, CreateAssignmentRequest req) {
        requireCaller(caller);
        TeacherAssignment saved = repo.save(TeacherAssignment.builder()
                .teacherEmail(caller.email())
                .title(req.title())
                .description(req.description() == null ? "" : req.description())
                .dueDate(req.dueDate())
                .build());
        log.info("teacher {} created assignment id={}", caller.email(), saved.getId());
        return AssignmentResponse.of(saved);
    }

    @Override
    public List<AssignmentResponse> search(CallerIdentity caller, String titleQuery) {
        requireCaller(caller);
        List<TeacherAssignment> hits;
        if (StringUtils.hasText(titleQuery)) {
            hits = repo.findByTeacherEmailAndTitleRegex(caller.email(), Pattern.quote(titleQuery), BY_CREATED_DESC);
        } else {
            hits = repo.findByTeacherEmail(caller.email(), BY_CREATED_DESC);
        }
        return hits.stream().map(AssignmentResponse::of).toList();
    }

    @Override
    public void remove(CallerIdentity caller, String id) {
        requireCaller(caller);
        TeacherAssignment found = repo.findByIdAndTeacherEmail(id, caller.email())
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment " + id + " not found for caller"));
        repo.delete(found);
        log.info("teacher {} removed assignment id={}", caller.email(), id);
    }

    private static void requireCaller(CallerIdentity caller) {
        if (caller == null || !StringUtils.hasText(caller.email())) {
            throw new MissingCallerIdentityException("caller identity (X-User-Email) missing");
        }
    }
}
