package com.hedera.services.legacy.smartcontract;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.TransactionID;

/**
 * Component used by threaded TPS test classes.
 *
 * @author Peter
 */
public class FlaggedTransactionID {
  private TransactionID transactionID;
  private boolean endFlag;


  private int seq;

  public FlaggedTransactionID(TransactionID transactionID, int seq, boolean endFlag) {
    this.transactionID = transactionID;
    this.seq = seq;
    this.endFlag = endFlag;
  }

  public TransactionID getTransactionID() {
    return transactionID;
  }

  public boolean isEndFlag() {
    return endFlag;
  }

  public int getSeq() {
    return seq;
  }
}
