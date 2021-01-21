package com.hedera.services.context.domain.trackers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.PropertySource;

import java.time.Instant;
import java.util.Optional;

public class IssEventInfo {
	private final PropertySource properties;

	int remainingRoundsToDump = 0;
	private IssEventStatus status = IssEventStatus.NO_KNOWN_ISS;
	private Optional<Instant> consensusTimeOfRecentAlert = Optional.empty();

	public IssEventInfo(PropertySource properties) {
		this.properties = properties;
	}

	public IssEventStatus status() {
		return status;
	}

	public Optional<Instant> consensusTimeOfRecentAlert() {
		return consensusTimeOfRecentAlert;
	}

	public boolean shouldDumpThisRound() {
		return remainingRoundsToDump > 0;
	}

	public void decrementRoundsToDump() {
		remainingRoundsToDump--;
	}

	public synchronized void alert(Instant roundConsensusTime) {
		consensusTimeOfRecentAlert = Optional.of(roundConsensusTime);
		if (status == IssEventStatus.NO_KNOWN_ISS) {
			remainingRoundsToDump = properties.getIntProperty("iss.roundsToDump");
		}
		status = IssEventStatus.ONGOING_ISS;
	}

	public synchronized void relax() {
		status = IssEventStatus.NO_KNOWN_ISS;
		consensusTimeOfRecentAlert = Optional.empty();
		remainingRoundsToDump = 0;
	}
}
