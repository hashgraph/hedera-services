/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.fees.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void canMaxMultipliers() {
        given(gasFeeMultiplier.currentMultiplier(accessor)).willReturn(2L);
        given(genericFeeMultiplier.currentMultiplier(accessor)).willReturn(3L);
        assertEquals(3L, subject.maxCurrentMultiplier(accessor));
    }

    @Test
    void delegatesExpectationsReset() {
        subject.resetExpectations();
        verify(gasFeeMultiplier).resetExpectations();
        verify(genericFeeMultiplier).resetExpectations();
    }

    @Test
    void delegatesReset() {
        subject.resetGenericCongestionLevelStarts(SOME_MORE_TIMES);
        subject.resetGasCongestionLevelStarts(SOME_TIMES);
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
