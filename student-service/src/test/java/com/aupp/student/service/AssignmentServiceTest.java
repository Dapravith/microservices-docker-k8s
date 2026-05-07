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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock AssignmentRepository repo;
    @InjectMocks AssignmentService service;

    private static final CallerIdentity ALICE = new CallerIdentity("alice@x.y", "student");

    @Test
    void submitPersistsWithCallerEmailAndStatus() {
        when(repo.save(any(Assignment.class))).thenAnswer(inv -> {
            Assignment a = inv.getArgument(0);
            a.setId("id-1");
            return a;
        });

        AssignmentResponse resp = service.submit(ALICE, new SubmitAssignmentRequest("HW1", "x"));

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(repo).save(captor.capture());
        Assignment saved = captor.getValue();
        assertThat(saved.getStudentEmail()).isEqualTo("alice@x.y");
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");
        assertThat(saved.getTitle()).isEqualTo("HW1");
        assertThat(resp.id()).isEqualTo("id-1");
    }

    @Test
    void submitWithoutEmailRaises() {
        assertThatThrownBy(() -> service.submit(new CallerIdentity(null, "student"),
                new SubmitAssignmentRequest("x", "y")))
                .isInstanceOf(MissingCallerIdentityException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void submitWithBlankEmailRaises() {
        assertThatThrownBy(() -> service.submit(new CallerIdentity("  ", "student"),
                new SubmitAssignmentRequest("x", "y")))
                .isInstanceOf(MissingCallerIdentityException.class);
    }

    @Test
    void submitWithNullCallerRaises() {
        assertThatThrownBy(() -> service.submit(null, new SubmitAssignmentRequest("x", "y")))
                .isInstanceOf(MissingCallerIdentityException.class);
    }

    @Test
    void listMineReturnsOnlyAssignmentsOfCaller() {
        Assignment a1 = Assignment.builder().id("1").studentEmail("alice@x.y").title("HW1").build();
        Assignment a2 = Assignment.builder().id("2").studentEmail("alice@x.y").title("HW2").build();
        when(repo.findByStudentEmail("alice@x.y", Sort.by(Sort.Direction.DESC, "createdAt")))
                .thenReturn(List.of(a1, a2));

        AssignmentListResponse resp = service.listMine(ALICE);

        assertThat(resp.count()).isEqualTo(2);
        assertThat(resp.assignments()).extracting(AssignmentResponse::id).containsExactly("1", "2");
    }

    @Test
    void listMineWithoutCallerRaises() {
        assertThatThrownBy(() -> service.listMine(new CallerIdentity("", "student")))
                .isInstanceOf(MissingCallerIdentityException.class);
    }

    @Test
    void updateLatestAppliesPartialPatch() {
        Assignment latest = Assignment.builder().id("1").studentEmail("alice@x.y")
                .title("HW1").content("v1").status("SUBMITTED").build();
        when(repo.findFirstByStudentEmailOrderByCreatedAtDesc("alice@x.y")).thenReturn(Optional.of(latest));
        when(repo.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse resp = service.updateLatest(ALICE, new UpdateAssignmentRequest("HW1 (rev)", null));

        assertThat(resp.title()).isEqualTo("HW1 (rev)");
        assertThat(resp.content()).isEqualTo("v1");
    }

    @Test
    void updateLatestUpdatesContentEvenWhenEmpty() {
        Assignment latest = Assignment.builder().id("1").studentEmail("alice@x.y").content("v1").build();
        when(repo.findFirstByStudentEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.of(latest));
        when(repo.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse resp = service.updateLatest(ALICE, new UpdateAssignmentRequest(null, ""));

        assertThat(resp.content()).isEmpty();
    }

    @Test
    void updateLatestThrowsWhenNoAssignmentExists() {
        when(repo.findFirstByStudentEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLatest(ALICE, new UpdateAssignmentRequest("x", "y")))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void resubmitLatestSetsStatusAndSaves() {
        Assignment latest = Assignment.builder().id("1").studentEmail("alice@x.y")
                .title("HW1").content("v1").status("SUBMITTED").build();
        when(repo.findFirstByStudentEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.of(latest));
        when(repo.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse resp = service.resubmitLatest(ALICE, new UpdateAssignmentRequest("HW1 (rev2)", "v3"));

        assertThat(resp.status()).isEqualTo("RESUBMITTED");
        assertThat(resp.title()).isEqualTo("HW1 (rev2)");
        assertThat(resp.content()).isEqualTo("v3");
    }

    @Test
    void resubmitLatestThrowsWhenAbsent() {
        when(repo.findFirstByStudentEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resubmitLatest(ALICE, new UpdateAssignmentRequest("a", "b")))
                .isInstanceOf(AssignmentNotFoundException.class);
    }
}
