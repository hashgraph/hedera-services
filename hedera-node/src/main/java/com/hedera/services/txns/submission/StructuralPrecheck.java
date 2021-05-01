package com.hedera.services.txns.submission;

import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;

public class StructuralPrecheck {
	private final int maxSignedTxnSize;
	private final int maxProtoMessageDepth;

	private static final EnumMap<ResponseCodeEnum, Pair<ResponseCodeEnum, Optional<SignedTxnAccessor>>> FLAWS =
			new EnumMap<>(ResponseCodeEnum.class) {{
				put(INVALID_TRANSACTION, Pair.of(INVALID_TRANSACTION, Optional.empty()));
				put(TRANSACTION_OVERSIZE, Pair.of(TRANSACTION_OVERSIZE, Optional.empty()));
				put(INVALID_TRANSACTION_BODY, Pair.of(INVALID_TRANSACTION_BODY, Optional.empty()));
			}};

	public StructuralPrecheck(int maxSignedTxnSize, int maxProtoMessageDepth) {
		this.maxSignedTxnSize = maxSignedTxnSize;
		this.maxProtoMessageDepth = maxProtoMessageDepth;
	}

	public Pair<ResponseCodeEnum, Optional<SignedTxnAccessor>> validate(Transaction signedTxn) {
		final var hasSignedTxnBytes = !signedTxn.getSignedTransactionBytes().isEmpty();
		final var hasDeprecatedSigMap = signedTxn.hasSigMap();
		final var hasDeprecatedBodyBytes = !signedTxn.getBodyBytes().isEmpty();

		if (hasSignedTxnBytes) {
			if (hasDeprecatedBodyBytes || hasDeprecatedSigMap) {
				return FLAWS.get(INVALID_TRANSACTION);
			}
		} else if (!hasDeprecatedBodyBytes) {
			return FLAWS.get(INVALID_TRANSACTION_BODY);
		}

		if (signedTxn.getSerializedSize() > maxSignedTxnSize) {
			return FLAWS.get(TRANSACTION_OVERSIZE);
		}

		throw new AssertionError("Not implemented!");
	}

	int protoDepthOf(GeneratedMessageV3 msg) {
		return 0;
	}
}
