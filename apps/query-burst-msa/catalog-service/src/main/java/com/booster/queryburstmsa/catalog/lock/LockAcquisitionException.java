package com.booster.queryburstmsa.catalog.lock;

public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String key) {
        super("Failed to acquire distributed lock. key=" + key);
    }
}
