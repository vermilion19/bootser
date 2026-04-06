package com.booster.queryburst.lock;

public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String key) {
        super("분산 락 획득 실패. key=" + key);
    }
}
