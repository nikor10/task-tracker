package com.nikola.task_tracker_project_spring.event;

import com.nikola.task_tracker_project_spring.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the assignee-notification email after the task-creation transaction commits.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT} — only notify once the task is durably persisted, so we
 *       never email about a task that later rolled back.</li>
 *   <li>{@code @Async} — runs on the notification thread pool, off the request thread,
 *       so a slow SMTP server never delays the API response.</li>
 * </ul>
 */
@Component
public class TaskAssignedListener {

    private static final Logger log = LoggerFactory.getLogger(TaskAssignedListener.class);

    private final EmailService emailService;

    public TaskAssignedListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAssigned(TaskAssignedEvent event) {
        String subject = "New task assigned: " + event.taskTitle();
        String body = """
                Hi %s,

                You have been assigned a new task on project "%s":

                  %s

                Please log in to the task tracker to view the details.
                """.formatted(event.assigneeUsername(), event.projectName(), event.taskTitle());

        try {
            emailService.sendSimpleMessage(event.assigneeEmail(), subject, body);
            log.info("Sent task-assignment email to {} for task {}", event.assigneeEmail(), event.taskId());
        } catch (MailException ex) {
            // Fire-and-forget: a failed notification must not affect the created task.
            log.error("Failed to send task-assignment email to {} for task {}",
                    event.assigneeEmail(), event.taskId(), ex);
        }
    }
}
