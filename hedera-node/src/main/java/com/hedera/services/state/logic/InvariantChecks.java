package com.hedera.services.state.logic;

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

import com.hedera.services.context.NodeInfo;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.utils.LogUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Encapsulates some basic invariants of the system, including:
 * <ol>
 *     <li>Consensus time must be strictly increasing.</li>
 *     <li>Zero-stake nodes cannot submit transactions.</li>
 * </ol>
 */
@Singleton
public class InvariantChecks {
	private static final Logger log = LogManager.getLogger(InvariantChecks.class);

	private final NodeInfo nodeInfo;
	private final Supplier<MerkleNetworkContext> networkCtx;

	@Inject
	public InvariantChecks(NodeInfo nodeInfo, Supplier<MerkleNetworkContext> networkCtx) {
		this.nodeInfo = nodeInfo;
		this.networkCtx = networkCtx;
	}

	public boolean holdFor(PlatformTxnAccessor accessor, Instant consensusTime, long submittingMember) {
		final var currentNetworkCtx = networkCtx.get();
		final var lastConsensusTime = currentNetworkCtx.consensusTimeOfLastHandledTxn();
		if (lastConsensusTime != null && !consensusTime.isAfter(lastConsensusTime)) {
			final String logMessage = String.format("submitted by %d reached consensus at %s, not later than last-handled %s",
					submittingMember, consensusTime, lastConsensusTime);
			LogUtils.encodeGrpcAndLog(log, Level.ERROR, "Invariant failure! %s " + logMessage, accessor.getSignedTxnWrapper());
			return false;
		}

		if (nodeInfo.isZeroStake(submittingMember)) {
			LogUtils.encodeGrpcAndLog(log, Level.WARN,
					"Invariant failure! Zero-stake node " + submittingMember + " submitted %s",
					accessor.getSignedTxnWrapper());
			return false;
		}

		return true;
	}
}
