package com.hedera.services.throttles;

public interface CongestibleThrottle {
    long used();

    long capacity();

    long mtps();

    String name();
}
