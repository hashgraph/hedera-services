package com.hedera.services.sigs.sourcing;

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

import com.hederahashgraph.api.proto.java.Transaction;

/**
 * Defines a type that can provide {@link PubKeyToSigBytes} sources scoped
 * to entities assuming the payer and non-payer roles in a given transaction.
 *
 * @author Michael Tinker
 */
public interface PubKeyToSigBytesProvider {
	/**
	 * Get a {@link PubKeyToSigBytes} providing the cryptographic signatures
	 * for the payer of a given gRPC transaction.
	 *
	 * @param signedTxn
	 * 		the txn of interest.
	 * @return a source of the payer signatures.
	 */
	PubKeyToSigBytes payerSigBytesFor(Transaction signedTxn);

	/**
	 * Get a {@link PubKeyToSigBytes} providing the cryptographic signatures
	 * for entities involved in a non-payer role in a given gRPC transaction.
	 *
	 * @param signedTxn
	 * 		the txn of interest.
	 * @return a source of the signatures for entities in non-payer roles.
	 */
	PubKeyToSigBytes otherPartiesSigBytesFor(Transaction signedTxn);

	/**
	 * Get a {@link PubKeyToSigBytes} providing the cryptographic signatures
	 * for all entities involved in a given gRPC transaction (payer first).
	 *
	 * @param signedTxn
	 * 		the txn of interest.
	 * @return a source of the signatures for entities in all roles.
	 */
	PubKeyToSigBytes allPartiesSigBytesFor(Transaction signedTxn);
}
