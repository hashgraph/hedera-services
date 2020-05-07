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

import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
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
   * This method returns Fee Matrices for File Create Transaction
   */
  public FeeData getFileCreateTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasFileCreate()) {
      throw new InvalidTxBodyException("FileCreate Tx Body not available for Fee Calculation");
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
    bpt = txBodySize + getFileCreateTxSize(txBody) + sigValObj.getSignatureSize();
    
    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    // sbs - Stoarge bytes seconds 
    sbs = getFileCreateStorageBytesSec(txBody);

    bpr = INT_SIZE;
    
    rbs =  (getBaseTransactionRecordSize(txBody) + BASIC_ACCTID_SIZE/* Added for File ID size*/) * RECIEPT_STORAGE_TIME_SEC;

    long rbsNetwork = getDefaultRBHNetworkSize() + BASIC_ACCTID_SIZE * (RECIEPT_STORAGE_TIME_SEC);

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);
  }

  /**
   * This method returns Fee Matrices for File Update Transaction
   */
  public FeeData getFileUpdateTxFeeMatrices(TransactionBody txBody, Timestamp expirationTimeStamp,
      SigValueObj sigValObj) throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasFileUpdate()) {
      throw new InvalidTxBodyException("FileUpdate Tx Body not available for Fee Calculation");
    }

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    FileUpdateTransactionBody fileUpdateTxBody = txBody.getFileUpdate();
    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);

    // bpt - Bytes per Transaction
    bpt = txBodySize + getFileUpdateBodyTxSize(txBody) + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

    // sbs - Storage bytes seconds - check if key is changed, need to charge for new key storage
    int sbsStorageSize = 0;

    if (fileUpdateTxBody.hasKeys()) {
      List<Key> waclKeys = fileUpdateTxBody.getKeys().getKeysList();
      int keySize = 0;

      for (Key key : waclKeys) {
        keySize += getAccountKeyStorageSize(key);
      }
      sbsStorageSize += keySize;
    }

    if (fileUpdateTxBody.getContents() != null) {
      sbsStorageSize += fileUpdateTxBody.getContents().size();
    }
    if ((sbsStorageSize != 0) && (expirationTimeStamp != null && expirationTimeStamp.getSeconds() > 0)) {
      Instant expirationTime = RequestBuilder.convertProtoTimeStamp(expirationTimeStamp);
      Timestamp txValidStartTimestamp = txBody.getTransactionID().getTransactionValidStart();
      Instant txValidStartTime = RequestBuilder.convertProtoTimeStamp(txValidStartTimestamp);
      Duration duration = Duration.between(txValidStartTime, expirationTime);
      long seconds = duration.getSeconds();
      sbs = sbsStorageSize * seconds;
    }
    
    rbs =  getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;

    long rbsNetwork = getDefaultRBHNetworkSize();

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
   
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }

  /**
   * This method returns Fee Matrices for File Append Transaction
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
    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);

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
    rbs =  getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;
    long rbsNetwork = getDefaultRBHNetworkSize();

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }


  /**
   * This method returns Fee Matrices for File Info Query
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
     * Transaction processing) ResponseType - INT_SIZE FileID - 3 * LONG_SIZE
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ACCTID_SIZE;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * FileInfo FileID fileID - 3 * LONG_SIZE int64 size - LONG_SIZE Timestamp expirationTime = 3;
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
    
    
    bpr = BASIC_QUERY_RES_HEADER  + getStateProofSize(responseType);

    sbpr = BASE_FILEINFO_SIZE + keySize;


    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }


  /**
   * This method returns Fee Matrices for File Content Query
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
     * Transaction processing) ResponseType - INT_SIZE FileID - 3 * LONG_SIZE
     */

    bpt =  BASIC_QUERY_HEADER + BASIC_ACCTID_SIZE;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * FileContents FileID fileID - 3 * LONG_SIZE bytes content - calculated value (size of the
     * content)
     *
     */

    bpr =  BASIC_QUERY_RES_HEADER  + getStateProofSize(responseType);

    sbpr = BASIC_ACCTID_SIZE + contentSize;

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

    int cryptoFileCreateSize = (LONG_SIZE) + keySize + fileContentsSize + (3 * LONG_SIZE)   + newRealmAdminKeySize;

    return cryptoFileCreateSize;

  }

  /**
   * This method returns total bytes in File Update Transaction Body
   */
  public static int getFileUpdateBodyTxSize(TransactionBody txBody) {
    /*
     * FileID fileID = 1; // the file to update Timestamp expirationTime = 2; // the new time at
     * which it should expire (ignored if not later than the current value) KeyList keys = 3; // the
     * keys that can modify or delete the file bytes contents = 4; // the new file contents. All the
     * bytes in the old contents are discarded.
     */

    int fileUpdateBodySize = BASIC_ACCTID_SIZE;
    FileUpdateTransactionBody fileUpdateTxBody = txBody.getFileUpdate();

    if (fileUpdateTxBody.hasKeys()) {
      List<Key> waclKeys = fileUpdateTxBody.getKeys().getKeysList();
      int keySize = 0;
      for (Key key : waclKeys) {
        keySize = keySize + getAccountKeyStorageSize(key);
      }
      fileUpdateBodySize = fileUpdateBodySize +keySize;
    }

    if (fileUpdateTxBody.hasExpirationTime()) {
      fileUpdateBodySize = fileUpdateBodySize + (LONG_SIZE);
    }

    if (fileUpdateTxBody.getContents() != null) {
      fileUpdateBodySize = fileUpdateBodySize +  fileUpdateTxBody.getContents().size();
    }

    return fileUpdateBodySize;

  }

  /**
   * This method calculates total total Storage Bytes (product of total bytes that will be stored in
   * File Storage and time till account expires)
   */
  private long getFileCreateStorageBytesSec(TransactionBody txBody) {
    long storageSize = getFileCreateTxSize(txBody)  + LONG_SIZE + BOOL_SIZE ; // add Expiration Time and Deleted flag space
    Timestamp expirationTimeStamp = txBody.getFileCreate().getExpirationTime();
    if (expirationTimeStamp == null) {
      return 0;
    }
    Instant expirationTime = RequestBuilder.convertProtoTimeStamp(expirationTimeStamp);
    Timestamp txValidStartTimestamp = txBody.getTransactionID().getTransactionValidStart();
    Instant txValidStartTime = RequestBuilder.convertProtoTimeStamp(txValidStartTimestamp);
    Duration duration = Duration.between(txValidStartTime, expirationTime);
    long seconds = duration.getSeconds();
    storageSize = storageSize * seconds;
    return storageSize;

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
    bpt = bpt + 3 * LONG_SIZE + LONG_SIZE;
    vpt = numSignatures.getTotalSigCount();
    
    rbs =  getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;
    
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
    bpt = bpt + 3 * LONG_SIZE + LONG_SIZE;
    vpt = numSignatures.getTotalSigCount();
    rbs =  getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;
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

    int txBodySize = 0;
    txBodySize = getCommonTransactionBodyBytes(txBody);

    // bpt - Bytes per Transaction
    bpt = txBodySize + BASIC_ACCTID_SIZE + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

   
    rbs =  getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;

    long rbsNetwork = getDefaultRBHNetworkSize();

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();
    
    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }


}
