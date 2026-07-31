package com.sra.journal_tracking.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int CAPTCHA_EXPIRY_MINUTES = 5;

    // In-memory stores (reset on restart)
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    /**
     * Record a failed login attempt for an identifier (email).
     * Returns true if CAPTCHA is now required.
     */
    public boolean recordFailedAttempt(String identifier) {
        String key = identifier.toLowerCase().trim();
        int attempts = failedAttempts.merge(key, 1, Integer::sum);
        log.info("Failed login attempt #{} for {}", attempts, key);
        return attempts >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * Reset failed attempts on successful login.
     */
    public void resetFailedAttempts(String identifier) {
        String key = identifier.toLowerCase().trim();
        failedAttempts.remove(key);
        captchaStore.remove(key);
    }

    /**
     * Check if CAPTCHA is required for this identifier.
     */
    public boolean isCaptchaRequired(String identifier) {
        String key = identifier.toLowerCase().trim();
        return failedAttempts.getOrDefault(key, 0) >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * Generate a simple math CAPTCHA and return the question + token.
     * The token is used to verify the answer later.
     */
    public CaptchaChallenge generateCaptcha(String identifier) {
        String key = identifier.toLowerCase().trim();
        int a = RANDOM.nextInt(20) + 1;
        int b = RANDOM.nextInt(20) + 1;
        int op = RANDOM.nextInt(3); // 0: +, 1: -, 2: ×
        String question;
        int answer;
        switch (op) {
            case 0:
                question = a + " + " + b + " = ?";
                answer = a + b;
                break;
            case 1:
                if (a < b) { int tmp = a; a = b; b = tmp; }
                question = a + " - " + b + " = ?";
                answer = a - b;
                break;
            default:
                a = RANDOM.nextInt(10) + 1;
                b = RANDOM.nextInt(10) + 1;
                question = a + " × " + b + " = ?";
                answer = a * b;
                break;
        }

        String token = java.util.UUID.randomUUID().toString();
        captchaStore.put(key, new CaptchaEntry(token, answer, LocalDateTime.now().plusMinutes(CAPTCHA_EXPIRY_MINUTES)));

        log.info("CAPTCHA generated for {}: {} (answer={})", key, question, answer);
        return new CaptchaChallenge(question, token);
    }

    /**
     * Verify a CAPTCHA answer. Returns true if correct.
     */
    public boolean verifyCaptcha(String identifier, String captchaToken, int answer) {
        String key = identifier.toLowerCase().trim();
        CaptchaEntry entry = captchaStore.get(key);

        if (entry == null) {
            log.warn("CAPTCHA verify: no entry for {}", key);
            return false;
        }

        if (entry.expiresAt.isBefore(LocalDateTime.now())) {
            captchaStore.remove(key);
            log.warn("CAPTCHA verify: expired for {}", key);
            return false;
        }

        if (!entry.token.equals(captchaToken)) {
            log.warn("CAPTCHA verify: token mismatch for {}", key);
            return false;
        }

        boolean correct = entry.answer == answer;
        // Always remove the captcha after verification (one-time use)
        captchaStore.remove(key);
        if (!correct) {
            log.warn("CAPTCHA verify: wrong answer for {} (expected {}, got {})", key, entry.answer, answer);
        }
        return correct;
    }

    // Inner classes

    public record CaptchaChallenge(String question, String token) {}

    private record CaptchaEntry(String token, int answer, LocalDateTime expiresAt) {}
}
