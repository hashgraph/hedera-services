package com.hederahashgraph.fee;

/*-
 * ‌
 * Hedera Services API
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

import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.exception.InvalidTxBodyException;

import java.util.List;

/**
 * This class includes methods for generating Fee Matrices and calculating Fee for Crypto related
 * Transactions and Query.
 */

public class CryptoFeeBuilder extends FeeBuilder {

  /**
   * This method returns the fee matrices for crypto create transaction
   *
   * @param txBody transaction body
   * @param sigValObj signature value object
   *
   * @return fee data
   * @throws InvalidTxBodyException when transaction body is invalid
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

    long txBodySize = getCommonTransactionBodyBytes(txBody);
    int cryptoCreateSize = getCryptoCreateAccountBodyTxSize(txBody);
    bpt = txBodySize + cryptoCreateSize + sigValObj.getSignatureSize();
    vpt = sigValObj.getTotalSigCount();
    rbs = getCryptoRBS(txBody, cryptoCreateSize)
        + calculateRBS(txBody); // TxRecord
    long rbsNetwork = getDefaultRBHNetworkSize() + BASIC_ENTITY_ID_SIZE * (RECEIPT_STORAGE_TIME_SEC);

    bpr = INT_SIZE;

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
  }

  /**
   * This method returns the fee matrices for crypto delete transaction
   *
   * @param txBody transaction body
   * @param sigValObj signature value object
   *
   * @return fee data
   * @throws InvalidTxBodyException when transaction body is invalid
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

    long txBodySize = 0;

    bpr = INT_SIZE;
    txBodySize = getCommonTransactionBodyBytes(txBody);
    bpt = txBodySize + 2 * BASIC_ENTITY_ID_SIZE + sigValObj.getSignatureSize();
    vpt = sigValObj.getTotalSigCount();
    // TxRecord
    rbs = calculateRBS(txBody);
    long rbsNetwork = getDefaultRBHNetworkSize();
    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
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

  /**
   * This method returns the fee matrices for query (for getting the cost of transaction record
   * query)
   *
   * @return fee data
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

    bpt = (long) INT_SIZE + BASIC_ENTITY_ID_SIZE + LONG_SIZE;

    /*
     * bpr = TransactionRecordResponse Response header NodeTransactionPrecheckCode - 4 bytes
     * ResponseType - 4 bytes Transaction Record Size
     *
     */
   
    bpr = BASIC_QUERY_RES_HEADER ;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return FeeData.getDefaultInstance();

  }

  /**
   * This method returns the fee matrices for transaction record query
   *
   * @param transRecord transaction record
   * @param responseType response type
   *
   * @return fee data
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
   * This method returns the fee matrices for account records query
   *
   * @param transRecord transaction record
   * @param responseType response type
   *
   * @return fee data
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
   * This method returns the fee matrices for query (for getting the cost of account record query)
   *
   * @return fee data
   */
  public FeeData getCostCryptoAccountRecordsQueryFeeMatrices() {

    return getCostForQueryByIDOnly();
  }

  /**
   * This method returns the fee matrices for query (for getting the cost of account info query)
   *
   * @return fee data
   */
  public FeeData getCostCryptoAccountInfoQueryFeeMatrices() {

    return getCostForQueryByIDOnly();
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
      acountAmountSize = accountAmountCount * (BASIC_ACCOUNT_AMT_SIZE); // (24 bytes AccountID and 8 bytes Amount)
    }

    int txRecordSize = BASIC_TX_RECORD_SIZE + memoBytesSize + acountAmountSize;

    return txRecordSize;

  }
}
