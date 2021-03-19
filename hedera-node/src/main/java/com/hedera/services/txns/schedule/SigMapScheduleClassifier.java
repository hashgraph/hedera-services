package com.hedera.services.txns.schedule;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.keys.HederaKeyTraversal.visitSimpleKeys;
import static com.hedera.services.sigs.sourcing.SigMapPubKeyToSigBytes.beginsWith;
import static com.hedera.services.txns.schedule.SigClassification.INVALID_SCHEDULED_TXN_MATCH;
import static com.hedera.services.txns.schedule.SigClassification.NO_MATCH;
import static com.hedera.services.txns.schedule.SigClassification.PAYER_MATCH;
import static com.hedera.services.txns.schedule.SigClassification.VALID_SCHEDULED_TXN_MATCH;
import static com.swirlds.common.crypto.VerificationStatus.VALID;

/**
 * Provides a best-effort attempt to classify the signing outcomes of the
 * Ed25519 keys linked to the active schedule. (That is, the Ed25519 keys that
 * compose the Hedera keys prerequisite to the active scheduled transaction.)
 *
 * Note that if a linked Ed25519 key did provide a valid signature, it will
 * never cause the {@code validScheduleKeys} method to return an empty
 * {@code Optional}. And in general, a valid signature with an unrelated Ed25519
 * key will <i>also</i> not cause the {@code validScheduleKeys} method to return
 * an empty {@code Optional}.
 *
 * But it <i>is</i> possible for collisions in public key prefixes to make
 * it appear that a valid signature with an unrelated Ed25519 key was
 * actually an invalid signature with a linked key. See for example the
 * {@code ScheduleSignSpecs.overlappingKeysTreatedAsExpected()} in
 * the {@code test-clients} module.
 *
 * @author Michael Tinker
 */
public class SigMapScheduleClassifier {
	/**
	 * Returns the list of Ed25519 keys linked to the active schedule that
	 * had valid signatures on the active transaction. If a linked Ed25519
	 * key was expanded to an invalid signature, returns an empty {@code Optional}
	 * to indicate some signatures were invalid.
	 *
	 * Makes a best-effort attempt to return an empty optional <i>only</i>
	 * if a {@code SignaturePair} in the active {@code SignatureMap}
	 * expanded to exclusively invalid signatures; and seems linked to the active
	 * schedule; and cannot be "explained" as an invalid payer signature.
	 *
	 * @param payerKey the Hedera key for the active payer
	 * @param sigMap the active transaction's signature map
	 * @param sigsFn the active mapping from Ed25519 keys to expanded signatures
	 * @param scheduleCryptoSigs a traversal accepting a visitor to the Ed25519 keys linked to the active schedule
	 * @return the list of linked Ed25519 with valid signatures, if none appears to have given an invalid signature
	 */
	Optional<List<JKey>> validScheduleKeys(
			JKey payerKey,
			SignatureMap sigMap,
			Function<byte[], TransactionSignature> sigsFn,
			Consumer<BiConsumer<JKey, TransactionSignature>> scheduleCryptoSigs
	) {
		List<JKey> valid = new ArrayList<>();

		for (SignaturePair sp : sigMap.getSigPairList()) {
			var prefix = sp.getPubKeyPrefix().toByteArray();
			var classification = new MutableSigClassification();

			updateForPayer(payerKey, prefix, classification, sigsFn);
			updateForScheduled(prefix, valid, classification, scheduleCryptoSigs);

			if (classification.get() == INVALID_SCHEDULED_TXN_MATCH) {
				return Optional.empty();
			}
		}

		return Optional.of(valid);
	}

	private void updateForScheduled(
			byte[] prefix,
			List<JKey> valid,
			MutableSigClassification classification,
			Consumer<BiConsumer<JKey, TransactionSignature>> scheduleCryptoSigs
	) {
		scheduleCryptoSigs.accept((key, sig) -> {
			if (beginsWith(key.getEd25519(), prefix)) {
				if (sig != INVALID_MISSING_SIG) {
					if (sig.getSignatureStatus() == VALID) {
						classification.considerSetting(VALID_SCHEDULED_TXN_MATCH);
						valid.add(key);
					} else {
						classification.considerSetting(INVALID_SCHEDULED_TXN_MATCH);
					}
				}
			}
		});
	}

	private void updateForPayer(
			JKey payerKey,
			byte[] prefix,
			MutableSigClassification classification,
			Function<byte[], TransactionSignature> sigsFn
	) {
		visitSimpleKeys(payerKey, key -> {
			byte[] ed25519 = key.getEd25519();
			if (beginsWith(ed25519, prefix)) {
				var sig = sigsFn.apply(ed25519);
				if (sig != INVALID_MISSING_SIG) {
					classification.considerSetting(PAYER_MATCH);
				}
			}
		});
	}

	private static class MutableSigClassification {
		private SigClassification current = NO_MATCH;

		SigClassification get() {
			return current;
		}

		void considerSetting(SigClassification candidate) {
			if (candidate.compareTo(current) > 0) {
				current = candidate;
			}
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(MutableSigClassification.class)
					.add("current", current)
					.toString();
		}
	}
}
