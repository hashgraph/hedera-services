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

import com.hedera.services.sigs.PlatformSigsFactory;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

@FunctionalInterface
public interface SyncActivationCheck {
	boolean allKeysAreActive(
			List<JKey> keys,
			SyncVerifier syncVerifier,
			PlatformTxnAccessor accessor,
			PlatformSigsFactory sigsFactory,
			Function<Transaction, PubKeyToSigBytes> sigBytesProvider,
			Function<byte[], TxnScopedPlatformSigFactory> scopedSigProvider,
			BiPredicate<JKey, Function<byte[], TransactionSignature>> isActive,
			Function<List<TransactionSignature>, Function<byte[], TransactionSignature>> sigsFnProvider);
}
