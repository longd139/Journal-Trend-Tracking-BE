package com.sra.journal_tracking.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}