package com.aplicafacil.profile.domain;

public enum AiTaskStatus {
    PENDING,
    COMPLETED,
    FAILED,
    /** El mcp-gateway no respondió antes del plazo; un resultado tardío se ignora. */
    TIMED_OUT
}
