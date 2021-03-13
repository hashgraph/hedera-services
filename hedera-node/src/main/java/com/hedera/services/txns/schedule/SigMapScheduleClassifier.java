package com.hedera.services.txns.schedule;

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

public class SigMapScheduleClassifier {
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
	}
}
