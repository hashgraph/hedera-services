package com.hedera.node.app.service.contract.impl.test.hevm;

import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import org.junit.jupiter.api.Test;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.GAS_LIMIT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.VALUE;
import static org.junit.jupiter.api.Assertions.*;

class HederaEvmTransactionTest {
    @Test
    void computesUpfrontCostWithoutOverflowConcern() {
        final var subject = TestHelpers.wellKnownHapiCall();
        assertEquals(VALUE + 123L * GAS_LIMIT, subject.upfrontCostGiven(123L));
    }

    @Test
    void computesUpfrontCostWithOverflow() {
        final var subject = TestHelpers.wellKnownHapiCall();
        assertEquals(Long.MAX_VALUE,
                subject.upfrontCostGiven(Long.MAX_VALUE / (GAS_LIMIT - 1)));
    }
}