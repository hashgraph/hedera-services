package com.hedera.services.utils;

import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.exceptions.ResourceLimitException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponseCodeUtilTest {
    @Test
    void translatesResourceLimitReversions() {
        for (final var status : ResponseCodeUtil.RESOURCE_EXHAUSTION_REVERSIONS.values()) {
            final var result = failureWithRevertReasonFrom(status);
            final var code = ResponseCodeUtil.getStatusOrDefault(result, OK);
            assertEquals(status, code);
        }
    }

    private TransactionProcessingResult failureWithRevertReasonFrom(final ResponseCodeEnum status) {
        final var ex = new ResourceLimitException(status);
        return TransactionProcessingResult.failed(
                1L, 2L, 3L, Optional.of(ex.messageBytes()), Optional.empty(), Map.of(), List.of());
    }
}