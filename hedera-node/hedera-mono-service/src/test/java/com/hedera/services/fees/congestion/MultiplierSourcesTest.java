package com.hedera.services.fees.congestion;

import com.hedera.services.utils.accessors.TxnAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MultiplierSourcesTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567);
    private static final Instant[] SOME_TIMES = new Instant[] {NOW, NOW};
    private static final Instant[] SOME_MORE_TIMES = new Instant[] {NOW, NOW, NOW, NOW};

    @Mock
    private FeeMultiplierSource gasFeeMultiplier;
    @Mock
    private FeeMultiplierSource genericFeeMultiplier;
    @Mock
    private TxnAccessor accessor;

    private MultiplierSources subject;

    @BeforeEach
    void setUp() {
        subject = new MultiplierSources(genericFeeMultiplier, gasFeeMultiplier);
    }

    @Test
    void delegatesUpdate() {
        subject.updateMultiplier(accessor, NOW);
        verify(gasFeeMultiplier).updateMultiplier(accessor, NOW);
        verify(genericFeeMultiplier).updateMultiplier(accessor, NOW);
    }

    @Test
    void delegatesExpectationsReset() {
        subject.resetExpectations();
        verify(gasFeeMultiplier).resetExpectations();
        verify(genericFeeMultiplier).resetExpectations();
    }

    @Test
    void delegatesReset() {
        subject.resetCongestionLevelStarts(SOME_TIMES, SOME_MORE_TIMES);
        verify(gasFeeMultiplier).resetCongestionLevelStarts(SOME_TIMES);
        verify(genericFeeMultiplier).resetCongestionLevelStarts(SOME_MORE_TIMES);
    }

    @Test
    void delegatesStarts() {
        given(gasFeeMultiplier.congestionLevelStarts()).willReturn(SOME_TIMES);
        given(genericFeeMultiplier.congestionLevelStarts()).willReturn(SOME_MORE_TIMES);

        assertSame(SOME_TIMES, subject.gasCongestionStarts());
        assertSame(SOME_MORE_TIMES, subject.genericCongestionStarts());
    }
}