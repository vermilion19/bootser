package com.booster.queryburstmsa.catalog.lock;

import java.time.Duration;

public interface DistributedLock {

    FencingToken tryLock(String key, Duration ttl);

    void unlock(String key, FencingToken token);
}
