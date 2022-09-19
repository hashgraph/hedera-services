/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HapiOpSpeedometersTest {
    @Mock private Platform platform;
    @Mock private HapiOpCounters counters;
    @Mock private NodeLocalProperties properties;

    @Mock private SpeedometerMetric xferReceived;
    @Mock private SpeedometerMetric xferSubmitted;
    @Mock private SpeedometerMetric xferHandled;
    @Mock private SpeedometerMetric infoReceived;
    @Mock private SpeedometerMetric infoAnswered;
    @Mock private SpeedometerMetric xferDeprecatedRcvd;

    private Function<HederaFunctionality, String> statNameFn;
    private HapiOpSpeedometers subject;

    @BeforeEach
    void setup() {
        HapiOpSpeedometers.allFunctions =
                () -> new HederaFunctionality[] {CryptoTransfer, TokenGetInfo};

        statNameFn = HederaFunctionality::toString;

        properties = mock(NodeLocalProperties.class);

        subject = new HapiOpSpeedometers(counters, properties, statNameFn);
    }

    @AfterEach
    public void cleanup() {
        HapiOpSpeedometers.allFunctions = HederaFunctionality.class::getEnumConstants;
    }

    @Test
    void beginsRationally() {
        subject.registerWith(platform);

        // expect:
        assertTrue(subject.getReceivedOps().containsKey(CryptoTransfer));
        assertTrue(subject.getSubmittedTxns().containsKey(CryptoTransfer));
        assertTrue(subject.getHandledTxns().containsKey(CryptoTransfer));
        assertFalse(subject.getAnsweredQueries().containsKey(CryptoTransfer));

        // and:
        assertTrue(subject.getReceivedOps().containsKey(TokenGetInfo));
        assertTrue(subject.getAnsweredQueries().containsKey(TokenGetInfo));
        assertFalse(subject.getSubmittedTxns().containsKey(TokenGetInfo));
        assertFalse(subject.getHandledTxns().containsKey(TokenGetInfo));
    }

    @Test
    void registersExpectedStatEntries() {
        subject.registerWith(platform);

        // then:
        verify(platform, times(6)).getOrCreateMetric(any());
    }

    @Test
    void updatesSpeedometersAsExpected() {
        // setup:
        subject.getLastReceivedOpsCount().put(CryptoTransfer, 1L);
        subject.getLastSubmittedTxnsCount().put(CryptoTransfer, 2L);
        subject.getLastHandledTxnsCount().put(CryptoTransfer, 3L);
        subject.setLastReceivedDeprecatedTxnCount(4L);
        // and:
        subject.getLastReceivedOpsCount().put(TokenGetInfo, 4L);
        subject.getLastAnsweredQueriesCount().put(TokenGetInfo, 5L);
        // and:

        given(counters.receivedSoFar(CryptoTransfer)).willReturn(2L);
        given(counters.submittedSoFar(CryptoTransfer)).willReturn(4L);
        given(counters.handledSoFar(CryptoTransfer)).willReturn(6L);
        given(counters.receivedSoFar(TokenGetInfo)).willReturn(8L);
        given(counters.answeredSoFar(TokenGetInfo)).willReturn(10L);
        given(counters.receivedDeprecatedTxnSoFar()).willReturn(12L);
        // and:
        subject.getReceivedOps().put(CryptoTransfer, xferReceived);
        subject.getSubmittedTxns().put(CryptoTransfer, xferSubmitted);
        subject.getHandledTxns().put(CryptoTransfer, xferHandled);
        subject.getReceivedOps().put(TokenGetInfo, infoReceived);
        subject.getAnsweredQueries().put(TokenGetInfo, infoAnswered);
        subject.setReceivedDeprecatedTxns(xferDeprecatedRcvd);

        // when:
        subject.updateAll();

        // then:
        assertEquals(2L, subject.getLastReceivedOpsCount().get(CryptoTransfer));
        assertEquals(4L, subject.getLastSubmittedTxnsCount().get(CryptoTransfer));
        assertEquals(6L, subject.getLastHandledTxnsCount().get(CryptoTransfer));
        assertEquals(8L, subject.getLastReceivedOpsCount().get(TokenGetInfo));
        assertEquals(10L, subject.getLastAnsweredQueriesCount().get(TokenGetInfo));
        assertEquals(12L, subject.getLastReceivedDeprecatedTxnCount());
        // and:
        verify(xferReceived).update(1);
        verify(xferSubmitted).update(2);
        verify(xferHandled).update(3);
        verify(infoReceived).update(4);
        verify(infoAnswered).update(5);
    }
}
