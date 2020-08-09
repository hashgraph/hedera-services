package com.hedera.services.legacy.logic;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class ProtectedEntities {
  public static final long GENESIS_NUM = 2L;
  public static final long MASTER_NUM = 50L;
  public static final long ADDRESS_ACC_NUM = 55L;
  public static final long FEE_ACC_NUM = 56L;
  public static final long EXCHANGE_ACC_NUM = 57L;

  public static long ADDRESS_FILE_ACCOUNT_NUM = ApplicationConstants.ADDRESS_FILE_ACCOUNT_NUM;
  public static long NODE_DETAILS_FILE = ApplicationConstants.NODE_DETAILS_FILE;
  public static long FEE_FILE_ACCOUNT_NUM = ApplicationConstants.FEE_FILE_ACCOUNT_NUM;
  public static long EXCHANGE_RATE_FILE_ACCOUNT_NUM = ApplicationConstants.EXCHANGE_RATE_FILE_ACCOUNT_NUM;
  public static long DEFAULT_SHARD = 0L;
  public static long DEFAULT_REALM = 0L;
  public static long APPLICATION_PROPERTIES_FILE_NUM = ApplicationConstants.APPLICATION_PROPERTIES_FILE_NUM;
  public static long API_PROPERTIES_FILE_NUM = ApplicationConstants.API_PROPERTIES_FILE_NUM;

  public static AccountID genAccountID(long accNum) {
    return AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(accNum).build();
  }

  /**
   * Determine whether the given transaction is free of charge, currently the following is free:
       A/c 0.0.56 - Update Fee schedule (0.0.111) - This transaction should be FREE
       A/c 0.0.57 - Update Exchange Rate (0.0.112) - This transaction should be FREE
       Any transaction with a payer account of 50 and 2 is free
   * 
   * @param txBody body of the transaction
   * @return true if the transaction is free, false otherwise
   */
  public static boolean isFree(TransactionBody txBody) {
    boolean rv = false;
    
    AccountID payer = txBody.getTransactionID().getAccountID();
    if(isFreePayer(payer)) {
      rv = true;
    } else if(txBody.hasFileUpdate()) {
      FileID fid = txBody.getFileUpdate().getFileID();
        if((payer.equals(genAccountID(FEE_ACC_NUM)) && fid.equals(genFileID(FEE_FILE_ACCOUNT_NUM))) 
          || (payer.equals(genAccountID(EXCHANGE_ACC_NUM)) && fid.equals(genFileID(EXCHANGE_RATE_FILE_ACCOUNT_NUM)))) {
          rv = true;
        }
    } else if(txBody.hasFileAppend()) { // for large system files, append calls are necessary
      FileID fid = txBody.getFileAppend().getFileID();
        if((payer.equals(genAccountID(FEE_ACC_NUM)) && fid.equals(genFileID(FEE_FILE_ACCOUNT_NUM))) 
          || (payer.equals(genAccountID(EXCHANGE_ACC_NUM)) && fid.equals(genFileID(EXCHANGE_RATE_FILE_ACCOUNT_NUM)))) {
          rv = true;
        }
    } else {
      //NoOp
    }
  
    return rv;
  }

  /**
   * Generates a file ID in realm 0 and shard 0 based on provided file number.
   * 
   * @param fileNum file number
   * @return generated file ID
   */
  public static FileID genFileID(long fileNum) {
    return FileID.newBuilder().setShardNum(DEFAULT_SHARD).setRealmNum(DEFAULT_REALM).setFileNum(fileNum).build();
  }

  /**
   * Check if the payer is free for all transactions.
   * It has to be account 2 or 50 to be free for all transactions.
   *
   * @param payer payer to be checked
   * @return true if free, false otherwise
   */
  public static boolean isFreePayer(AccountID payer) {
    if(payer.equals(genAccountID(MASTER_NUM)) || payer.equals(genAccountID(GENESIS_NUM)))
      return true;
    else
      return false;
  }
}
