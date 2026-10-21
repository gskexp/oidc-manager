package com.example.b2b;

/**
 * Checked exception carrying an actionable, credential-free message suitable for printing
 * directly to the user.
 */
public class B2bClientException extends Exception {

    private static final long serialVersionUID = 1L;

    public B2bClientException(String message) {
        super(message);
    }

    public B2bClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
