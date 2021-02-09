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

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;

import java.util.List;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractSignatureMapOrUseDefault;

/**
 * Defines a type that is a source of the cryptographic signatures associated to
 * given public keys. It is useful to define an explicit type for this simple behavior,
 * because it must be implemented quite differently for a gRPC transaction using a
 * {@link SignatureMap} vs. a gRPC transaction using a
 * {@link com.hederahashgraph.api.proto.java.SignatureList}.
 *
 * In particular, to create a {@link com.swirlds.common.crypto.Signature}, you must have:
 * <ol>
 *     <li>The raw data that was signed.</li>
 *     <li>The public key matching the private key used to sign the data.</li>
 *     <li>The cryptographic signature that resulted.</li>
 * </ol>
 * A {@code PubKeyToSigBytes} implementation lets us obtain the third ingredient
 * given the second.
 *
 * <b>NOTE:</b> This interface also provides static factories to obtain appropriate
 * implementations of its type given {@link SignatureMap},
 * {@link com.hederahashgraph.api.proto.java.SignatureList}, or gRPC transaction.
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

	/**
	 * Return the cryptographic signature associated to a given public key relative
	 * to a scheduled transaction linked to some context (presumably the creation of
	 * a {@link com.swirlds.common.crypto.Signature}).
	 *
	 * @param pubKey a public key whose private key was used to sign some data.
	 * @return the cryptographic signature that resulted.
	 * @throws Exception if the desired cryptographic signature is unavailable.
	 */
	default byte[] sigBytesForScheduled(byte[] pubKey) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Create a {@code PubKeyToSigBytes} implementation backed by the given map.
	 *
	 * @param sigMap a list of public-key-to-cryptographic-signature map entries.
	 * @return a source of raw signatures that encapsulates this mapping.
	 */
	static PubKeyToSigBytes from(SignatureMap sigMap) {
		return new SigMapPubKeyToSigBytes(sigMap);
	}

	/**
	 * Create a {@code PubKeyToSigBytes} implementation backed by the [implied]
	 * list of cryptographic signatures contained in a list of Hedera
	 * {@link Signature} instances.
	 *
	 * @param hederaSigs a list of Hedera {@link Signature} objects.
	 * @return a source of the raw signatures contained in the Hedera signatures.
	 */
	static PubKeyToSigBytes from(List<Signature> hederaSigs) {
		return new SigListPubKeyToSigBytes(hederaSigs);
	}

	/**
	 * Create a {@code PubKeyToSigBytes} implementation backed by the cryptographic
	 * signatures associated to the payer of a given gRPC transaction.
	 *
	 * @param signedTxn a gRPC transaction.
	 * @return a source of the raw signatures associated to the payer for the txn.
	 */
	static PubKeyToSigBytes forPayer(Transaction signedTxn) {
		return from(extractSignatureMapOrUseDefault(signedTxn));
	}

	/**
	 * Create a {@code PubKeyToSigBytes} implementation backed by the cryptographic
	 * signatures associated to entities involved in non-payer roles for a given
	 * gRPC transaction.
	 *
	 * @param signedTxn a gRPC transaction.
	 * @return a source of the raw signatures associated non-payer roles in the txn.
	 */
	static PubKeyToSigBytes forOtherParties(Transaction signedTxn) {
		return forPayer(signedTxn);
	}

	/**
	 * Create a {@code PubKeyToSigBytes} implementation backed by the cryptographic
	 * signatures associated to entities involved in non-payer roles for a given
	 * gRPC transaction.
	 *
	 * @param signedTxn a gRPC transaction.
	 * @return a source of the raw signatures associated non-payer roles in the txn.
	 */
	static PubKeyToSigBytes forAllParties(Transaction signedTxn) {
		return forPayer(signedTxn);
	}
}
