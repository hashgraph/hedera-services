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

import com.hedera.services.legacy.config.PropertiesLoader;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.LongPredicate;

/**
 * Utilities for protected entities, i.e. those with ID below 1000.
 * 
 * @author Hua Li Created on 2019-07-31
 */
public class ProtectedEntities {
  public static final long MAX_PROTECTED_ENTITY_NUM = 1000;
  public static final long MIN_PROTECTED_ENTITY_NUM = 1;
  public static final long GENESIS_NUM = 2L;
  public static final long MASTER_NUM = 50L;
  public static final long ADDRESS_ACC_NUM = 55L;
  public static final long FEE_ACC_NUM = 56L;
  public static final long EXCHANGE_ACC_NUM = 57L;
  public static final long FREEZE_ACC_NUM = 58L;
  public static final long SYSTEM_DELETE_ACC_NUM = 59L;
  public static final long SYSTEM_UNDELETE_ACC_NUM = 60L;
  
  public static long ADDRESS_FILE_ACCOUNT_NUM = ApplicationConstants.ADDRESS_FILE_ACCOUNT_NUM;
  public static long NODE_DETAILS_FILE = ApplicationConstants.NODE_DETAILS_FILE;
  public static long FEE_FILE_ACCOUNT_NUM = ApplicationConstants.FEE_FILE_ACCOUNT_NUM;
  public static long EXCHANGE_RATE_FILE_ACCOUNT_NUM = ApplicationConstants.EXCHANGE_RATE_FILE_ACCOUNT_NUM;
  public static long DEFAULT_SHARD = 0L;
  public static long DEFAULT_REALM = 0L;
  public static long APPLICATION_PROPERTIES_FILE_NUM = ApplicationConstants.APPLICATION_PROPERTIES_FILE_NUM;
  public static long API_PROPERTIES_FILE_NUM = ApplicationConstants.API_PROPERTIES_FILE_NUM;

  public static final LongPredicate IS_PROPERTIES_OR_PERMISSIONS = num ->
          (num == APPLICATION_PROPERTIES_FILE_NUM) || (num == API_PROPERTIES_FILE_NUM);

  public static boolean isMasterAccount(AccountID account) {
    return (account.getShardNum() == DEFAULT_SHARD && account.getRealmNum() == DEFAULT_REALM
        && account.getAccountNum() == MASTER_NUM);
  }

  /**
   * Checks if the entity is protected, i.e. with ID number below 1000.
   * 
   * @param entityID the ID of the entity
   * @return true if the entity should be protected, false otherwise
   */
  public static boolean isProtectedEntity(Object entityID) {
    long entityNum = -1l;
    long realmNum = -1l;
    long shardNum = -1l;
    if (entityID instanceof AccountID) {
      AccountID accID = (AccountID) entityID;
      entityNum = accID.getAccountNum();
      realmNum = accID.getRealmNum();
      shardNum = accID.getShardNum();
    } else if (entityID instanceof FileID) {
      FileID fileID = (FileID) entityID;
      entityNum = fileID.getFileNum();
      realmNum = fileID.getRealmNum();
      shardNum = fileID.getShardNum();
    } else {
      ContractID contratID = (ContractID) entityID;
      entityNum = contratID.getContractNum();
      realmNum = contratID.getRealmNum();
      shardNum = contratID.getShardNum();
    }

    boolean rv = (shardNum == DEFAULT_SHARD) && (realmNum == DEFAULT_REALM) && (entityNum >= MIN_PROTECTED_ENTITY_NUM)
        && (entityNum <= MAX_PROTECTED_ENTITY_NUM);
    return rv;
  }

  /**
   * Checks if an account is treasury.
   * 
   * @param account account to be checked
   * @return true if the account is treasury, false otherwise
   */
  public static boolean isTreasury(AccountID account) {
    return (account.getShardNum() == DEFAULT_SHARD && account.getRealmNum() == DEFAULT_REALM
        && account.getAccountNum() == GENESIS_NUM);
  }


