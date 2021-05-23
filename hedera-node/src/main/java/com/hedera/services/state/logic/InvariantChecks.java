package com.hedera.services.state.logic;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.utils.PlatformTxnAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Encapsulates some basic invariants of the system, including:
 * <ol>
 *     <li>Consensus time must be strictly increasing.</li>
 *     <li>Zero-stake nodes cannot submit transactions.</li>
 * </ol>
 */
public class InvariantChecks {
	private static final Logger log = LogManager.getLogger(InvariantChecks.class);

	private final NodeInfo nodeInfo;
	private final Supplier<MerkleNetworkContext> networkCtx;

	public InvariantChecks(NodeInfo nodeInfo, Supplier<MerkleNetworkContext> networkCtx) {
		this.nodeInfo = nodeInfo;
		this.networkCtx = networkCtx;
	}

	public boolean holdFor(PlatformTxnAccessor accessor, Instant consensusTime, long submittingMember) {
		final var currentNetworkCtx = networkCtx.get();
		final var lastConsensusTime = currentNetworkCtx.consensusTimeOfLastHandledTxn();
		if (lastConsensusTime != null && !consensusTime.isAfter(lastConsensusTime)) {
			log.error(
					"Invariant failure! {} submitted by {} reached consensus at {}, not later than last-handled {}",
					accessor.getSignedTxn4Log(),
					submittingMember,
					consensusTime,
					lastConsensusTime);
			return false;
		}

		if (nodeInfo.isZeroStake(submittingMember)) {
			log.warn(
					"Invariant failure! Zero-stake node {} submitted {}",
					submittingMember,
					accessor.getSignedTxn4Log());
			return false;
		}

		return true;
	}
}
