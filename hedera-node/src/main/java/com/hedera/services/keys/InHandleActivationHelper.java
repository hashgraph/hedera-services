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
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;

public class InHandleActivationHelper {
	private static final List<JKey> NO_OTHER_PARTIES = null;
	private static final PlatformTxnAccessor NO_LAST_ACCESSOR = null;
	private static final Function<byte[], TransactionSignature> NO_LAST_SIGS_FN = null;

	static Activation activation = HederaKeyActivation::isActive;
	static Function<
			List<TransactionSignature>,
			Function<byte[], TransactionSignature>> sigsFnSource = HederaKeyActivation::pkToSigMapFrom;

	private final HederaSigningOrder keyOrderer;
	private final CharacteristicsFactory characteristics;
	private final Supplier<PlatformTxnAccessor> accessorSource;

	private List<JKey> otherParties = NO_OTHER_PARTIES;
	private PlatformTxnAccessor accessor = NO_LAST_ACCESSOR;
	private Function<byte[], TransactionSignature> sigsFn = NO_LAST_SIGS_FN;

	public InHandleActivationHelper(
			HederaSigningOrder keyOrderer,
			CharacteristicsFactory characteristics,
			Supplier<PlatformTxnAccessor> accessorSource
	) {
		this.keyOrderer = keyOrderer;
		this.characteristics = characteristics;
		this.accessorSource = accessorSource;
	}

	public boolean areOtherPartiesActive(BiPredicate<JKey, TransactionSignature> givenTests) {
		ensureUpToDate();
		return arePartiesActive(false, accessor.getTxn(), givenTests);
	}

	public boolean areScheduledPartiesActive(
			TransactionBody scheduledTxn,
			BiPredicate<JKey, TransactionSignature> givenTests
	) {
		ensureUpToDate();
		return arePartiesActive(true, scheduledTxn, givenTests);
	}

	private boolean arePartiesActive(
			boolean useScheduleKeys,
			TransactionBody txn,
			BiPredicate<JKey, TransactionSignature> givenTests
	) {
		var activeCharacter = characteristics.inferredFor(txn);
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

	private void ensureUpToDate() {
		var current = accessorSource.get();
		if (accessor != current) {
			var otherOrderingResult = keyOrderer.keysForOtherParties(current.getTxn(), IN_HANDLE_SUMMARY_FACTORY);
			if (otherOrderingResult.hasErrorReport()) {
				throw new AssertionError("Not implemented!");
			}
			otherParties = otherOrderingResult.getOrderedKeys();
			sigsFn = sigsFnSource.apply(current.getPlatformTxn().getSignatures());
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
}
