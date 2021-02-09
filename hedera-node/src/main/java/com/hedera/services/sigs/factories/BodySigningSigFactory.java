package com.hedera.services.sigs.factories;

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

import com.google.protobuf.ByteString;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;

/**
 * A trivial convenience implementation of a {@link TxnScopedPlatformSigFactory} that
 * creates {@link com.swirlds.common.crypto.TransactionSignature} objects representing ed25519 sigs
 * of the body bytes for a gRPC transaction.
 *
 * @author Michael Tinker
 */
public class BodySigningSigFactory implements TxnScopedPlatformSigFactory {
	private final TxnAccessor accessor;

	public BodySigningSigFactory(TxnAccessor accessor) {
		this.accessor = accessor;
	}

	@Override
	public TransactionSignature create(ByteString publicKey, ByteString sigBytes) {
		return PlatformSigFactory.createEd25519(
				publicKey.toByteArray(),
				sigBytes.toByteArray(),
				accessor.getTxnBytes());
	}
}
