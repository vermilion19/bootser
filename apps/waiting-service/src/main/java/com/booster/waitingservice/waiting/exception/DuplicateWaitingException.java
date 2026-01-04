package com.booster.waitingservice.waiting.exception;

import com.booster.core.web.exception.CoreException;

public class DuplicateWaitingException extends CoreException {

    public DuplicateWaitingException() {
        super(WaitingErrorCode.ALREADY_WAITING);
    }
}
