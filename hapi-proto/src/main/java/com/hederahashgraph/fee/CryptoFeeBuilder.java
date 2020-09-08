package com.hederahashgraph.fee;

/*-
 * ‌
 * Hedera Services API
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

import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.exception.InvalidTxBodyException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;


/**
 * This class includes methods for generating Fee Matrices and calculating Fee for Crypto related
 * Transactions and Query.
 */

public class CryptoFeeBuilder extends FeeBuilder {


  /**
   * This method returns the Fee Matrices for Crypto Create Transaction.
   */
  public FeeData getCryptoCreateTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasCryptoCreateAccount()) {
      throw new InvalidTxBodyException("CryptoCreate Tx Body not available for Fee Calculation");
    }
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    int txBodySize = getCommonTransactionBodyBytes(txBody);
    int cryptoCreateSize = getCryptoCreateAccountBodyTxSize(txBody);
    bpt = txBodySize + cryptoCreateSize + sigValObj.getSignatureSize();
    vpt = sigValObj.getTotalSigCount();
    rbs = getCryptoRBS(txBody, cryptoCreateSize)
        + getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC; // TxRecord
    long rbsNetwork = getDefaultRBHNetworkSize() + BASIC_ENTITY_ID_SIZE * (RECIEPT_STORAGE_TIME_SEC);

    bpr = INT_SIZE;

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
  }

  /**
   * This method returns the Fee Matrices for Crypto Delete Transaction.
   */
  public FeeData getCryptoDeleteTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    /**
     * accountIDToDeleteFrom AccountID The account ID that should have a claim deleted
     *
     * hashToDelete bytes The hash in the claim to delete (a SHA-384 hash, 48 bytes)
     */

    if (txBody == null || !txBody.hasCryptoDelete()) {
      throw new InvalidTxBodyException("CryptoCreate Tx Body not available for Fee Calculation");
    }

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    int txBodySize = 0;

    bpr = INT_SIZE;
    txBodySize = getCommonTransactionBodyBytes(txBody);
    bpt = txBodySize + 2 * BASIC_ENTITY_ID_SIZE + sigValObj.getSignatureSize();
    vpt = sigValObj.getTotalSigCount();
    // TxRecord
    rbs = getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;
    long rbsNetwork = getDefaultRBHNetworkSize();
    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
  }


  /**
   * This method returns the Fee Matrices for Crypto Transfer Transaction.
   */
  public FeeData getCryptoTransferTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasCryptoTransfer()) {
      throw new InvalidTxBodyException("CryptoTransfer Tx Body not available for Fee Calculation");
    }

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);

    // bpt - Bytes per Transaction
    bpt = txBodySize + getCryptoTransferBodyTxSize(txBody) + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    // tv - Transfer Value
    // tv = getTV(txBody);

    bpr = INT_SIZE;

    rbs = getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;

    long rbsNetwork = getDefaultRBHNetworkSize();

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return  getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
  }

  /**
   * This method returns the Fee Matrices for Crypto Update Transaction
   */
  public FeeData getCryptoUpdateTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj,
      Timestamp expirationTimeStamp, Key existingKey) throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasCryptoUpdateAccount()) {
      throw new InvalidTxBodyException("CryptoUpdate Tx Body not available for Fee Calculation");
    }

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    CryptoUpdateTransactionBody crUpdateTxBody = txBody.getCryptoUpdateAccount();
    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);

    // bpt - Bytes per Transaction
    bpt = txBodySize + getCryptoUpdateBodyTxSize(txBody) +  sigValObj.getSignatureSize();

    rbs = getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();
    long rbsNetwork = getDefaultRBHNetworkSize();

    if(expirationTimeStamp != null && expirationTimeStamp.getSeconds() > 0) {
	    if (crUpdateTxBody.hasExpirationTime()) {
	      if (crUpdateTxBody.getExpirationTime().getSeconds() > expirationTimeStamp.getSeconds()) {
	        if (crUpdateTxBody.hasKey()) {
	          rbs = rbs + (BASIC_ACCOUNT_SIZE + getAccountKeyStorageSize(crUpdateTxBody.getKey()))
	              * (crUpdateTxBody.getExpirationTime().getSeconds()
	                  - expirationTimeStamp.getSeconds());
	        } else {
	          rbs = rbs + (BASIC_ACCOUNT_SIZE + getAccountKeyStorageSize(existingKey))
	              * (crUpdateTxBody.getExpirationTime().getSeconds()
	                  - expirationTimeStamp.getSeconds());
	        }
	      } else {
	        if (crUpdateTxBody.hasKey()) {
	          int newKeySize = getAccountKeyStorageSize(crUpdateTxBody.getKey());
	          int existingKeySize = getAccountKeyStorageSize(existingKey);
	          if (newKeySize > existingKeySize) {
	            Instant expirationTime = RequestBuilder.convertProtoTimeStamp(expirationTimeStamp);
	            Timestamp txValidStartTimestamp = txBody.getTransactionID().getTransactionValidStart();
	            Instant txValidStartTime = RequestBuilder.convertProtoTimeStamp(txValidStartTimestamp);
	            Duration duration = Duration.between(txValidStartTime, expirationTime);
	            long seconds = duration.getSeconds();
	            rbs = rbs + newKeySize * seconds;
	          }
	        }
	      }
	    }
    }

   

    bpr = INT_SIZE;

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
  }

  /**
   * This method calculated total bytes in Crypto Update Tx body
   */
  private int getCryptoUpdateBodyTxSize(TransactionBody txBody) {
    /*
     * AccountID accountIDToUpdate - BASIC_ENTITY_ID_SIZE Key - calculated bytes AccountID proxyAccountID -
     * BASIC_ENTITY_ID_SIZE google.protobuf.UInt64Value sendRecordThreshold - LONG_SIZE
     * google.protobuf.UInt64Value receiveRecordThreshold - LONG_SIZE Duration autoRenewPeriod -
     * (LONG_SIZE + INT_SIZE) Timestamp expirationTime - (LONG_SIZE + INT_SIZE) bytes
     * google.protobuf.BoolValue receiverSigRequired - BOOL_VALUE
     */

    int cryptoAcctUpdateBodySize = BASIC_ENTITY_ID_SIZE;

    CryptoUpdateTransactionBody crUpdateTxBody = txBody.getCryptoUpdateAccount();

    if (crUpdateTxBody.hasKey()) {
      cryptoAcctUpdateBodySize += getAccountKeyStorageSize(crUpdateTxBody.getKey());
    }

    if (crUpdateTxBody.hasProxyAccountID()) {
      cryptoAcctUpdateBodySize += (BASIC_ENTITY_ID_SIZE);
    }
    if (crUpdateTxBody.getSendRecordThreshold() != 0
        || crUpdateTxBody.hasSendRecordThresholdWrapper()) {
      cryptoAcctUpdateBodySize += LONG_SIZE;
    }

    if (crUpdateTxBody.getReceiveRecordThreshold() != 0
        || crUpdateTxBody.hasReceiveRecordThresholdWrapper()) {
      cryptoAcctUpdateBodySize += LONG_SIZE;
    }

    if (crUpdateTxBody.hasAutoRenewPeriod()) {
      cryptoAcctUpdateBodySize += (LONG_SIZE);
    }

    if (crUpdateTxBody.hasExpirationTime()) {
      cryptoAcctUpdateBodySize += (LONG_SIZE);
    }

    if (crUpdateTxBody.hasReceiverSigRequiredWrapper()
        || crUpdateTxBody.getReceiverSigRequired() == true) {
      cryptoAcctUpdateBodySize += BOOL_SIZE;
    }

    return cryptoAcctUpdateBodySize;

  }

  /**
   * This method calculates total total RAM Bytes (product of total bytes that will be stored in
   * memory and time till account expires)
   */
  private long getCryptoRBS(TransactionBody txBody, int crCreateSize) {
    // Number of bytes stored in memory
    /*
     * AccountID => 3 LONG_SIZE long balance - LONG_SIZE long receiverThreshold - LONG_SIZE long
     * senderThreshold - LONG_SIZE boolean receiverSigRequired - BOOL_SIZE Key accountKeys -
     * calculated size AccountID proxyAccount - BASIC_ENTITY_ID_SIZE long autoRenewPeriod - LONG_SIZE
     * boolean deleted - BOOL_SIZE
     */

    long rbsSize = 0;
    long seconds = 0;
    if (txBody.hasCryptoCreateAccount()) {
      CryptoCreateTransactionBody cryptoCreate = txBody.getCryptoCreateAccount();
      if (cryptoCreate.hasAutoRenewPeriod()) {
        seconds = cryptoCreate.getAutoRenewPeriod().getSeconds();
      }
      rbsSize = (crCreateSize) * seconds;
    }
    return rbsSize;
  }

  /**
   * This method returns the total bytes in Crypto Transaction body
   */
  private int getCryptoCreateAccountBodyTxSize(TransactionBody txBody) {
    /*
     * Key key - calculated value , uint64 initialBalance - LONG_SIZE, AccountID proxyAccountID - 3
     * * LONG_SIZE, uint64 sendRecordThreshold - LONG_SIZE, uint64 receiveRecordThreshold -
     * LONG_SIZE, bool receiverSigRequired - BOOL_SIZE, Duration autoRenewPeriod - (LONG_SIZE),
     * ShardID shardID - LONG_SIZE, RealmID realmID - LONG_SIZE, Key newRealmAdminKey - calculated
     * value
     */

    int keySize = getAccountKeyStorageSize(txBody.getCryptoCreateAccount().getKey());
    int newRealmAdminKeySize = 0;
    if (txBody.getCryptoCreateAccount().hasNewRealmAdminKey()) {
      newRealmAdminKeySize =
          getAccountKeyStorageSize(txBody.getCryptoCreateAccount().getNewRealmAdminKey());
    }

    int cryptoAcctBodySize = keySize + BASIC_ACCOUNT_SIZE + newRealmAdminKeySize;

    return cryptoAcctBodySize;

  }



  private int getCryptoTransferBodyTxSize(TransactionBody txBody) {

    /*
     * TransferList transfers repeated AccountAmount AccountID - (BASIC_ENTITY_ID_SIZE) sint64 amount -
     * LONG_SIZE
     */
    int accountAmountCount = txBody.getCryptoTransfer().getTransfers().getAccountAmountsCount();
    int cryptoTransfertBodySize = (BASIC_ACCT_AMT_SIZE) * accountAmountCount;
    return cryptoTransfertBodySize;
  }

  /*
   * private long getTV(TransactionBody txBody) { long amount = 0; TransferList transferList =
   * txBody.getCryptoTransfer().getTransfers(); List<AccountAmount> accountAmounts =
   * transferList.getAccountAmountsList(); for (AccountAmount actAmt : accountAmounts) { if
   * (actAmt.getAmount() > 0) { amount = amount + actAmt.getAmount(); } } return Math.round(amount /
   * 1000); }
   */

  ////////////////////////////////////////////////////////////////////////// Query Fee
  ////////////////////////////////////////////////////////////////////////// //////////////////////////////////////////////////////////////////////////

  /**
   * This method returns the Fee Matrices for balance query
   */
  public FeeData getBalanceQueryFeeMatrices(ResponseType responseType) {
    // get the Fee Matrices
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;


    /*
     * CryptoGetAccountBalanceQuery QueryHeader Transaction - CryptoTransfer - (will be taken care
     * in Transaction processing) ResponseType - INT_SIZE AccountID - BASIC_ENTITY_ID_SIZE
     */
    bpt = INT_SIZE + BASIC_ENTITY_ID_SIZE;

    /*
     * CryptoGetAccountBalanceResponse Response header NodeTransactionPrecheckCode - 4 bytes
     * ResponseType - 4 bytes AccountID - 24 bytes (consist of 3 long values) balance - 8 bytes (1
     * long value)
     */

    bpr = BASIC_QUERY_RES_HEADER + BASIC_ENTITY_ID_SIZE + LONG_SIZE + getStateProofSize(responseType);

   /* FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    return getQueryFeeDataMatrices(feeMatrices);*/
    
    return FeeData.getDefaultInstance();
  }

  /**
   * This method returns the Fee Matrices for query (for getting the cost of Transaction Record
   * Query)
   */
  public FeeData getCostTransactionRecordQueryFeeMatrices() {
    // get the Fee Matrices
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    /*
     * CostTransactionGetRecordQuery QueryHeader Transaction - CryptoTransfer - (will be taken care
     * in Transaction processing) ResponseType - INT_SIZE TransactionID AccountID accountID - BASIC_ENTITY_ID_SIZE
     * bytes Timestamp transactionValidStart - (LONG_SIZE + INT_SIZE) bytes
     */

    bpt = INT_SIZE + BASIC_ENTITY_ID_SIZE + LONG_SIZE;

    /*
     * bpr = TransactionRecordResponse Response header NodeTransactionPrecheckCode - 4 bytes
     * ResponseType - 4 bytes Transaction Record Size
     *
     */
   
    bpr = BASIC_QUERY_RES_HEADER ;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

   /* return getQueryFeeDataMatrices(feeMatrices);*/
    return FeeData.getDefaultInstance();

  }


  /**
   * This method returns the Fee matrices for Transaction Record query
   */
  public FeeData getTransactionRecordQueryFeeMatrices(TransactionRecord transRecord,ResponseType responseType) {

    if (transRecord == null) {
      return FeeData.getDefaultInstance();
    }
    // get the Fee Matrices
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    /*
     * TransactionGetRecordQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE TransactionID AccountID accountID - BASIC_ENTITY_ID_SIZE
     * bytes Timestamp transactionValidStart - (LONG_SIZE) bytes
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE + LONG_SIZE;

    /*
     * bpr = TransactionRecordResponse Response header NodeTransactionPrecheckCode - 4 bytes
     * ResponseType - 4 bytes Transaction Record Size
     *
     */
    int txRecordSize = getAccountTransactionRecordSize(transRecord);

    bpr = BASIC_QUERY_RES_HEADER + txRecordSize + getStateProofSize(responseType);

   FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }

  /**
   * This method returns the Fee matrices for Account Info query
   */
  public FeeData getAccountInfoQueryFeeMatrices(
          Key key, List<LiveHash>
          liveHashes,
          ResponseType responseType
  ) {
    // get the Fee Matrices
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    /*
     * CryptoGetInfoQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE AccountID accountID - BASIC_ENTITY_ID_SIZE bytes
     *
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE;

    /*
     * bpr = CryptoGetInfoResponse Response header NodeTransactionPrecheckCode - 4 bytes
     * ResponseType - 4 bytes AccountInfo accountInfo - calculated value
     *
     */
    int accountInfoSize = getAccountInfoSize(key, liveHashes);

    bpr = BASIC_QUERY_RES_HEADER + accountInfoSize + getStateProofSize(responseType);

   
    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }

  /**
   * This method returns the Fee Matrices for Account Records query
   */
  public FeeData getCryptoAccountRecordsQueryFeeMatrices(List<TransactionRecord> transRecord,ResponseType responseType) {

    // get the Fee Matrices
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    /*
     * CryptoGetAccountRecordsQuery QueryHeader Transaction - CryptoTransfer - (will be taken care
     * in Transaction processing) ResponseType - INT_SIZE AccountID - BASIC_ENTITY_ID_SIZE
     *
     */

    bpt = BASIC_QUERY_HEADER + BASIC_TX_ID_SIZE;

    /*
     * bpr = TransactionRecordResponse Response header NodeTransactionPrecheckCode - 4 bytes
     * ResponseType - 4 bytes AccountID accountID - 24 bytes repeated TransactionRecord - get size
     * from records
     *
     */
    int txRecordListsize = 0;
    if (transRecord != null) {
      for (TransactionRecord record : transRecord) {
        txRecordListsize = txRecordListsize + getAccountTransactionRecordSize(record);
      }
    }
    bpr = BASIC_QUERY_RES_HEADER + txRecordListsize + getStateProofSize(responseType);

    
    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);
  }

  /**
   * This method returns the Fee Matrices for query (for getting the cost of Account Record Query)
   */
  public FeeData getCostCryptoAccountRecordsQueryFeeMatrices() {

    return getCostForQueryByIDOnly();
  }

  /**
   * This method returns the Fee Matrices for query (for getting the cost of Account Info Query)
   */
  public FeeData getCostCryptoAccountInfoQueryFeeMatrices() {

    return getCostForQueryByIDOnly();
  }


  private int getAccountInfoSize(Key accountKey, List<LiveHash> liveHashes) {

    /*
     * AccountID accountID - BASIC_ENTITY_ID_SIZE string contractAccountID - SOLIDITY_ADDRESS bool deleted
     * - BOOL_SIZE AccountID proxyAccountID - BASIC_ENTITY_ID_SIZE int32 proxyFraction - INT_SIZE int64
     * proxyReceived - INT_SIZE Key key - calculated value uint64 balance - LONG_SIZE uint64
     * generateSendRecordThreshold - LONG_SIZE uint64 generateReceiveRecordThreshold - LONG_SIZE
     * bool receiverSigRequired - BOOL_SIZE Timestamp expirationTime - LONG_SIZE Duration
     * autoRenewPeriod - LONG_SIZE repeated LiveHash claims - calculated value AccountID accountID - BASIC_ENTITY_ID_SIZE
     * bytes hash - 48 byte SHA-384 hash (presumably of some kind of credential or
     * certificate) KeyList keys - calculated value
     *
     */

    int keySize = getAccountKeyStorageSize(accountKey);

    int claimSize = liveHashSize(liveHashes);

    int accountInfoSize = BASIC_ACCOUNT_SIZE + keySize + claimSize;

    return accountInfoSize;

  }

  private int getAccountTransactionRecordSize(TransactionRecord transRecord) {

    /*
     * TransactionReceipt - 4 bytes + BASIC_ENTITY_ID_SIZE bytes transactionHash - 96 bytes Timestamp
     * consensusTimestamp - 8 bytes TransactionID - 32 bytes (AccountID - 24 + Timestamp - 8) string
     * memo - get from the record uint64 transactionFee - 8 bytes TransferList transferList - get
     * from actual transaction record
     *
     */
    int memoBytesSize = 0;
    if (transRecord.getMemo() != null) {
      memoBytesSize = transRecord.getMemoBytes().size();
    }

    int acountAmountSize = 0;
    if (transRecord.hasTransferList()) {
      int accountAmountCount = transRecord.getTransferList().getAccountAmountsCount();
      acountAmountSize = accountAmountCount * (BASIC_ACCT_AMT_SIZE); // (24 bytes AccountID and 8
      // bytes Amount)
    }

    int txRecordSize = BASIC_TX_RECORD_SIZE + memoBytesSize + acountAmountSize;

    return txRecordSize;

  }

  public FeeData getCryptoAddLiveHashTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    /*
     * account ID LiveHash contains accountID hash keys claimExpiration
     */

    if (txBody == null || !txBody.hasCryptoAddLiveHash()) {
      throw new InvalidTxBodyException("CryptoAddLiveHash Tx Body not available for Fee Calculation");
    }
    long bpt = 0;
    long vpt = 0;
    long rbs = 0; // as nothing is stored in memory for claim
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    // calculate BPT - Total Bytes in Transaction
    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);
    bpt = txBodySize + getCryptoAddLiveHashBodyBodyTxSize(txBody) + (BASIC_ENTITY_ID_SIZE)
        + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();
    long rbsNetwork = getDefaultRBHNetworkSize();
    rbs = getCryptoLiveHashStorageBytesSec(txBody)
        + getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;

    bpr = INT_SIZE;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();


    return getFeeDataMatrices(feeMatrices, sigValObj.getPayerAcctSigCount(), rbsNetwork);
  }

  public FeeData getCryptoDeleteLiveHashTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasCryptoDeleteLiveHash()) {
      throw new InvalidTxBodyException(
          "CryptoDeleteLiveHash Tx Body not available for Fee Calculation");
    }
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    // calculate BPT - Total Bytes in Transaction
    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);
    bpt = txBodySize + getCryptoDeleteLiveHashBodyBodyTxSize() + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

    long rbsNetwork = getDefaultRBHNetworkSize();
    rbs = getCryptoLiveHashStorageBytesSec(txBody)
        + getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;
    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);

  }

  private int getCryptoAddLiveHashBodyBodyTxSize(TransactionBody txBody) {

    int keySize = 0;
    int keyListSize = 0;
    KeyList liveHashKeys = txBody.getCryptoAddLiveHash().getLiveHash().getKeys();
    for (Key key : liveHashKeys.getKeysList()) {
      keySize = sizeOfLiveHashKeyStorage(key);
      keyListSize = keySize + 1;
    }
    int claimHashSize = getLiveHashHashSize();

    int cryptoAddLiveHashBodySize = keyListSize + (BASIC_ENTITY_ID_SIZE) + claimHashSize;

    return cryptoAddLiveHashBodySize;
  }

  private int getCryptoDeleteLiveHashBodyBodyTxSize() {
    int claimHashSize = getLiveHashHashSize();
    int cryptoDeleteLiveHashBodySize = +(BASIC_ENTITY_ID_SIZE) + claimHashSize;
    return cryptoDeleteLiveHashBodySize;
  }

  private int getCryptoGetLiveHashBodyTxSize() {
    int claimHashSize = getLiveHashHashSize();
    int cryptoGetLiveHashBodySize = (BASIC_ENTITY_ID_SIZE) + claimHashSize;
    return cryptoGetLiveHashBodySize;
  }

  private int getLiveHashHashSize() {
    return TX_HASH_SIZE;

  }

  private long getCryptoLiveHashStorageBytesSec(TransactionBody txBody) {

    long storageSize = (BASIC_ENTITY_ID_SIZE) + TX_HASH_SIZE
        + +txBody.getCryptoAddLiveHash().getLiveHash().getKeys().getSerializedSize();
    // get expiration time storage
    // Instant expirationTime = RequestBuilder
    // .convertProtoTimeStampSeconds(txBody.getCryptoAddLiveHash().getLiveHash().getLiveHashExpiration());
    // Timestamp txValidStartTimestamp = txBody.getTransactionID()
    // .getTransactionValidStart();
    // Instant txValidStartTime = RequestBuilder.convertProtoTimeStamp(txValidStartTimestamp);
    // Duration duration = Duration.between(txValidStartTime, expirationTime);
    long seconds = txBody.getCryptoAddLiveHash().getLiveHash().getDuration().getSeconds();
    storageSize = storageSize * seconds;
    return storageSize;

  }


  public FeeData getLiveHashFeeQueryFeeMatrices() {
    // get the Fee Matrices
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    bpt = INT_SIZE + INT_SIZE + getCryptoGetLiveHashBodyTxSize();
    sbpr = getCryptoGetLiveHashBodyTxSize();
    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatrices, DEFAULT_PAYER_ACC_SIG_COUNT, DEFAULT_RBS_NETWORK);
  }
 

  /**
   * FeeMetrics for Crypto Account
   *
   * @return feeComponents
   */
  public FeeData getCryptoAccountRenewalFeeMatrices(Key key, long autoRenewal) {

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    // Since its renewal, only RAM bytes will be considered!
    // rbs - RAM bytes seconds
    /*
     * long balance - LONG_SIZE long receiverThreshold - LONG_SIZE long senderThreshold - LONG_SIZE
     * boolean receiverSigRequired - BOOL_SIZE Key accountKeys - calculated size AccountID
     * proxyAccount - BASIC_ENTITY_ID_SIZE long autoRenewPeriod - LONG_SIZE boolean deleted - BOOL_SIZE
     */

    rbs = (BASIC_ACCOUNT_SIZE + getAccountKeyStorageSize(key)) * autoRenewal;


    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, DEFAULT_PAYER_ACC_SIG_COUNT, DEFAULT_RBS_NETWORK);

  }


}
