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

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.exception.InvalidTxBodyException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


/**
 * This class includes methods for generating Fee Matrices and calculating Fee for File related
 * Transactions and Query.
 */
public class FileFeeBuilder extends FeeBuilder {

  /**
   * This method returns fee matrices for file append transaction
   *
   * @param txBody transaction body
   * @param expirationTimeStamp expiration timestamp
   * @param sigValObj signature value object
   *
   * @return fee data
   * @throws InvalidTxBodyException when transaction body is invalid
   */
  public FeeData getFileAppendTxFeeMatrices(TransactionBody txBody, Timestamp expirationTimeStamp,
      SigValueObj sigValObj) throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasFileAppend()) {
      throw new InvalidTxBodyException("FileAppend Tx Body not available for Fee Calculation");
    }
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    FileAppendTransactionBody fileAppendTxBody = txBody.getFileAppend();
    final long txBodySize = getCommonTransactionBodyBytes(txBody);

    // bpt - Bytes per Transaction
    bpt = txBodySize + sigValObj.getSignatureSize();
    int fileContentSize = 0;

    if (fileAppendTxBody.getContents() != null) {
      fileContentSize = fileAppendTxBody.getContents().size();
    }
    bpt = bpt + fileContentSize;
    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

    // sbs - Storage bytes seconds
    int sbsStorageSize = fileContentSize;
    if ((sbsStorageSize != 0) && (expirationTimeStamp != null && expirationTimeStamp.getSeconds() > 0)) {
      Instant expirationTime = RequestBuilder.convertProtoTimeStamp(expirationTimeStamp);
      Timestamp txValidStartTimestamp = txBody.getTransactionID().getTransactionValidStart();
      Instant txValidStartTime = RequestBuilder.convertProtoTimeStamp(txValidStartTimestamp);
      Duration duration = Duration.between(txValidStartTime, expirationTime);
      long seconds = duration.getSeconds();
      sbs = sbsStorageSize * seconds;
    }
    rbs =  calculateRBS(txBody);
    long rbsNetwork = getDefaultRBHNetworkSize();

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }

  /**
   * This method returns fee matrices for file info query
   *
   * @param keys keys
   * @param responseType response type
   *
   * @return fee data
   */
  public FeeData getFileInfoQueryFeeMatrices(KeyList keys, ResponseType responseType) {

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
     * FileGetContentsQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE FileID - BASIC_ENTITY_ID_SIZE
     */

    bpt = calculateBPT();
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * FileInfo FileID fileID - BASIC_ENTITY_ID_SIZE int64 size - LONG_SIZE Timestamp expirationTime = 3;
     * // the current time at which this account is set to expire bool deleted = 4; // true if
     * deleted but not yet expired KeyList keys = 5; // one of these keys must sign in order to
     * modify or delete the file
     *
     */
    int keySize = 0;
    if (keys != null) {
      List<Key> waclKeys = keys.getKeysList();
      for (Key key : waclKeys) {
        keySize += getAccountKeyStorageSize(key);
      }
    }
    
    
    bpr = BASIC_QUERY_RES_HEADER + getStateProofSize(responseType);

    sbpr = (long) BASE_FILEINFO_SIZE + keySize;


    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }

  /**
   * This method returns fee matrices for file content query
   *
   * @param contentSize content size
   * @param responseType response type
   *
   * @return fee data
   */
  public FeeData getFileContentQueryFeeMatrices(int contentSize, ResponseType responseType) {

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
     * FileGetContentsQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE FileID - BASIC_ENTITY_ID_SIZE
     */

    bpt =  calculateBPT();
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * FileContents FileID fileID - BASIC_ENTITY_ID_SIZE bytes content - calculated value (size of the
     * content)
     *
     */

    bpr =  BASIC_QUERY_RES_HEADER + getStateProofSize(responseType);

    sbpr = (long) BASIC_ENTITY_ID_SIZE + contentSize;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }


  /**
   * This method returns total bytes in File Create Transaction
   */
  private int getFileCreateTxSize(TransactionBody txBody) {
    /*
     * Timestamp expirationTime - (LONG_SIZE + INT_SIZE) KeyList keys - calculated value bytes
     * contents -get the size ShardID shardID - LONG_SIZE RealmID realmID - LONG_SIZE Key
     * newRealmAdminKey - calculated value
     */

    FileCreateTransactionBody fileCreateTxBody = txBody.getFileCreate();
    List<Key> waclKeys = fileCreateTxBody.getKeys().getKeysList();

    int keySize = 0;

    for (Key key : waclKeys) {
      keySize += getAccountKeyStorageSize(key);
    }
    int newRealmAdminKeySize = 0;

    if (fileCreateTxBody.hasNewRealmAdminKey()) {
      newRealmAdminKeySize = getAccountKeyStorageSize(fileCreateTxBody.getNewRealmAdminKey());
    }
    int fileContentsSize = 0;
    if (fileCreateTxBody.getContents() != null) {
      fileContentsSize = fileCreateTxBody.getContents().size();
    }

    int cryptoFileCreateSize = (LONG_SIZE) + keySize + fileContentsSize + (BASIC_ENTITY_ID_SIZE)   + newRealmAdminKeySize;

    return cryptoFileCreateSize;

  }

  public FeeData getSystemDeleteFileTxFeeMatrices(TransactionBody txBody, SigValueObj numSignatures)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasSystemDelete()) {
      throw new InvalidTxBodyException("System Delete Tx Body not available for Fee Calculation");
    }
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    // get the bytes per second
    bpt = getCommonTransactionBodyBytes(txBody);
    bpt = bpt + BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    vpt = numSignatures.getTotalSigCount();
    
    rbs =  calculateRBS(txBody);
    
    long rbsNetwork = getDefaultRBHNetworkSize();

    // sbs should not be charged as the fee for storage was already paid. What if expiration is changed though?

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, numSignatures.getPayerAcctSigCount(),rbsNetwork);
  }

  public FeeData getSystemUnDeleteFileTxFeeMatrices(TransactionBody txBody,
      SigValueObj numSignatures)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasSystemUndelete()) {
      throw new InvalidTxBodyException("System UnDelete Tx Body not available for Fee Calculation");
    }
    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    // get the bytes per second
    bpt = getCommonTransactionBodyBytes(txBody);
    bpt = bpt + BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    vpt = numSignatures.getTotalSigCount();
    rbs =  calculateRBS(txBody);
    long rbsNetwork = getDefaultRBHNetworkSize();

    // sbs should not be charged as the fee for storage was already paid. What if expiration is changed though?

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, numSignatures.getPayerAcctSigCount(),rbsNetwork);
  }

  public FeeData getFileDeleteTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj) throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasFileDelete()) {
      throw new InvalidTxBodyException("FileDelete Tx Body not available for Fee Calculation");
    }

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    final long txBodySize = getCommonTransactionBodyBytes(txBody);

    // bpt - Bytes per Transaction
    bpt = txBodySize + BASIC_ENTITY_ID_SIZE + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

   
    rbs =  calculateRBS(txBody);

    long rbsNetwork = getDefaultRBHNetworkSize();

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }


}
