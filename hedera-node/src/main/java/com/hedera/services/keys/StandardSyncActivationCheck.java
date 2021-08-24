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
import com.hedera.services.sigs.PlatformSigsFactory;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class StandardSyncActivationCheck {
	StandardSyncActivationCheck() {
		throw new IllegalStateException("Utility Class");
	}

	public static boolean allKeysAreActive(
			final List<JKey> keys,
			final SyncVerifier syncVerifier,
			final TxnAccessor accessor,
			final PlatformSigsFactory sigsFactory,
			final PubKeyToSigBytes sigBytes,
			final Function<TxnAccessor, TxnScopedPlatformSigFactory> scopedSigProvider,
			final BiPredicate<JKey, Function<byte[], TransactionSignature>> isActive,
			final Function<List<TransactionSignature>, Function<byte[], TransactionSignature>> sigsFnProvider
	) {
		final var sigFactory = scopedSigProvider.apply(accessor);

		final var creationResult = sigsFactory.createEd25519From(keys, sigBytes, sigFactory);
		if (creationResult.hasFailed()) {
			return false;
		}
		final var sigs = creationResult.getPlatformSigs();

		syncVerifier.verifySync(sigs);

		final var sigsFn = sigsFnProvider.apply(sigs);
		for (final var key : keys) {
			if (!isActive.test(key, sigsFn)) {
				return false;
			}
		}
		return true;
	}
}
