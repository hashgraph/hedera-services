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
import java.util.Optional;

/**
 * Defines a type able to manage the history of transactions funded by accounts on the injected
 * ledger. (Note that these transactions may not be directly <b>about</b> the ledger, but instead a
 * file or smart contract.)
 *
 * <p>The definitive history is represented by {@link ExpirableTxnRecord} instances, which expire at
 * regular intervals and are stored in the ledger accounts themselves.
 *
 * <p>Note this type is implicitly assumed to have access to the context of the active transaction,
 * which is somewhat confusing and will be addressed in a future refactor.
 *
 * @author Michael Tinker
 */
public interface AccountRecordsHistorian {
  /**
   * Injects the expiring entity creator which the historian should use to create records.
   *
   * @param creator the creator of expiring entities.
   */
  void setCreator(EntityCreator creator);

  /**
   * Called immediately before committing the active transaction to finalize the record of the
   * executed business logic.
   */
  void finalizeExpirableTransactionRecord();

  /**
   * Called immediately after committing the active transaction, to save the record (e.g. in the
   * payer account of the committed transaction.)
   */
  void saveExpirableTransactionRecord();

  /**
   * Returns the last record created, if it exists.
   *
   * @return an optional record.
   */
  Optional<ExpirableTxnRecord> lastCreatedRecord();

  /**
   * At the moment before committing the active transaction, checks if Transaction Context has any
   * existing expiring entities and if so, tracks them using {@link
   * com.hedera.services.state.expiry.ExpiryManager}
   */
  void noteNewExpirationEvents();
}
