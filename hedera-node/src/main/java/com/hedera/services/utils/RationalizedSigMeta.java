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
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.pkToSigMapFrom;

/**
 * A simple wrapper around the three outputs of the {@code Rationalization#execute()} process.
 *
 * These outputs are,
 * <ol>
 *     <li>The payer key required to sign the active transaction.</li>
 *     <li>The list of other-party keys (if any) required to sign the active transaction.</li>
 *     <li>The mapping from a public key to the verified {@link TransactionSignature} for that key.
 * </ol>
 *
 * If a transaction is invalid, it is possible that one or both of the payer key and the list
 * of other-party keys will be unavailable. So this wrapper class can be constructed using one
 * of three factories: {@link RationalizedSigMeta#noneAvailable()},
 * {@link RationalizedSigMeta#forPayerOnly(JKey, List)}, and {@link RationalizedSigMeta#forPayerAndOthers(JKey, List, List)}.
 * (There is no factory for just other-party signatures, because without a payer signature
 * {@link com.hedera.services.ServicesState#handleTransaction(long, boolean, Instant, Instant, SwirldTransaction, SwirldDualState)}
 * will abort almost immediately.)
 *
 * Note that the mapping from public key to verified {@link TransactionSignature} is equivalent
 * to just the list of verified {@link TransactionSignature}s, since each {@link TransactionSignature}
 * instance includes the relevant public key. We construct the function in this class just
 * to avoid repeating that work twice in {@code handleTransaction}.
 */
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
			final JKey payerReqSig,
			final List<JKey> othersReqSigs,
			final List<TransactionSignature> rationalizedSigs,
			final Function<byte[], TransactionSignature> pkToVerifiedSigFn
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
			final JKey payerReqSig,
			final List<TransactionSignature> rationalizedSigs
	) {
		return forPayerAndOthers(payerReqSig, null, rationalizedSigs);
	}

	public static RationalizedSigMeta forPayerAndOthers(
			final JKey payerReqSig,
			final List<JKey> othersReqSigs,
			final List<TransactionSignature> rationalizedSigs
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
