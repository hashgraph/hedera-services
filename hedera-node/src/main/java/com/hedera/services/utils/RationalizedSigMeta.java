package com.hedera.services.utils;

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
