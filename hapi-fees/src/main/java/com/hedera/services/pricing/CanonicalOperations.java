package com.hedera.services.pricing;

import com.google.protobuf.ByteString;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SubType;

public class CanonicalOperations {
	private static final ByteString CANONICAL_SIG = ByteString.copyFromUtf8(
			"0123456789012345678901234567890123456789012345678901234567890123");

	private static final SignatureMap ONE_PAIR_SIG_MAP = SignatureMap.newBuilder()
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("a"))
					.setEd25519(CANONICAL_SIG))
			.build();
	private static final SigUsage SINGLE_SIG_USAGE = new SigUsage(
			1, ONE_PAIR_SIG_MAP.getSerializedSize(), 1
	);

	private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
	private static final ConsensusOpsUsage CONSENSUS_OPS_USAGE = new ConsensusOpsUsage();

	public UsageAccumulator canonicalUsageFor(HederaFunctionality function, SubType type) {
		switch (function) {
			case CryptoTransfer:
				switch (type) {
					case DEFAULT:
						return hbarCryptoTransfer();
					case TOKEN_FUNGIBLE_COMMON:
						return htsCryptoTransfer();
					case TOKEN_NON_FUNGIBLE_UNIQUE:
						return nftCryptoTransfer();
				}
				break;
			case ConsensusSubmitMessage:
				return submitMessage();
			case TokenFeeScheduleUpdate:
				return feeScheduleUpdate();
		}

		throw new IllegalArgumentException("Canonical usage unknown");
	}

	private UsageAccumulator submitMessage() {
		final var baseMeta = new BaseTransactionMeta(0, 0);
		final var opMeta = new SubmitMessageMeta(100);
		final var into = new UsageAccumulator();
		CONSENSUS_OPS_USAGE.submitMessageUsage(SINGLE_SIG_USAGE, opMeta, baseMeta, into);
		return into;
	}

	private UsageAccumulator feeScheduleUpdate() {
		throw new AssertionError("Not implemented!");
	}

	private UsageAccumulator hbarCryptoTransfer() {
		throw new AssertionError("Not implemented!");
	}

	private UsageAccumulator htsCryptoTransfer() {
		throw new AssertionError("Not implemented!");
	}

	private UsageAccumulator nftCryptoTransfer() {
		throw new AssertionError("Not implemented!");
	}
}
