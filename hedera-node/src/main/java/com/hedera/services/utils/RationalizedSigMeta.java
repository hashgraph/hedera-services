package com.hedera.services.utils;

import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.pkToSigMapFrom;

public class RationalizedSigMeta {
	private static final RationalizedSigMeta NONE_AVAIL = new RationalizedSigMeta();

	private final List<JKey> payerReqSigs;
	private final List<JKey> othersReqSigs;
	private final Function<byte[], TransactionSignature> pkToVerifiedSigFn;

	private RationalizedSigMeta() {
		this.pkToVerifiedSigFn = null;
		this.payerReqSigs = null;
		this.othersReqSigs = null;
	}

	private RationalizedSigMeta(
			List<JKey> payerReqSigs,
			List<JKey> othersReqSigs,
			Function<byte[], TransactionSignature> pkToVerifiedSigFn
	) {
		this.pkToVerifiedSigFn = pkToVerifiedSigFn;
		this.payerReqSigs = payerReqSigs;
		this.othersReqSigs = othersReqSigs;
	}

	public static RationalizedSigMeta noneAvailable() {
		return NONE_AVAIL;
	}

	public static RationalizedSigMeta forPayerOnly(
			List<JKey> payerReqSigs,
			List<TransactionSignature> rationalizedSigs
	) {
		return forPayerAndOthers(payerReqSigs, null, rationalizedSigs);
	}

	public static RationalizedSigMeta forPayerAndOthers(
			List<JKey> payerReqSigs,
			List<JKey> othersReqSigs,
			List<TransactionSignature> rationalizedSigs
	) {
		return new RationalizedSigMeta(payerReqSigs, othersReqSigs, pkToSigMapFrom(rationalizedSigs));
	}

	public boolean couldRationalizePayer() {
		return payerReqSigs != null;
	}

	public boolean couldRationalizeOthers() {
		return othersReqSigs != null;
	}

	public List<JKey> payerReqSigs() {
		if (payerReqSigs == null) {
			throw new IllegalStateException("Payer sigs could not be rationalized");
		}
		return payerReqSigs;
	}

	public List<JKey> othersReqSigs() {
		if (othersReqSigs == null) {
			throw new IllegalStateException("Other-party sigs could not be rationalized");
		}
		return othersReqSigs;
	}

	public Function<byte[], TransactionSignature> pkToVerifiedSigFn() {
		if (pkToVerifiedSigFn == null) {
			throw new IllegalStateException("Verified signatures could not be rationalied");
		}
		return pkToVerifiedSigFn;
	}
}