  /**
   * Generates an account ID in realm 0 and shard 0 based on provided account number.
   * 
   * @param accNum account number
   * @return generated account ID
   */
  public static AccountID genAccountID(long accNum) {
    return AccountID.newBuilder().setShardNum(DEFAULT_SHARD).setRealmNum(DEFAULT_REALM).setAccountNum(accNum).build();
  }

  /**
   * Gets the sequence number of an entity.
   * 
   * @param entity the target entity
   * @return the sequence number of the entity
   */
  public static long getEntityNumber(Object entity) {
    long rv = -1l;

    if (entity instanceof AccountID) {
      rv = ((AccountID) entity).getAccountNum();
    } else if (entity instanceof FileID) {
      rv = ((FileID) entity).getFileNum();
    } else { // contract ID
      rv = ((ContractID) entity).getContractNum();
    }

    return rv;
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
  
  /**
   * At the moment only items listed below do not need the WACL validated only the relevant accounts need to be signing the transactions.
      2 x Address book - Accounts 55 & 50
      Fee Schedule - Account 56 & 50
      Exchange Rate - Account 57 & 50
      System Deleted & Undelete - Accounts 59 & 60
   * @param body body of transaction to be checked
   * @return trie of wacl should sign, false otherwisse
   */
  public static boolean shouldWaclSign(TransactionBody body) {
    boolean rv = true;
    AccountID payerID = body.getTransactionID().getAccountID();
    if(body.hasFileUpdate() || body.hasFileAppend()) {
      FileID fileID;
      if(body.hasFileUpdate()) {
        fileID = body.getFileUpdate().getFileID();
      } else {
        fileID = body.getFileAppend().getFileID();
      }
        
      long fid = fileID.getFileNum();
      long payer = payerID.getAccountNum();
      
      if((fid == ADDRESS_FILE_ACCOUNT_NUM || fid == NODE_DETAILS_FILE) && (payer == ADDRESS_ACC_NUM || payer == MASTER_NUM)) {
        rv = false;
      } else if((fid == FEE_FILE_ACCOUNT_NUM) && (payer == FEE_ACC_NUM || payer == MASTER_NUM)) {
        rv = false;
      } else if((fid == EXCHANGE_RATE_FILE_ACCOUNT_NUM) && (payer == EXCHANGE_ACC_NUM || payer == MASTER_NUM)) {
        rv = false;
      } else if((fid == ADDRESS_ACC_NUM) && (payer == ADDRESS_FILE_ACCOUNT_NUM || payer == MASTER_NUM)) {
        rv = false;
      } else if ((payer == ADDRESS_ACC_NUM) && IS_PROPERTIES_OR_PERMISSIONS.test(fid)) {
        rv = false;
      }
    } else if(body.hasSystemDelete()) {
      long fid = body.getSystemDelete().getFileID().getFileNum();     
      if(fid == SYSTEM_DELETE_ACC_NUM) {
        rv = false;
      }
    } else if(body.hasSystemUndelete()) {
      long fid = body.getSystemUndelete().getFileID().getFileNum();     
      if(fid == SYSTEM_UNDELETE_ACC_NUM) {
        rv = false;
      }
    } else {
      //NoOp
    }
    
    return rv;
  }

  public static boolean shouldExistingAccountSign(AccountID payerAccount, AccountID acctId) {
    if (!isProtectedEntity(acctId)) {
      return true;
    }

    if (isTreasury(payerAccount) && !isTreasury(acctId)) {
      return false;
    }

    boolean rv = true;
    long seq = getEntityNumber(acctId);
    if (isMasterAccount(payerAccount) && !isMasterAccount(acctId)) {
      if (seq >= 50 && seq <= 80) {
        rv = false;
      }
    } 
    
    return rv;
  }
}
