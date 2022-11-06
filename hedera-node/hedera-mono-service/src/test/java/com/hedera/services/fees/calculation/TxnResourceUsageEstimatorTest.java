package com.hedera.services.fees.calculation;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TxnResourceUsageEstimatorTest {
    @Test
    void defaultResourceUsageEstimatorHasNoSecondaryFees() {
        final var mockSubject = mock(TxnResourceUsageEstimator.class);

        willCallRealMethod().given(mockSubject).hasSecondaryFees();
        willCallRealMethod().given(mockSubject).secondaryFeesFor(any());

        assertFalse(mockSubject.hasSecondaryFees());
        verify(mockSubject).hasSecondaryFees();
        assertThrows(UnsupportedOperationException.class, () ->
                mockSubject.secondaryFeesFor(TransactionBody.getDefaultInstance()));
    }
}