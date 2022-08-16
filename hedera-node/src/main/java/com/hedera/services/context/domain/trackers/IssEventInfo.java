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

import com.hedera.services.context.properties.NodeLocalProperties;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IssEventInfo {
    private final NodeLocalProperties nodeLocalProperties;

    int remainingRoundsToLog = 0;
    private IssEventStatus status = NO_KNOWN_ISS;
    private Optional<Instant> consensusTimeOfRecentAlert = Optional.empty();

    @Inject
    public IssEventInfo(final NodeLocalProperties nodeLocalProperties) {
        this.nodeLocalProperties = nodeLocalProperties;
    }

    public IssEventStatus status() {
        return status;
    }

    public Optional<Instant> consensusTimeOfRecentAlert() {
        return consensusTimeOfRecentAlert;
    }

    public boolean shouldLogThisRound() {
        return remainingRoundsToLog > 0;
    }

    public void decrementRoundsToLog() {
        remainingRoundsToLog--;
    }

    public synchronized void alert(Instant roundConsensusTime) {
        consensusTimeOfRecentAlert = Optional.of(roundConsensusTime);
        if (status == NO_KNOWN_ISS) {
            remainingRoundsToLog = nodeLocalProperties.issRoundsToLog();
        }
        status = ONGOING_ISS;
    }

    public synchronized void relax() {
        status = NO_KNOWN_ISS;
        consensusTimeOfRecentAlert = Optional.empty();
        remainingRoundsToLog = 0;
    }
}
