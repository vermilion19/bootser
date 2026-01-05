package com.booster.waitingservice.waiting.exception;

import com.booster.core.web.exception.CoreException;

public class InvalidWaitingStatusException extends CoreException {
    public InvalidWaitingStatusException() {
        super(WaitingErrorCode.INVALID_ENTRY_STATUS);
    }
}
