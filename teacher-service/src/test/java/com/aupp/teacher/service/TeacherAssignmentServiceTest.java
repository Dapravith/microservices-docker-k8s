package com.aupp.teacher.service;

import com.aupp.teacher.domain.TeacherAssignment;
import com.aupp.teacher.dto.AssignmentListResponse;
import com.aupp.teacher.dto.AssignmentResponse;
import com.aupp.teacher.dto.CreateAssignmentRequest;
import com.aupp.teacher.exception.AssignmentNotFoundException;
import com.aupp.teacher.exception.MissingCallerIdentityException;
import com.aupp.teacher.repository.TeacherAssignmentRepository;
import com.aupp.teacher.service.impl.TeacherAssignmentServiceImpl;
import com.aupp.teacher.web.CallerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherAssignmentServiceTest {

    @Mock TeacherAssignmentRepository repo;
    @InjectMocks TeacherAssignmentServiceImpl service;

    private static final CallerIdentity SMITH = new CallerIdentity("ms.smith@x.y", "teacher");

    @Test
    void createPersistsWithCallerEmail() {
        when(repo.save(any(TeacherAssignment.class))).thenAnswer(inv -> {
            TeacherAssignment a = inv.getArgument(0);
            a.setId("id-1");
            return a;
        });
        Instant due = Instant.parse("2026-12-31T23:59:00Z");

        AssignmentResponse r = service.create(SMITH, new CreateAssignmentRequest("Algebra", "Ch1", due));

        ArgumentCaptor<TeacherAssignment> captor = ArgumentCaptor.forClass(TeacherAssignment.class);
        verify(repo).save(captor.capture());
        TeacherAssignment saved = captor.getValue();
        assertThat(saved.getTeacherEmail()).isEqualTo("ms.smith@x.y");
        assertThat(saved.getTitle()).isEqualTo("Algebra");
        assertThat(saved.getDescription()).isEqualTo("Ch1");
        assertThat(saved.getDueDate()).isEqualTo(due);
        assertThat(r.id()).isEqualTo("id-1");
    }

    @Test
    void createNullDescriptionStoredAsEmptyString() {
        when(repo.save(any(TeacherAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<TeacherAssignment> captor = ArgumentCaptor.forClass(TeacherAssignment.class);

        service.create(SMITH, new CreateAssignmentRequest("X", null, null));

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEmpty();
    }

    @Test
    void createWithMissingIdentityThrows() {
        assertThatThrownBy(() -> service.create(new CallerIdentity(null, "teacher"),
                new CreateAssignmentRequest("X", null, null)))
                .isInstanceOf(MissingCallerIdentityException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void searchWithoutTitleQueryUsesByEmailOnly() {
        TeacherAssignment a = TeacherAssignment.builder().id("1").teacherEmail("ms.smith@x.y").title("X").build();
        when(repo.findByTeacherEmail(eq("ms.smith@x.y"), any(Sort.class))).thenReturn(List.of(a));

        AssignmentListResponse resp = service.search(SMITH, null);

        assertThat(resp.count()).isEqualTo(1);
        verify(repo, never()).findByTeacherEmailAndTitleRegex(anyString(), anyString(), any());
    }

    @Test
    void searchWithBlankTitleQueryUsesByEmailOnly() {
        when(repo.findByTeacherEmail(anyString(), any(Sort.class))).thenReturn(List.of());

        AssignmentListResponse resp = service.search(SMITH, "   ");

        assertThat(resp.count()).isZero();
        verify(repo, never()).findByTeacherEmailAndTitleRegex(anyString(), anyString(), any());
    }

    @Test
    void searchWithTitleQueryUsesRegex() {
        TeacherAssignment a = TeacherAssignment.builder().id("1").teacherEmail("ms.smith@x.y").title("Algebra exam").build();
        when(repo.findByTeacherEmailAndTitleRegex(eq("ms.smith@x.y"), anyString(), any(Sort.class)))
                .thenReturn(List.of(a));

        AssignmentListResponse resp = service.search(SMITH, "Algebra");

        assertThat(resp.count()).isEqualTo(1);
        verify(repo).findByTeacherEmailAndTitleRegex(eq("ms.smith@x.y"), anyString(), any(Sort.class));
        verify(repo, never()).findByTeacherEmail(anyString(), any(Sort.class));
    }

    @Test
    void searchWithMissingIdentityThrows() {
        assertThatThrownBy(() -> service.search(new CallerIdentity("", "teacher"), null))
                .isInstanceOf(MissingCallerIdentityException.class);
    }

    @Test
    void removeDeletesOwnedAssignment() {
        TeacherAssignment a = TeacherAssignment.builder().id("1").teacherEmail("ms.smith@x.y").build();
        when(repo.findByIdAndTeacherEmail("1", "ms.smith@x.y")).thenReturn(Optional.of(a));

        service.remove(SMITH, "1");

        verify(repo).delete(a);
    }

    @Test
    void removeForeignAssignmentThrows() {
        when(repo.findByIdAndTeacherEmail(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(SMITH, "999"))
                .isInstanceOf(AssignmentNotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void removeWithMissingIdentityThrows() {
        assertThatThrownBy(() -> service.remove(null, "1"))
                .isInstanceOf(MissingCallerIdentityException.class);
    }
}
