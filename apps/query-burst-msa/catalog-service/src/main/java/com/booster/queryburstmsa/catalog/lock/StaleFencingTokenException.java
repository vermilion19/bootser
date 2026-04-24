package com.booster.queryburstmsa.catalog.lock;

public class StaleFencingTokenException extends RuntimeException {

    public StaleFencingTokenException(Long productId, long token, long lastToken) {
        super("Stale fencing token. productId=%d, token=%d, lastToken=%d"
                .formatted(productId, token, lastToken));
    }
}
