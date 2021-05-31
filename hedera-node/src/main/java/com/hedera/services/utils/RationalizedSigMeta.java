package com.hedera.services.utils;

import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;
import java.util.function.Function;

public class RationalizedSigMeta {
	private final List<JKey> payerReqSigs;
	private final List<JKey> otherPartyReqSigs;
	private final Function<byte[], TransactionSignature> sigsFn;

	public RationalizedSigMeta(
			List<JKey> payerReqSigs,
			List<JKey> otherPartyReqSigs,
			Function<byte[], TransactionSignature> sigsFn
	) {
		this.payerReqSigs = payerReqSigs;
		this.otherPartyReqSigs = otherPartyReqSigs;
		this.sigsFn = sigsFn;
	}
}
