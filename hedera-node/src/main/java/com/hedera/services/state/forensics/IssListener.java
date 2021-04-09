package com.hedera.services.state.forensics;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesMain;
import com.hedera.services.ServicesState;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.swirlds.common.AddressBook;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldState;
import com.swirlds.common.events.Event;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Function;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

public class IssListener implements InvalidSignedStateListener {
	static Logger log = LogManager.getLogger(IssListener.class);

	static final String ISS_ERROR_MSG_PATTERN =
			"In round %d, node %d received a signed state from node %d with a signature different than %s on %s";
	static final String ISS_FALLBACK_ERROR_MSG_PATTERN =
			"In round %d, node %s received a signed state from node %s differing from its local "
					+ "signed state; could not provide all details";

	private final FcmDump fcmDump;
	private final IssEventInfo issEventInfo;

	public IssListener(FcmDump fcmDump, IssEventInfo issEventInfo) {
		this.fcmDump = fcmDump;
		this.issEventInfo = issEventInfo;
	}

	@Override
	public void notifyError(
			Platform platform,
			AddressBook addressBook,
			SwirldState swirldsState,
			Event[] events,
			NodeId self, NodeId other,
			long round, Instant consensusTime, long numConsEvents,
			byte[] sig, byte[] hash
	) {
		try {
			ServicesState issState = (ServicesState) swirldsState;
			issEventInfo.alert(consensusTime);
			if (issEventInfo.shouldDumpThisRound()) {
				issEventInfo.decrementRoundsToDump();
				var msg = String.format(
						ISS_ERROR_MSG_PATTERN,
						round, self.getId(), other.getId(),
						encodeHexString(sig), encodeHexString(hash));
				log.error(msg);
				fcmDump.dumpFrom(issState, self, round);
				issState.logSummary();
			}
		} catch (Exception any) {
			String fallbackMsg = String.format(
					ISS_FALLBACK_ERROR_MSG_PATTERN, round, String.valueOf(self), String.valueOf(other));
			log.warn(fallbackMsg, any);
		}
	}
}
