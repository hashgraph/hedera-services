package com.hedera.services.api.implementation.workflows.ingest;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Thrown if a throttle is exceeded.
 */
public class ThrottleException extends PreCheckException {
    public ThrottleException(String message) {
        // TODO Not sure.
        super(ResponseCodeEnum.BUSY, message);
    }
}
