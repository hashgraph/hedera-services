package com.hedera.services.ledger.accounts;

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


/**
 * Defines a type that provides safe and unsafe access to a collection
 * of accounts. ("Safe" implies a defensive copy is returned from the
 * accessor, while "unsafe" implies a reference to the element in the
 * underlying collection is returned).
 *
 * @param <K> the type of id used to index the collection.
 * @param <A> the type of account stored in the collection.
 *
 * @author Michael Tinker
 */
public interface BackingAccounts<K, A> {
	/**
	 * Gets a reference to the actual account with the specified id. This reference
	 * should not be considered safe to mutate.
	 *
	 * @param id the id of the relevant account.
	 * @return a reference to the account.
	 */
	A getUnsafeRef(K id);

	/**
	 * Gets a mutable reference to the actual account with the specified id.
	 *
	 * @param id the id of the relevant account.
	 * @return a reference to the account.
	 */
	A getMutableRef(K id);

	/**
	 * Updates (or creates, if absent) the account with the given id
	 * to the accompanying account.
	 *
	 * @param id the id of the relevant account.
	 * @param account the account that should have this id.
	 */
	void replace(K id, A account);

	/**
	 * Frees the account with the given id for reclamation.
	 *
	 * @param id the id of the relevant account.
	 */
	void remove(K id);

	/**
	 * Checks if the collection contains the account with the given id.
	 *
	 * @param id
	 * @return
	 */
	boolean contains(K id);
}
