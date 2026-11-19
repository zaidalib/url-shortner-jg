package com.jgu.urlshortener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Service layer for URL shortening operations.
 * Handles validation, code generation, and database interactions.
 */
@Service
public class UrlService {

    // Base62 alphabet for generating short codes
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();
    
    // Reserved words that cannot be used as custom aliases
    private static final Set<String> RESERVED_WORDS = Set.of(
            "stats", "qr", "shorten", "result", "error", "static", "actuator", "h2-console"
    );

    private final UrlRepository repo;

    public UrlService(UrlRepository repo) {
        this.repo = repo;
    }

    /**
     * Create a new short link.
     * @param originalUrl The long URL to shorten
     * @param alias Optional custom alias (null for auto-generated)
     * @param expiryDays Optional expiry in days (null or 0 for no expiry)
     * @return The saved UrlMapping entity
     */
    @Transactional
    public UrlMapping shorten(String originalUrl, String alias, Integer expiryDays) {
        // Validate URL is not empty
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new LinkException("URL must not be empty.");
        }
        
        // Ensure URL starts with http:// or https://
        originalUrl = originalUrl.trim();
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "https://" + originalUrl;
        }
        
        // Validate URL length
        if (originalUrl.length() > 2048) {
            throw new LinkException("URL is too long. Maximum 2048 characters allowed.");
        }
        
        // Block self-referencing URLs (prevents redirect loops)
        if (isSelfReferencingUrl(originalUrl)) {
            throw new LinkException("Cannot shorten a link to this application.");
        }

        String code;
        if (alias != null && !alias.isBlank()) {
            code = alias.trim().toLowerCase();
            
            // Validate alias format (3-20 chars, alphanumeric, hyphen, underscore)
            if (!code.matches("[a-zA-Z0-9_-]{3,20}")) {
                throw new LinkException("Alias must be 3-20 characters: letters, digits, hyphens, or underscores.");
            }
            
            // Block reserved words
            if (RESERVED_WORDS.contains(code)) {
                throw new LinkException("That alias is reserved. Please choose another.");
            }
            
            // Check for duplicates
            if (repo.existsByCode(code)) {
                throw new LinkException("That alias is already taken. Please choose another.");
            }
        } else {
            code = generateUniqueCode();
        }

        LocalDateTime expiresAt = (expiryDays != null && expiryDays > 0)
                ? LocalDateTime.now().plusDays(expiryDays)
                : null;

        UrlMapping mapping = new UrlMapping(code, originalUrl, LocalDateTime.now(), expiresAt, 0, null);
        return repo.save(mapping);
    }

    /**
     * Resolve a short code to its original URL and record the click.
     * @param code The short code
     * @return The original URL for redirect
     * @throws LinkException if not found or expired
     */
    @Transactional
    public String resolve(String code) {
        UrlMapping mapping = repo.findByCode(code)
                .orElseThrow(() -> new LinkException("Short link not found: " + code, 404));

        if (mapping.getExpiresAt() != null && LocalDateTime.now().isAfter(mapping.getExpiresAt())) {
            throw new LinkException("This short link has expired.", 410);
        }

        // Increment click count and update last clicked time
        mapping.setClickCount(mapping.getClickCount() + 1);
        mapping.setLastClickedAt(LocalDateTime.now());
        repo.save(mapping);

        return mapping.getOriginalUrl();
    }

    /**
     * Fetch a mapping by code without recording a click.
     * Used for result/stats pages.
     */
    public UrlMapping findByCode(String code) {
        return repo.findByCode(code)
                .orElseThrow(() -> new LinkException("Short link not found: " + code, 404));
    }

    /** Return the 10 most recently created links. */
    public List<UrlMapping> recentLinks() {
        return repo.findTop10ByOrderByCreatedAtDesc();
    }

    // --- Private helper methods ---

    /**
     * Generate a unique random code with collision check.
     */
    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = randomCode();
            attempts++;
            if (attempts > 10) {
                throw new LinkException("Unable to generate unique code. Please try again.", 503);
            }
        } while (repo.existsByCode(code));
        return code;
    }

    /**
     * Generate a random 6-character base62 code using SecureRandom.
     */
    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
    
    /**
     * Check if the URL points to this application itself.
     * Blocks localhost, 127.0.0.1, and the app's own domain.
     */
    private boolean isSelfReferencingUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("localhost") 
            || lower.contains("127.0.0.1")
            || lower.contains("linksnip")
            || lower.matches(".*://[^/]*:" + "\\d+" + ".*"); // matches any URL with a port number on localhost
    }
}
