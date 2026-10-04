package com.offertracker.service;

import com.offertracker.common.BusinessException;

/** Provider failure with a stable retry classification for API and worker callers. */
public class AiProviderException extends BusinessException {
    private final boolean retryable;
    public AiProviderException(int code, String message, boolean retryable) { super(code, message); this.retryable = retryable; }
    public boolean isRetryable() { return retryable; }
}
