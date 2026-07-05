package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails via Spring Boot's auto-configured JavaMailSender.
 * Runs asynchronously so email delivery never blocks the HTTP response.
 * Email failures are logged but never thrown — they must not break user flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    private static final String APP_NAME = "SCITRACK";

    // ──────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────

    @Override
    @Async
    public void sendVerificationEmail(String to, String userName, String verificationLink) {
        String subject = "Verify your email — " + APP_NAME;
        String body = buildVerificationEmailBody(userName, verificationLink);
        send(to, subject, body);
        // Also log to terminal so devs without SMTP can still test
        log.info("============================================");
        log.info("VERIFICATION EMAIL sent to {}", to);
        log.info("   Link: {}", verificationLink);
        log.info("============================================");
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String userName, String resetLink) {
        String subject = "Reset your password — " + APP_NAME;
        String body = buildPasswordResetEmailBody(userName, resetLink);
        send(to, subject, body);
        // Also log to terminal so devs without SMTP can still test
        log.info("============================================");
        log.info("PASSWORD RESET EMAIL sent to {}", to);
        log.info("   Link: {}", resetLink);
        log.info("============================================");
    }

    @Override
    @Async
    public void sendSimpleEmail(String to, String subject, String body) {
        send(to, subject, body);
    }

    // ──────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML
            mailSender.send(message);
            log.debug("Email sent successfully to {}: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {} (subject: {}): {}", to, subject, e.getMessage(), e);
            // Do NOT rethrow — email failure must not break the calling flow
        }
    }

    // ──────────────────────────────────────────────
    //  HTML templates (placeholder-based to avoid text-block concatenation issues)
    // ──────────────────────────────────────────────

    private static final String VERIFICATION_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; background: #f4f4f9; padding: 40px 0; margin: 0;">
              <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px;
                          box-shadow: 0 2px 12px rgba(0,0,0,0.08); overflow: hidden;">
                <div style="background: linear-gradient(135deg, #4F46E5, #7C3AED); padding: 32px 24px; text-align: center;">
                  <h1 style="color: #ffffff; margin: 0; font-size: 24px;">{{APP_NAME}}</h1>
                </div>
                <div style="padding: 32px 24px;">
                  <h2 style="color: #1e293b; margin: 0 0 12px; font-size: 20px;">Verify your email address</h2>
                  <p style="color: #475569; font-size: 15px; line-height: 1.6; margin: 0 0 24px;">
                    Hi <strong>{{USER_NAME}}</strong>,<br><br>
                    Thanks for signing up! Please verify your email address by clicking the button below.
                    This link expires in 24 hours.
                  </p>
                  <div style="text-align: center; margin-bottom: 24px;">
                    <a href="{{LINK}}"
                       style="display: inline-block; background: #4F46E5; color: #ffffff; text-decoration: none;
                              padding: 12px 32px; border-radius: 8px; font-size: 15px; font-weight: 600;">
                      Verify Email
                    </a>
                  </div>
                  <p style="color: #94a3b8; font-size: 13px; margin: 0;">
                    If the button doesn't work, copy and paste this link into your browser:<br>
                    <a href="{{LINK}}" style="color: #4F46E5; word-break: break-all;">{{LINK}}</a>
                  </p>
                </div>
                <div style="background: #f8fafc; padding: 16px 24px; text-align: center;
                            border-top: 1px solid #e2e8f0;">
                  <p style="color: #94a3b8; font-size: 12px; margin: 0;">
                    If you didn't create an account, you can safely ignore this email.
                  </p>
                </div>
              </div>
            </body>
            </html>
            """;

    private static final String PASSWORD_RESET_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; background: #f4f4f9; padding: 40px 0; margin: 0;">
              <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px;
                          box-shadow: 0 2px 12px rgba(0,0,0,0.08); overflow: hidden;">
                <div style="background: linear-gradient(135deg, #4F46E5, #7C3AED); padding: 32px 24px; text-align: center;">
                  <h1 style="color: #ffffff; margin: 0; font-size: 24px;">{{APP_NAME}}</h1>
                </div>
                <div style="padding: 32px 24px;">
                  <h2 style="color: #1e293b; margin: 0 0 12px; font-size: 20px;">Reset your password</h2>
                  <p style="color: #475569; font-size: 15px; line-height: 1.6; margin: 0 0 24px;">
                    Hi <strong>{{USER_NAME}}</strong>,<br><br>
                    We received a request to reset your password. Click the button below to choose a new one.
                    This link expires in 15 minutes.
                  </p>
                  <div style="text-align: center; margin-bottom: 24px;">
                    <a href="{{LINK}}"
                       style="display: inline-block; background: #4F46E5; color: #ffffff; text-decoration: none;
                              padding: 12px 32px; border-radius: 8px; font-size: 15px; font-weight: 600;">
                      Reset Password
                    </a>
                  </div>
                  <p style="color: #94a3b8; font-size: 13px; margin: 0;">
                    If the button doesn't work, copy and paste this link into your browser:<br>
                    <a href="{{LINK}}" style="color: #4F46E5; word-break: break-all;">{{LINK}}</a>
                  </p>
                </div>
                <div style="background: #f8fafc; padding: 16px 24px; text-align: center;
                            border-top: 1px solid #e2e8f0;">
                  <p style="color: #94a3b8; font-size: 12px; margin: 0;">
                    If you didn't request a password reset, you can safely ignore this email.
                  </p>
                </div>
              </div>
            </body>
            </html>
            """;

    private String buildVerificationEmailBody(String userName, String verificationLink) {
        String displayName = userName != null && !userName.isBlank() ? escapeHtml(userName) : "there";
        return VERIFICATION_TEMPLATE
                .replace("{{APP_NAME}}", APP_NAME)
                .replace("{{USER_NAME}}", displayName)
                .replace("{{LINK}}", verificationLink);
    }

    private String buildPasswordResetEmailBody(String userName, String resetLink) {
        String displayName = userName != null && !userName.isBlank() ? escapeHtml(userName) : "there";
        return PASSWORD_RESET_TEMPLATE
                .replace("{{APP_NAME}}", APP_NAME)
                .replace("{{USER_NAME}}", displayName)
                .replace("{{LINK}}", resetLink);
    }

    /**
     * Minimal HTML-escaping to prevent injection in email templates.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
