package com.hedera.services.records;

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

import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Defines a type able to manage the history of transactions
 * funded by accounts on the injected ledger. (Note that these
 * transactions may not be directly <b>about</b> the ledger, but
 * instead a file or smart contract.)
 *
 * The definitive history is represented by {@link ExpirableTxnRecord}
 * instances, which expire at regular intervals and are stored in
 * the ledger accounts themselves.
 *
 * Note this type is implicitly assumed to have access to the context
 * of the active transaction, which is somewhat confusing and will be
 * addressed in a future refactor.
 */
public interface AccountRecordsHistorian {
	/**
	 * Injects the expiring entity creator which the historian should use to create records.
	 *
	 * @param creator the creator of expiring entities.
	 */
	void setCreator(EntityCreator creator);

	/**
	 * Called immediately before committing the active transaction to finalize its record(s)
	 * of the executed business logic.
	 */
	void finalizeExpirableTransactionRecords();

	/**
	 * Called immediately after committing the active transaction, to save its record(s) in
	 * the account of the effective payer account of the committed transaction.
	 */
	void saveExpirableTransactionRecords();

	/**
	 * Returns the last record created by this historian, if it is known, and null otherwise.
	 *
	 * @return the last-created record, or null if none is known
	 */
	ExpirableTxnRecord lastCreatedTopLevelRecord();

	/**
	 * Indicates if the active transaction created child records.
	 *
	 * @return whether child records were created
	 */
	boolean hasChildRecords();

	/**
	 * Returns all the child records created by the active transaction.
	 *
	 * @return the created child records
	 */
	List<RecordStreamObject> getChildRecords();

	/**
	 * Returns a non-negative "source id" to be used to create a group of in-progress child transactions.
	 *
	 * @return the next source id
	 */
	int nextChildRecordSourceId();

	/**
	 * Adds the given in-progress child record to the active transaction.
	 *
	 * @param sourceId the id of the child record source
	 * @param recordSoFar the in-progress child record
	 */
	void trackChildRecord(int sourceId, Pair<ExpirableTxnRecord.Builder, Transaction> recordSoFar);

	/**
	 * Reverts all records created by the given source.
	 *
	 * @param sourceId the id of the source whose records should be reverted
	 */
	void revertChildRecordsFromSource(int sourceId);

	/**
	 * At the moment before committing the active transaction, takes the opportunity to track
	 * any new expiring entities with the {@link com.hedera.services.state.expiry.ExpiryManager}.
	 */
	void noteNewExpirationEvents();
}
