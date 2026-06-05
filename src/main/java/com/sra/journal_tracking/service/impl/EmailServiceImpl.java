package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Password Reset Request");

            String htmlContent = """
                    <html>
                    <body style="font-family: Arial, sans-serif; padding: 20px;">
                        <h2>Password Reset Request</h2>
                        <p>You have requested to reset your password. Click the link below to set a new password:</p>
                        <p>
                            <a href="%s" style="display: inline-block; padding: 10px 20px;
                               background-color: #007bff; color: white; text-decoration: none;
                               border-radius: 5px;">Reset Password</a>
                        </p>
                        <p>Or copy and paste this link into your browser:</p>
                        <p>%s</p>
                        <p><strong>Note:</strong> This link will expire in 15 minutes.</p>
                        <p>If you did not request a password reset, please ignore this email.</p>
                    </body>
                    </html>
                    """.formatted(resetLink, resetLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}