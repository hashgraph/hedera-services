package com.hedera.node.app.service.mono.utils;

import com.hedera.node.app.service.mono.exceptions.ResourceLimitException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public final class ResourceValidationUtils {
    private ResourceValidationUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static void validateResourceLimit(final boolean flag, final ResponseCodeEnum code) {
        if (!flag) {
            throw new ResourceLimitException(code);
        }
    }
}
