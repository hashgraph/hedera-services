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
    void nametranslatesResourceLimitReversions() {
        final var statuses = List.of(
                MAX_CHILD_RECORDS_EXCEEDED,
                MAX_CONTRACT_STORAGE_EXCEEDED,
                MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED,
                INSUFFICIENT_BALANCES_FOR_STORAGE_RENT);
        for (final var status : statuses) {
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