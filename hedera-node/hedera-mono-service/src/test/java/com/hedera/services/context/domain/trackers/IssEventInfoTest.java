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
package com.hedera.services.context.domain.trackers;

import static com.hedera.services.context.domain.trackers.IssEventStatus.NO_KNOWN_ISS;
import static com.hedera.services.context.domain.trackers.IssEventStatus.ONGOING_ISS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.properties.NodeLocalProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssEventInfoTest {
    private final Instant firstIssTime = Instant.now().minus(30, ChronoUnit.SECONDS);
    private final Instant recentIssTime = Instant.now();
    private final int roundsToDump = 2;

    @Mock private NodeLocalProperties nodeLocalProperties;

    private IssEventInfo subject;

    @BeforeEach
    void setup() {
        subject = new IssEventInfo(nodeLocalProperties);
    }

    @Test
    void startsClean() {
        // expect:
        assertEquals(NO_KNOWN_ISS, subject.status());
    }

    @Test
    void alertWorks() {
        given(nodeLocalProperties.issRoundsToLog()).willReturn(roundsToDump);

        // when:
        subject.alert(firstIssTime);

        // then:
        assertEquals(ONGOING_ISS, subject.status());
        // and:
        assertEquals(firstIssTime, subject.consensusTimeOfRecentAlert().get());
        assertTrue(subject.shouldLogThisRound());

        // and when:
        subject.decrementRoundsToLog();
        subject.alert(recentIssTime);
        // then:
        assertEquals(recentIssTime, subject.consensusTimeOfRecentAlert().get());
        assertTrue(subject.shouldLogThisRound());
        subject.decrementRoundsToLog();
        assertFalse(subject.shouldLogThisRound());
    }

    @Test
    void relaxWorks() {
        given(nodeLocalProperties.issRoundsToLog()).willReturn(roundsToDump);
        subject.alert(firstIssTime);

        subject.relax();

        assertEquals(NO_KNOWN_ISS, subject.status());
        assertEquals(0, subject.remainingRoundsToLog);
        assertTrue(subject.consensusTimeOfRecentAlert().isEmpty());
    }
}
