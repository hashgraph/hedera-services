package com.hedera.services.keys;

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
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JThresholdKey;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static com.hedera.services.legacy.core.jproto.JKey.getValidKeyBytes;
import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;

/**
 * Provides a static method to determine if a Hedera key is <i>active</i> relative to
 * a set of platform signatures corresponding to its simple keys.
 *
 * @see JKey
 */
public final class HederaKeyActivation {
	public static final TransactionSignature INVALID_MISSING_SIG = new InvalidSignature();

	public static final BiPredicate<JKey, TransactionSignature> ONLY_IF_SIG_IS_VALID =
			(ignoredKey, sig) -> VALID.equals(sig.getSignatureStatus());

	private HederaKeyActivation() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Determines if the given transaction has an set of valid cryptographic signatures that,
	 * taken together, activate the payer's Hedera key.
	 *
	 * @param accessor
	 * 		the txn to evaluate.
	 * @param validity
	 * 		the logic deciding if a given simple key is activated by a given platform sig.
	 * @return whether the payer's Hedera key is active
	 */
	public static boolean payerSigIsActive(
			final TxnAccessor accessor,
			final BiPredicate<JKey, TransactionSignature> validity
	) {
		final var sigMeta = accessor.getSigMeta();

		if (sigMeta == null) {
			throw new IllegalArgumentException("Cannot test payer sig activation without rationalized sig meta");
		}
		if (!sigMeta.couldRationalizePayer()) {
			return false;
		}

		return isActive(sigMeta.payerKey(), sigMeta.pkToVerifiedSigFn(), validity);
	}

	/**
	 * Tests whether a Hedera key's top-level signature is activated by a given set of
	 * platform signatures, using the platform sigs to test activation of the simple keys in the
	 * Hedera key.
	 *
	 * <p><b>IMPORTANT:</b> The sigs must be supplied in the order that a DFS traversal
	 * of the Hedera key tree structure encounters the corresponding simple keys.
	 *
	 * @param key
	 * 		the top-level Hedera key to test for activation.
	 * @param sigsFn
	 * 		the source of platform signatures for the simple keys in the Hedera key.
	 * @param validity
	 * 		the logic deciding if a given simple key is activated by a given platform sig.
	 * @return whether the Hedera key is active.
	 */
	public static boolean isActive(
			final JKey key,
			final Function<byte[], TransactionSignature> sigsFn,
			final BiPredicate<JKey, TransactionSignature> validity
	) {
		return isActive(key, sigsFn, validity, DEFAULT_ACTIVATION_CHARACTERISTICS);
	}

	public static boolean isActive(
			final JKey key,
			final Function<byte[], TransactionSignature> sigsFn,
			final BiPredicate<JKey, TransactionSignature> validity,
			final KeyActivationCharacteristics characteristics
	) {
		if (!key.hasKeyList() && !key.hasThresholdKey()) {
			return validity.test(key, sigsFn.apply(getValidKeyBytes(key)));
		} else {
			final var children = key.hasKeyList()
					? key.getKeyList().getKeysList()
					: key.getThresholdKey().getKeys().getKeysList();
			final var m = key.hasKeyList()
					? characteristics.sigsNeededForList((JKeyList) key)
					: characteristics.sigsNeededForThreshold((JThresholdKey) key);
			var n = 0;
			for (var child : children) {
				if (isActive(child, sigsFn, validity)) {
					n++;
				}
			}
			return n >= m;
		}
	}

	/**
	 * Factory for a source of platform signatures backed by a list.
	 *
	 * @param sigs
	 * 		the backing list of platform sigs.
	 * @return a supplier that produces the backing list sigs by public key.
	 */
	public static Function<byte[], TransactionSignature> pkToSigMapFrom(final List<TransactionSignature> sigs) {
		return key -> {
			for (var sig : sigs) {
				if (Arrays.equals(key, sig.getExpandedPublicKeyDirect())) {
					return sig;
				}
			}
			return INVALID_MISSING_SIG;
		};
	}

	private static class InvalidSignature extends TransactionSignature {
		private static final byte[] MEANINGLESS_BYTE = new byte[] {
				(byte) 0xAB
		};

		private InvalidSignature() {
			super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
		}

		@Override
		public VerificationStatus getSignatureStatus() {
			return INVALID;
		}
	}
}
