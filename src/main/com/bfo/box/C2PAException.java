package com.bfo.box;

/**
 * A C2PA exception, wrapping a {@link C2PAStatus} error code and message
 */
public class C2PAException extends Exception {

    private final C2PAStatus status;

    /**
     * Create a new C2PAException
     * @param status the status
     * @param message the message
     */
    public C2PAException(C2PAStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Create a new C2PAException
     * @param status the status
     * @param message the message
     * @param cause the cause
     */
    public C2PAException(C2PAStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Get the status
     * @return the status
     */
    public C2PAStatus getStatus() {
        return status;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append(":");
        if (status != null) {
            sb.append(" [");
            sb.append(status.toString());
            sb.append("]");
        }
        if (getMessage() != null) {
            sb.append(" ");
            sb.append(getMessage());
        }
        return sb.toString();
    }
}
