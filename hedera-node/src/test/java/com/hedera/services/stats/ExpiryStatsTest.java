package com.hedera.services.stats;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class ExpiryStatsTest {
    private static final double halfLife = 10.0;

    @Mock private Platform platform;
    @Mock
    private RunningAverageMetric idsScannedPerConsSec;
    @Mock private Counter contractsRemoved;
    @Mock private Counter contractsRenewed;

    private ExpiryStats subject;

    @BeforeEach
    void setup() {
        subject = new ExpiryStats(halfLife);
    }

    @Test
    void registersExpectedStatEntries() {
        setMocks();

        subject.registerWith(platform);

        verify(platform, times(3)).getOrCreateMetric(any());
    }

    @Test
    void recordsToExpectedMetrics() {
        setMocks();

        subject.countRemovedContract();
        subject.countRenewedContract();
        subject.incorporateLastConsSec(5);

        verify(contractsRemoved).increment();
        verify(contractsRenewed).increment();
        verify(idsScannedPerConsSec).update(5.0);
    }

    private void setMocks() {
        subject.setIdsScannedPerConsSec(idsScannedPerConsSec);
        subject.setContractsRemoved(contractsRemoved);
        subject.setContractsRenewed(contractsRenewed);
    }
}