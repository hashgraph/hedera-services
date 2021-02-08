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
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.keys.HederaKeyTraversal.visitSimpleKeys;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;

public class InHandleActivationHelper {
	private static final Logger log = LogManager.getLogger(InHandleActivationHelper.class);

	private static final List<JKey> NO_OTHER_PARTIES = null;
	private static final TxnAccessor NO_LAST_ACCESSOR = null;
	private static final Function<byte[], TransactionSignature> NO_LAST_SIGS_FN = null;

	static Activation activation = HederaKeyActivation::isActive;
	static Function<
			List<TransactionSignature>,
			Function<byte[], TransactionSignature>> sigsFnSource = HederaKeyActivation::pkToSigMapFrom;
	static PkToSigMapFactory scopedSigsFnSource = HederaKeyActivation::matchingPkToSigMapFrom;

	private final HederaSigningOrder keyOrderer;
	private final CharacteristicsFactory characteristics;
	private final Supplier<TxnAccessor> accessorSource;

	private List<JKey> otherParties = NO_OTHER_PARTIES;
	private TxnAccessor accessor = NO_LAST_ACCESSOR;
	private Function<byte[], TransactionSignature> scheduledSigsFn = NO_LAST_SIGS_FN;
	private Function<byte[], TransactionSignature> nonScheduledSigsFn = NO_LAST_SIGS_FN;

	public InHandleActivationHelper(
			HederaSigningOrder keyOrderer,
			CharacteristicsFactory characteristics,
			Supplier<TxnAccessor> accessorSource
	) {
		this.keyOrderer = keyOrderer;
		this.characteristics = characteristics;
		this.accessorSource = accessorSource;
	}

	public boolean areOtherPartiesActive(BiPredicate<JKey, TransactionSignature> tests) {
		ensureUpToDate();
		return arePartiesActive(false, accessor.getTxn(), tests);
	}

	public boolean areScheduledPartiesActive(
			TransactionBody scheduledTxn,
			BiPredicate<JKey, TransactionSignature> tests
	) {
		ensureUpToDate();
		ensureScheduledSigsFnPresent();
		return arePartiesActive(true, scheduledTxn, tests);
	}

	public void visitScheduledCryptoSigs(BiConsumer<JKey, TransactionSignature> visitor) {
		ensureUpToDate();
		ensureScheduledSigsFnPresent();
		for (JKey req : otherParties) {
			if (req.isForScheduledTxn()) {
				visitSimpleKeys(req, key -> visitor.accept(key, scheduledSigsFn.apply(key.getEd25519())));
			}
		}
	}

	private boolean arePartiesActive(
			boolean useScheduleKeys,
			TransactionBody txn,
			BiPredicate<JKey, TransactionSignature> givenTests
	) {
		var activeCharacter = characteristics.inferredFor(txn);
		Function<byte[], TransactionSignature> sigsFn = useScheduleKeys ? scheduledSigsFn : nonScheduledSigsFn;
		for (JKey req : otherParties) {
			if (req.isForScheduledTxn() != useScheduleKeys) {
				continue;
			}
			if (!activation.test(req, sigsFn, givenTests, activeCharacter)) {
				return false;
			}
		}
		return true;
	}

	private void ensureScheduledSigsFnPresent() {
		if (scheduledSigsFn == NO_LAST_SIGS_FN) {
			throw new IllegalStateException("No scheduled sigs function available!");
		}
	}

	private void ensureUpToDate() {
		var current = accessorSource.get();
		if (accessor != current) {
			var otherOrderingResult = keyOrderer.keysForOtherParties(current.getTxn(), IN_HANDLE_SUMMARY_FACTORY);
			if (otherOrderingResult.hasErrorReport()) {
				var errorReport = otherOrderingResult.getErrorReport();
				log.warn("Allowing active other-party sigs: {} ({})!", errorReport, errorReport.getResponseCode());
				otherParties = Collections.emptyList();
			} else {
				otherParties = otherOrderingResult.getOrderedKeys();
			}

			var sigs = current.getPlatformTxn().getSignatures();
			switch (current.getFunction()) {
				case ScheduleSign:
				case ScheduleCreate:
					var scopedTxnBytes = current.getTxnBytes();
					nonScheduledSigsFn = scopedSigsFnSource.get(scopedTxnBytes, true, sigs);
					scheduledSigsFn = scopedSigsFnSource.get(scopedTxnBytes, false, sigs);
					break;
				default:
					scheduledSigsFn = NO_LAST_SIGS_FN;
					nonScheduledSigsFn = sigsFnSource.apply(sigs);
			}
			accessor = current;
		}
	}

	@FunctionalInterface
	interface Activation {
		boolean test(
				JKey key,
				Function<byte[], TransactionSignature> sigsFn,
				BiPredicate<JKey, TransactionSignature> tests,
				KeyActivationCharacteristics characteristics);
	}

	@FunctionalInterface
	interface PkToSigMapFactory {
		Function<byte[], TransactionSignature> get(byte[] msg, boolean shouldMatch, List<TransactionSignature> sigs);
	}
}
