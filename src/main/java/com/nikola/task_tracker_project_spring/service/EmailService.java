package com.nikola.task_tracker_project_spring.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link JavaMailSender}. Keeps mail-construction details out of
 * the business services and gives the rest of the app a small, mockable surface.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends a plain-text email. Throws {@link MailException} on failure; callers that
     * treat email as fire-and-forget should invoke this from an async context and log
     * (rather than propagate) any failure.
     */
    public void sendSimpleMessage(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
