package com.hedera.services.utils;

import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.pkToSigMapFrom;

public class RationalizedSigMeta {
	private static final RationalizedSigMeta NONE_AVAIL = new RationalizedSigMeta();

	private final JKey payerReqSig;
	private final List<JKey> othersReqSigs;
	private final List<TransactionSignature> rationalizedSigs;
	private final Function<byte[], TransactionSignature> pkToVerifiedSigFn;

	private RationalizedSigMeta() {
		payerReqSig = null;
		othersReqSigs = null;
		rationalizedSigs = null;
		pkToVerifiedSigFn = null;
	}

	private RationalizedSigMeta(
			JKey payerReqSig,
			List<JKey> othersReqSigs,
			List<TransactionSignature> rationalizedSigs,
			Function<byte[], TransactionSignature> pkToVerifiedSigFn
	) {
		this.payerReqSig = payerReqSig;
		this.othersReqSigs = othersReqSigs;
		this.rationalizedSigs = rationalizedSigs;
		this.pkToVerifiedSigFn = pkToVerifiedSigFn;
	}

	public static RationalizedSigMeta noneAvailable() {
		return NONE_AVAIL;
	}

	public static RationalizedSigMeta forPayerOnly(
			JKey payerReqSig,
			List<TransactionSignature> rationalizedSigs
	) {
		return forPayerAndOthers(payerReqSig, null, rationalizedSigs);
	}

	public static RationalizedSigMeta forPayerAndOthers(
			JKey payerReqSig,
			List<JKey> othersReqSigs,
			List<TransactionSignature> rationalizedSigs
	) {
		return new RationalizedSigMeta(
				payerReqSig,
				othersReqSigs,
				rationalizedSigs,
				pkToSigMapFrom(rationalizedSigs));
	}

	public boolean couldRationalizePayer() {
		return payerReqSig != null;
	}

	public boolean couldRationalizeOthers() {
		return othersReqSigs != null;
	}

	public List<TransactionSignature> verifiedSigs() {
		if (rationalizedSigs == null) {
			throw new IllegalStateException("Verified signatures could not be rationalized");
		}
		return rationalizedSigs;
	}

	public JKey payerKey() {
		if (payerReqSig == null) {
			throw new IllegalStateException("Payer required signing keys could not be rationalized");
		}
		return payerReqSig;
	}

	public List<JKey> othersReqSigs() {
		if (othersReqSigs == null) {
			throw new IllegalStateException("Other-party required signing keys could not be rationalized");
		}
		return othersReqSigs;
	}

	public Function<byte[], TransactionSignature> pkToVerifiedSigFn() {
		if (pkToVerifiedSigFn == null) {
			throw new IllegalStateException("Verified signatures could not be rationalized");
		}
		return pkToVerifiedSigFn;
	}
}
