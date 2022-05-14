package com.hedera.services.contracts.execution;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.time.Instant;

/**
 * A {@link BlockMetaSource} that gets its information from a particular {@link MerkleNetworkContext}, which
 * ideally will be an immutable instance from the latest signed state.
 *
 * The important thing is that, unlike {@link InHandleBlockMetaSource}, here the {@code computeBlockValues()}
 * implementation has no side effects on the state of the {@link com.hedera.services.state.logic.BlockManager}
 * singleton that manages block metadata in the working network context.
 */
public class StaticBlockMetaSource implements BlockMetaSource {
	private final MerkleNetworkContext networkCtx;

	private StaticBlockMetaSource(MerkleNetworkContext networkCtx) {
		this.networkCtx = networkCtx;
	}

	public static StaticBlockMetaSource from(final StateView stateView) {
		return new StaticBlockMetaSource(stateView.networkCtx());
	}

	@Override
	public Hash getBlockHash(final long blockNo) {
		return networkCtx.getBlockHashByNumber(blockNo);
	}

	@Override
	public BlockValues computeBlockValues(final long gasLimit) {
		final var nominalBlockNo = networkCtx.getAlignmentBlockNo();
		if (nominalBlockNo < 0) {
			var nominalTimestamp = networkCtx.firstConsTimeOfCurrentBlock();
			// This is exceedingly unlikely in practice, as it requires a ContractCallLocal query to run
			// as exactly the first network interaction immediately the 0.26 upgrade---since there's impact
			// on consensus state, falling back to Instant.now() is acceptable
			if (nominalTimestamp == null) {
				nominalTimestamp = Instant.now();
			}
			return new HederaBlockValues(gasLimit, nominalTimestamp.getEpochSecond(), nominalTimestamp);
		} else {
			return new HederaBlockValues(gasLimit, nominalBlockNo, networkCtx.firstConsTimeOfCurrentBlock());
		}
	}
}
