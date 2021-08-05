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

public enum NoopRecordsHistorian implements AccountRecordsHistorian {
  NOOP_RECORDS_HISTORIAN;

  @Override
  public void setCreator(EntityCreator creator) {
    // Do nothing because this mainly serves as placeholder for now
  }

  @Override
  public void finalizeExpirableTransactionRecord() {
    // Do nothing because this mainly serves as placeholder for now
  }

  @Override
  public void saveExpirableTransactionRecord() {
    // Do nothing because this mainly serves as placeholder for now
  }

  @Override
  public void reviewExistingRecords() {
    // Do nothing because this mainly serves as placeholder for now
  }

  @Override
  public Optional<ExpirableTxnRecord> lastCreatedRecord() { return Optional.empty(); }

  @Override
  public void noteNewExpirationEvents() {
    // Do nothing because this mainly serves as placeholder for now
  }
}
