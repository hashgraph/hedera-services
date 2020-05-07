package com.hedera.services.sigs;

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
import com.hedera.services.keys.HederaKeyTraversal;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.legacy.core.jproto.JKey;

import java.util.List;

/**
 * Provides static methods to work with Swirlds {@link com.swirlds.common.crypto.Signature} objects.
 *
 * @author Michael Tinker
 */
public class PlatformSigOps {

	private PlatformSigOps(){
		throw new IllegalStateException("Utility Class");
	}
	/**
	 * Return the result of trying to create one or more Swirlds platform sigs using a given
	 * {@link TxnScopedPlatformSigFactory}, where this {@code factory} should be invoked for
	 * each public key in a left-to-right DFS traversal of the simple keys from a list of
	 * Hedera keys, using signature bytes from a given {@link PubKeyToSigBytes}.
	 *
	 * @param pubKeys
	 * 		a list of Hedera keys to traverse for public keys.
	 * @param sigBytes
	 * 		a source of cryptographic signatures to associate to the public keys.
	 * @param factory
	 * 		a factory to convert public keys and cryptographic sigs into Swirlds sigs.
	 * @return the result of attempting this creation.
	 */
	public static PlatformSigsCreationResult createEd25519PlatformSigsFrom(
			List<JKey> pubKeys,
			PubKeyToSigBytes sigBytes,
			TxnScopedPlatformSigFactory factory
	) {
		PlatformSigsCreationResult result = new PlatformSigsCreationResult();
		for (JKey pk : pubKeys) {
			HederaKeyTraversal.visitSimpleKeys(pk, simpleKey -> createPlatformSigFor(simpleKey, sigBytes, factory, result));
		}
		return result;
	}

	private static void createPlatformSigFor(JKey pk,
											 PubKeyToSigBytes sigBytes,
											 TxnScopedPlatformSigFactory factory,
											 PlatformSigsCreationResult result
	){
		if (result.hasFailed()) {
			return;
		}

		try {
			ByteString ed25519PubKey = ByteString.copyFrom(pk.getEd25519());
			ByteString sig = ByteString.copyFrom(sigBytes.sigBytesFor(ed25519PubKey.toByteArray()));

			if (!sig.isEmpty()) {
				result.getPlatformSigs().add(factory.create(ed25519PubKey, sig));
			}
		} catch (Exception e) {
			result.setTerminatingEx(e);
		}
	}
}
