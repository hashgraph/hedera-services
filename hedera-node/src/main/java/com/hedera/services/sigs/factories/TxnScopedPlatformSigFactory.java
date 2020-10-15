package com.hedera.services.sigs.factories;

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
import com.swirlds.common.crypto.TransactionSignature;

/**
 * Defines a type of {@link com.swirlds.common.crypto.TransactionSignature} factory that does not
 * require the {@code byte[]} data to sign, because it is assumed to be scoped
 * to a gRPC transaction.
 *
 * @author Michael Tinker
 */
public interface TxnScopedPlatformSigFactory {
	/**
	 * Returns a {@link com.swirlds.common.crypto.TransactionSignature} on the scoped transaction.
	 *
	 * @param publicKey
	 * 		the public key to use in creating the platform sig.
	 * @param sigBytes
	 * 		the cryptographic signature to use in creating the platform sig.
	 * @return a platform sig for the scoped transaction.
	 */
	TransactionSignature create(ByteString publicKey, ByteString sigBytes);
}
