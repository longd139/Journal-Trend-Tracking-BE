package com.sra.journal_tracking.service;

/**
 * Service for sending transactional emails (verification, password reset, etc.)
 * via Spring Boot's auto-configured JavaMailSender.
 */
public interface EmailService {

    /**
     * Send an email verification link to a newly registered user.
     *
     * @param to               recipient email address
     * @param userName         recipient's display name
     * @param verificationLink the full verification URL with token
     */
    void sendVerificationEmail(String to, String userName, String verificationLink);

    /**
     * Send a password reset link to a user who requested it.
     *
     * @param to        recipient email address
     * @param userName  recipient's display name
     * @param resetLink the full password-reset URL with token
     */
    void sendPasswordResetEmail(String to, String userName, String resetLink);

    /**
     * Send a plain or simple-styled email with a subject and body.
     * Used for general notifications / future extensibility.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param body    email body (can be plain text or minimal HTML)
     */
    void sendSimpleEmail(String to, String subject, String body);
}
