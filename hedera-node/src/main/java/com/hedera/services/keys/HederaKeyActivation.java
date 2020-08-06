package com.hedera.services.keys;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.order.SigningOrderResultFactory;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JThresholdKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.VerificationStatus;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static com.swirlds.common.crypto.VerificationStatus.*;
import static java.util.Arrays.copyOfRange;
import static java.util.stream.Collectors.toMap;

/**
 * Provides a static method to determine if a Hedera key is <i>active</i> relative to
 * a set of platform signatures corresponding to its simple keys.
 *
 * @author Michael Tinker
 * @see JKey
 */
public class HederaKeyActivation {
	public static final Signature INVALID_SIG = new InvalidSignature();

	public static final BiPredicate<JKey, Signature> ONLY_IF_SIG_IS_VALID =
			(ignoredKey, sig) -> VALID.name().equals( sig.getSignatureStatus().name() );

	private HederaKeyActivation(){
		throw new IllegalStateException("Utility Class");
	}

	/**
	 * Determines if the given transaction has an set of valid cryptographic signatures that,
	 * taken together, activate the payer's Hedera key.
	 *
	 * @param accessor the txn to evaluate.
	 * @param keyOrder a resource to determine the payer's Hedera key.
	 * @param summaryFactory a resource to summarize the result of the key determination.
	 * @return whether the payer's Hedera key is active.
	 */
	public static boolean payerSigIsActive(
			PlatformTxnAccessor accessor,
			HederaSigningOrder keyOrder,
			SigningOrderResultFactory<SignatureStatus> summaryFactory
	) {
		SigningOrderResult<SignatureStatus> payerSummary = keyOrder.keysForPayer(accessor.getTxn(), summaryFactory);

		return isActive(
				payerSummary.getPayerKey(),
				pkToSigMapFrom(accessor.getPlatformTxn().getSignatures()),
				ONLY_IF_SIG_IS_VALID);
	}

	/**
	 * Determines if the given transaction has an set of valid cryptographic signatures that,
	 * taken together, activate the Hedera keys of non-payer entities required to sign.
	 *
	 * @param accessor the txn to evaluate.
	 * @param keyOrder a resource to determine the non-payer entities' Hedera keys.
	 * @param summaryFactory a resource to summarize the result of the key determination.
	 * @return whether the non-payer entities' Hedera keys are active.
	 */
	public static boolean otherPartySigsAreActive(
			PlatformTxnAccessor accessor,
			HederaSigningOrder keyOrder,
			SigningOrderResultFactory<SignatureStatus> summaryFactory
	) {
		return otherPartySigsAreActive(accessor, keyOrder, summaryFactory, DEFAULT_ACTIVATION_CHARACTERISTICS);
	}

	public static boolean otherPartySigsAreActive(
			PlatformTxnAccessor accessor,
			HederaSigningOrder keyOrder,
			SigningOrderResultFactory<SignatureStatus> summaryFactory,
			KeyActivationCharacteristics characteristics
	) {
		TransactionBody txn = accessor.getTxn();
		Function<byte[], Signature> sigsFn = pkToSigMapFrom(accessor.getPlatformTxn().getSignatures());

		SigningOrderResult<SignatureStatus> othersResult = keyOrder.keysForOtherParties(txn, summaryFactory);
		for (JKey otherKey : othersResult.getOrderedKeys()) {
			if (!isActive(otherKey, sigsFn, ONLY_IF_SIG_IS_VALID, characteristics)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Tests whether a Hedera key's top-level signature is activated by a given set of
	 * platform signatures, using the platform sigs to test activation of the simple keys in the
	 * Hedera key.
	 *
	 * <p><b>IMPORTANT:</b> The sigs must be supplied in the order that a DFS traversal
	 * of the Hedera key tree structure encounters the corresponding simple keys.
	 *
	 * @param key the top-level Hedera key to test for activation.
	 * @param sigsFn the source of platform signatures for the simple keys in the Hedera key.
	 * @param tests the logic deciding if a given simple key is activated by a given platform sig.
	 * @return whether the Hedera key is active.
	 */
	public static boolean isActive(
			JKey key,
			Function<byte[], Signature> sigsFn,
			BiPredicate<JKey, Signature> tests
	) {
		return isActive(key, sigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS);
	}

	public static boolean isActive(
			JKey key,
			Function<byte[], Signature> sigsFn,
			BiPredicate<JKey, Signature> tests,
			KeyActivationCharacteristics characteristics
	) {
		if (!key.hasKeyList() && !key.hasThresholdKey()) {
			return tests.test(key, sigsFn.apply(key.getEd25519()));
		} else {
			List<JKey> children = key.hasKeyList()
					? key.getKeyList().getKeysList()
					: key.getThresholdKey().getKeys().getKeysList();
			int M = key.hasKeyList()
					? characteristics.sigsNeededForList((JKeyList)key)
					: characteristics.sigsNeededForThreshold((JThresholdKey)key);
			return children.stream().mapToInt(child -> isActive(child, sigsFn, tests) ? 1 : 0).sum() >= M;
		}
	}

	/**
	 * Factory for a source of platform signatures backed by a list.
	 *
	 * @param sigs the backing list of platform sigs.
	 * @return a supplier that produces the backing list sigs by public key.
	 */
	public static Function<byte[], Signature> pkToSigMapFrom(List<Signature> sigs) {
		final Map<ByteString, Signature> pkSigs = sigs
				.stream()
				.collect(toMap(s -> ByteString.copyFrom(s.getExpandedPublicKeyDirect()), s -> s, (a, b) -> a));

		return ed25519 -> pkSigs.getOrDefault(ByteString.copyFrom(ed25519), INVALID_SIG);
	}

	private static class InvalidSignature extends Signature {
		private static byte[] MEANINGLESS_BYTE = new byte[] {
				(byte)0xAB
		};

		public InvalidSignature() {
			super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
		}

		@Override
		public VerificationStatus getSignatureStatus() {
			return INVALID;
		}
	}
}
