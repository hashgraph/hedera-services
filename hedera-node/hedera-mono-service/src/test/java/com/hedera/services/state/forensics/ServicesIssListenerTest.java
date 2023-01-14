/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.state.forensics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.state.notifications.IssNotification;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ServicesIssListenerTest {
    private final long otherId = 2;
    private final long round = 1_234_567;
    private final Instant consensusTime = Instant.now();

    @Mock private ServicesState state;
    @Mock private IssEventInfo issEventInfo;
    @Mock private IssNotification issNotification;
    @Mock private Platform platform;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private ServicesIssListener subject;

    @BeforeEach
    void setup() {
        subject = new ServicesIssListener(issEventInfo, platform);
    }

    @Test
    void doesntNotifyIfOtherNodeHasIss() {
        given(issNotification.getIssType()).willReturn(IssNotification.IssType.OTHER_ISS);
        subject.notify(issNotification);

        assertTrue(logCaptor.warnLogs().isEmpty());
        verify(issNotification, never()).getRound();
        verify(issEventInfo, never()).decrementRoundsToLog();
    }

    @Test
    void logsFallbackInfo() {
        givenNoticeMeta();
        // when:
        subject.notify(issNotification);

        // then:
        var desired =
                String.format(ServicesIssListener.ISS_FALLBACK_ERROR_MSG_PATTERN, round, otherId);
        assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith(desired)));
    }

    @Test
    void onlyLogsIfConfiguredInfo() {
        givenNoticeMeta();

        given(issEventInfo.shouldLogThisRound()).willReturn(true);
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        given(state.getTimeOfLastHandledTxn()).willReturn(consensusTime);

        subject.notify(issNotification);

        // then:
        var desired = String.format(ServicesIssListener.ISS_ERROR_MSG_PATTERN, round, otherId);
        verify(issEventInfo).alert(consensusTime);
        verify(issEventInfo).decrementRoundsToLog();
        assertThat(logCaptor.errorLogs(), contains(desired));
        verify(state).logSummary();
    }

    @Test
    void shouldDumpThisRoundIsFalse() {
        givenNoticeMeta();

        given(issEventInfo.shouldLogThisRound()).willReturn(false);
        given(platform.getLatestImmutableState())
                .willReturn(new AutoCloseableWrapper<>(state, () -> {}));
        given(state.getTimeOfLastHandledTxn()).willReturn(consensusTime);

        // when:
        subject.notify(issNotification);

        // then:
        var desired = String.format(ServicesIssListener.ISS_ERROR_MSG_PATTERN, round, otherId);
        verify(issEventInfo).alert(consensusTime);
        verify(issEventInfo, never()).decrementRoundsToLog();
        verify(state, never()).logSummary();
    }

    private void givenNoticeMeta() {
        given(issNotification.getRound()).willReturn(round);
        given(issNotification.getOtherNodeId()).willReturn(otherId);
    }
}
