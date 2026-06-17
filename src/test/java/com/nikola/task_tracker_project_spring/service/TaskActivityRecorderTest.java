package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.config.AuthFacade;
import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.entity.TaskActivityAction;
import com.nikola.task_tracker_project_spring.entity.TaskPriority;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.repository.TaskActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskActivityRecorderTest {

    @Mock
    private TaskActivityRepository activityRepository;

    @Mock
    private AuthFacade authFacade;

    @InjectMocks
    private TaskActivityRecorder recorder;

    private Task task(Long id, String title, TaskStatus status, TaskPriority priority,
                      LocalDate due, String description, Long assigneeId) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(due);
        t.setDescription(description);
        if (assigneeId != null) {
            User u = new User();
            u.setId(assigneeId);
            t.setAssignee(u);
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private List<TaskActivity> captureSaveAll() {
        ArgumentCaptor<List<TaskActivity>> captor = ArgumentCaptor.forClass(List.class);
        verify(activityRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private TaskActivity captureSave() {
        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);
        verify(activityRepository).save(captor.capture());
        return captor.getValue();
    }

    private TaskActivity rowFor(List<TaskActivity> rows, String field) {
        return rows.stream().filter(r -> field.equals(r.getFieldChanged())).findFirst().orElseThrow();
    }

    @Test
    void recordUpdate_writesOneRowPerChangedFieldOnly() {
        when(authFacade.username()).thenReturn("alice");
        when(authFacade.currentUserId()).thenReturn(5L);
        Task before = task(1L, "Old", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);
        Task after  = task(1L, "New", TaskStatus.IN_PROGRESS, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);

        recorder.recordUpdate(before, after);

        // Only title and status changed; priority/dueDate/description/assignee are unchanged.
        assertThat(captureSaveAll()).extracting(TaskActivity::getFieldChanged)
                .containsExactlyInAnyOrder("title", "status");
    }

    @Test
    void recordUpdate_noChanges_savesNoRows() {
        Task before = task(1L, "Same", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);
        Task after  = task(1L, "Same", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);

        recorder.recordUpdate(before, after);

        assertThat(captureSaveAll()).isEmpty();
    }

    @Test
    void recordUpdate_stringifiesEnumsDatesAndAssigneeId() {
        when(authFacade.username()).thenReturn("alice");
        when(authFacade.currentUserId()).thenReturn(5L);
        Task before = task(1L, "T", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);
        Task after  = task(1L, "T", TaskStatus.COMPLETED, TaskPriority.HIGH, LocalDate.of(2026, 2, 2), "d", 7L);

        recorder.recordUpdate(before, after);
        List<TaskActivity> rows = captureSaveAll();

        assertThat(rowFor(rows, "status").getOldValue()).isEqualTo("TODO");
        assertThat(rowFor(rows, "status").getNewValue()).isEqualTo("COMPLETED");
        assertThat(rowFor(rows, "dueDate").getOldValue()).isEqualTo("2026-01-01");
        assertThat(rowFor(rows, "dueDate").getNewValue()).isEqualTo("2026-02-02");
        assertThat(rowFor(rows, "assignee").getOldValue()).isEqualTo("5");
        assertThat(rowFor(rows, "assignee").getNewValue()).isEqualTo("7");
    }

    @Test
    void recordUpdate_sharesOneChangeSetIdAndCapturesActor() {
        when(authFacade.username()).thenReturn("alice");
        when(authFacade.currentUserId()).thenReturn(5L);
        Task before = task(1L, "Old", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);
        Task after  = task(1L, "New", TaskStatus.COMPLETED, TaskPriority.LOW, LocalDate.of(2026, 1, 1), "d", 5L);

        recorder.recordUpdate(before, after);
        List<TaskActivity> rows = captureSaveAll();

        assertThat(rows).hasSize(2);
        // The two field-rows from one edit share a single change_set_id.
        assertThat(rows).extracting(TaskActivity::getChangeSetId)
                .containsOnly(rows.get(0).getChangeSetId());
        assertThat(rows.get(0).getChangeSetId()).isNotNull();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getAction()).isEqualTo(TaskActivityAction.UPDATE);
            assertThat(r.getTaskId()).isEqualTo(1L);
            assertThat(r.getUserId()).isEqualTo(5L);
            assertThat(r.getUsername()).isEqualTo("alice");
        });
    }

    @Test
    void recordCreate_writesSingleCreateRow_withNullFieldAndValues() {
        when(authFacade.username()).thenReturn("alice");
        when(authFacade.currentUserId()).thenReturn(5L);
        Task t = task(1L, "Created", TaskStatus.TODO, TaskPriority.LOW, null, "d", null);

        recorder.recordCreate(t);
        TaskActivity row = captureSave();

        assertThat(row.getAction()).isEqualTo(TaskActivityAction.CREATE);
        assertThat(row.getTaskId()).isEqualTo(1L);
        assertThat(row.getTaskTitle()).isEqualTo("Created");
        assertThat(row.getFieldChanged()).isNull();
        assertThat(row.getOldValue()).isNull();
        assertThat(row.getNewValue()).isNull();
        assertThat(row.getUsername()).isEqualTo("alice");
        assertThat(row.getChangeSetId()).isNotNull();
    }

    @Test
    void recordDelete_writesDeleteRow_andHandlesAdminActorWithNullId() {
        // In-memory admin: currentUserId() is null but username() still records "admin".
        when(authFacade.username()).thenReturn("admin");
        when(authFacade.currentUserId()).thenReturn(null);
        Task t = task(9L, "Doomed", TaskStatus.TODO, TaskPriority.LOW, null, "d", null);

        recorder.recordDelete(t);
        TaskActivity row = captureSave();

        assertThat(row.getAction()).isEqualTo(TaskActivityAction.DELETE);
        assertThat(row.getTaskId()).isEqualTo(9L);
        assertThat(row.getUserId()).isNull();
        assertThat(row.getUsername()).isEqualTo("admin");
    }
}
