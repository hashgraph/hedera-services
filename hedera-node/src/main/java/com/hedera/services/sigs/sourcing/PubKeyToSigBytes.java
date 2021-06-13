package com.hedera.services.sigs.sourcing;

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

import com.hederahashgraph.api.proto.java.SignatureMap;

/**
 * Defines a type that is a source of the cryptographic signatures associated to
 * given public keys. It is useful to define an explicit type for this simple behavior,
 * because to create a {@link com.swirlds.common.crypto.Signature}, you must have:
 * <ol>
 *     <li>The raw data that was signed.</li>
 *     <li>The public key matching the private key used to sign the data.</li>
 *     <li>The cryptographic signature that resulted.</li>
 * </ol>
 * A {@code PubKeyToSigBytes} implementation lets us obtain the third ingredient
 * given the second.
 *
 * <b>NOTE:</b> This interface also provides static factories to obtain appropriate
 * implementations of its type given a {@link SignatureMap}.
 *
 * @author Michael Tinker
 */
public interface PubKeyToSigBytes {
	byte[] EMPTY_SIG = {};

	/**
	 * Return the cryptographic signature associated to a given public key in some
	 * context (presumably the creation of a {@link com.swirlds.common.crypto.Signature}).
	 *
 	 * @param pubKey a public key whose private key was used to sign some data.
	 * @return the cryptographic signature that resulted.
	 * @throws Exception if the desired cryptographic signature is unavailable.
	 */
	byte[] sigBytesFor(byte[] pubKey) throws Exception;
}
