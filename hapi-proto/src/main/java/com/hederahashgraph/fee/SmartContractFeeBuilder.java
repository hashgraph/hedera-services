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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
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
 * This class includes methods for generating Fee Matrices and calculating Fee for Smart Contract
 * related Transactions and Query.
 */
public class SmartContractFeeBuilder extends FeeBuilder {


  /**
   * This method returns Fee Matrices for Contract Create Transaction
   */
  public FeeData getContractCreateTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasContractCreateInstance()) {
      throw new InvalidTxBodyException(
          "ContractCreateInstance Tx Body not available for Fee Calculation");
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
    bpt = txBodySize + getContractCreateTransactionBodySize(txBody) + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    /*// sbs - Stoarge bytes seconds
    sbs = getContractCreateStorageBytesSec(txBody);*/


    bpr = INT_SIZE;
    rbs =  getBaseTransactionRecordSize(txBody) * (RECIEPT_STORAGE_TIME_SEC + THRESHOLD_STORAGE_TIME_SEC);
    long rbsNetwork = getDefaultRBHNetworkSize() + BASIC_ACCTID_SIZE * (RECIEPT_STORAGE_TIME_SEC);

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }

  /**
   * This method calculates total total Storage Bytes (product of total bytes that will be stored in
   * disk and time till account expires)
   */
  private long getContractCreateStorageBytesSec(TransactionBody txBody) {

    long storageSize = getContractCreateTransactionBodySize(txBody);
    long seconds = txBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds();
    storageSize = storageSize * seconds;
    return storageSize;
  }

  /**
   * This method returns total bytes in Contract Create Transaction body
   */
  private int getContractCreateTransactionBodySize(TransactionBody txBody) {
    /*
     * FileID fileID - 3 * LONG_SIZE Key adminKey - calculated value int64 gas - LONG_SIZE uint64
     * initialBalance - LONG_SIZE AccountID proxyAccountID - 3 * LONG_SIZE bytes
     * constructorParameters - calculated value Duration autoRenewPeriod - (LONG_SIZE + INT_SIZE)
     * ShardID shardID - LONG_SIZE RealmID realmID - LONG_SIZE Key newRealmAdminKey - calculated
     * value string memo - calculated value
     *
     */

    ContractCreateTransactionBody contractCreate = txBody.getContractCreateInstance();
    int adminKeySize = 0;
    int proxyAcctID =0;
    if (contractCreate.hasAdminKey()) {
      adminKeySize = getAccountKeyStorageSize(contractCreate.getAdminKey());
    }
    int newRealmAdminKeySize = 0;
    if (contractCreate.hasNewRealmAdminKey()) {
      newRealmAdminKeySize = getAccountKeyStorageSize(contractCreate.getNewRealmAdminKey());
    }

    int constructParamSize = 0;

    if (contractCreate.getConstructorParameters() != null) {
      constructParamSize = contractCreate.getConstructorParameters().size();
    }
    
    if (contractCreate.hasProxyAccountID()) {
      proxyAcctID = BASIC_ACCTID_SIZE;
    }

    int memoSize = 0;
    if (contractCreate.getMemo() != null) {
      memoSize = contractCreate.getMemoBytes().size();
    }
    int contractCreateBodySize = BASIC_CONTRACT_CREATE_SIZE + adminKeySize +  proxyAcctID +  constructParamSize  +  newRealmAdminKeySize + memoSize;

    return contractCreateBodySize;

  }

  /**
   * This method returns Fee Matrices for Contract Update Transaction
   */
  public FeeData getContractUpdateTxFeeMatrices(TransactionBody txBody,
      Timestamp contractExpiryTime, SigValueObj sigValObj) throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasContractUpdateInstance()) {
      throw new InvalidTxBodyException(
          "ContractUpdateInstance Tx Body not available for Fee Calculation");
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
    bpt = txBodySize + getContractUpdateBodyTxSize(txBody) + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;
    
    if(contractExpiryTime != null && contractExpiryTime.getSeconds() > 0) {
    	sbs = getContractUpdateStorageBytesSec(txBody, contractExpiryTime);
    }

    long rbsNetwork = getDefaultRBHNetworkSize()  ;
    
    rbs =  getBaseTransactionRecordSize(txBody) * (RECIEPT_STORAGE_TIME_SEC + THRESHOLD_STORAGE_TIME_SEC);

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);
  }


  /**
   * This method returns Fee Matrices for Contract Call Transaction
   */
  public FeeData getContractCallTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasContractCall()) {
      throw new InvalidTxBodyException(
          "ContractCreateInstance Tx Body not available for Fee Calculation");
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
    bpt = txBodySize + getContractCallBodyTxSize(txBody) + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

    rbs =  getBaseTransactionRecordSize(txBody) * (RECIEPT_STORAGE_TIME_SEC + THRESHOLD_STORAGE_TIME_SEC);
    long rbsNetwork = getDefaultRBHNetworkSize() ;

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return  getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);

  }


  /**
   * This method returns Fee Matrices for Contract Call Local
   */
  public FeeData getContractCallLocalFeeMatrices(int funcParamSize,
      ContractFunctionResult contractFuncResult,ResponseType responseType) {

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
     * QueryHeader header Transaction - CryptoTransfer - (will be taken care in Transaction
     * processing) ResponseType - INT_SIZE ContractID contractID - 3 * LONG_SIZE int64 gas -
     * LONG_SIZE bytes functionParameters - calculated value int64 maxResultSize - LONG_SIZE
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ACCTID_SIZE + LONG_SIZE + funcParamSize + LONG_SIZE;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     * ContractFunctionResult ContractID contractID - 3 * LONG_SIZE bytes contractCallResult -
     * Calculated Value string errorMessage - Calculated value bytes bloom - Calculated value uint64
     * gasUsed - LONG_SIZE repeated ContractLoginfo ContractID contractID - 3 * LONG_SIZE bytes
     * bloom - Calculated Value repeated bytes - Calculated Value bytes data - Calculated Value
     *
     */

    int errorMessageSize = 0;   
    int contractFuncResultSize = 0;
    if (contractFuncResult != null) {
      
       if (contractFuncResult.getContractCallResult() != null) {
        contractFuncResultSize = contractFuncResult.getContractCallResult().size();
      }
      if (contractFuncResult.getErrorMessage() != null) {
        errorMessageSize = contractFuncResult.getErrorMessage().length();
      }      
    }
    
    bpr = BASIC_QUERY_RES_HEADER + getStateProofSize(responseType);    
   
    sbpr = BASIC_ACCTID_SIZE + errorMessageSize +  LONG_SIZE  +  contractFuncResultSize;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);


  }

  /**
   * This method returns Fee Matrices for Contract Call Local
   */
  public FeeData getCostContractCallLocalFeeMatrices(int funcParamSize) {

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
     * ContractCallLocalQuery QueryHeader header Transaction - CryptoTransfer - (will be taken care
     * in Transaction processing) ResponseType - INT_SIZE ContractID contractID - 3 * LONG_SIZE
     * int64 gas - LONG_SIZE bytes functionParameters - calculated value int64 maxResultSize -
     * LONG_SIZE
     */

    bpt = INT_SIZE + BASIC_ACCTID_SIZE + LONG_SIZE + funcParamSize + LONG_SIZE;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes uint64 cost -
     * LONG_SIZE
     */
    bpr = BASIC_QUERY_RES_HEADER ;   

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);
  }

  /**
   * This method returns total bytes in Contract Update Transaction
   */
  private int getContractUpdateBodyTxSize(TransactionBody txBody) {
    /*
     * ContractID contractID - 3 * LONG_SIZE Timestamp expirationTime - LONG_SIZE + INT_SIZE
     * AccountID proxyAccountID - 3 * LONG_SIZE Duration autoRenewPeriod - LONG_SIZE + INT_SIZE
     * FileID fileID - 3 * LONG_SIZE Key adminKey - calculated value string memo - calculated value
     */

    int contractUpdateBodySize = 3 * LONG_SIZE;

    ContractUpdateTransactionBody contractUpdateTxBody = txBody.getContractUpdateInstance();

    if (contractUpdateTxBody.hasProxyAccountID()) {
      contractUpdateBodySize += 3 * LONG_SIZE;
    }

    if (contractUpdateTxBody.hasFileID()) {
      contractUpdateBodySize += 3 * LONG_SIZE;
    }

    if (contractUpdateTxBody.hasExpirationTime()) {
      contractUpdateBodySize += (LONG_SIZE);
    }

    if (contractUpdateTxBody.hasAutoRenewPeriod()) {
      contractUpdateBodySize += (LONG_SIZE);
    }

    if (contractUpdateTxBody.hasAdminKey()) {
      contractUpdateBodySize += getAccountKeyStorageSize(contractUpdateTxBody.getAdminKey());
    }

    if (contractUpdateTxBody.getMemo() != null) {
      contractUpdateBodySize += contractUpdateTxBody.getMemoBytes().size();
    }

    return contractUpdateBodySize;

  }

  /**
   * This method returns total bytes in Contract Call body Transaction
   */
  private int getContractCallBodyTxSize(TransactionBody txBody) {
    /*
     * ContractID contractID - 3 * LONG_SIZE int64 gas - LONG_SIZE int64 amount - LONG_SIZE bytes
     * functionParameters - calculated value
     *
     */

    int contractCallBodySize = BASIC_ACCOUNT_SIZE + LONG_SIZE;

    ContractCallTransactionBody contractCallTxBody = txBody.getContractCall();

    if (contractCallTxBody.getFunctionParameters() != null) {
      contractCallBodySize += contractCallTxBody.getFunctionParameters().size();
    }

    if (contractCallTxBody.getAmount() != 0) {
      contractCallBodySize += LONG_SIZE;
    }

    return contractCallBodySize;
  }


  /**
   * This method returns the Fee Matrices for Contract Info Query
   */
  public FeeData getContractInfoQueryFeeMatrices(Key key, ResponseType responseType) {

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
     * ContractInfoQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE ContractID - 3 * LONG_SIZE
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ACCTID_SIZE;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * ContractInfo ContractID contractID - 3 * LONG_SIZE AccountID accountID - 3 * LONG_SIZE string
     * contractAccountID - SOLIDITY_ADDRESS Key adminKey - calculated value Timestamp expirationTime
     * - (LONG_SIZE) Duration autoRenewPeriod - (LONG_SIZE) int64 storage - LONG_SIZE
     *
     */

    int keySize = 0;

    if (key != null) {
        keySize = getAccountKeyStorageSize(key);
    }

    bpr = BASIC_QUERY_RES_HEADER + getStateProofSize(responseType); 

    sbpr = BASIC_CONTRACT_INFO_SIZE + keySize ;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }


  /**
   * This method returns the Fee Matrices for Contract Byte Code Query
   */
  public FeeData getContractByteCodeQueryFeeMatrices(int byteCodeSize, ResponseType responseType) {

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
     * ContractGetBytecodeQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE ContractID - 3 * LONG_SIZE
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ACCTID_SIZE;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * bytes bytescode - calculated value
     *
     */

    bpr = BASIC_QUERY_RES_HEADER + getStateProofSize(responseType); 

    sbpr = byteCodeSize;

    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }

  /**
   *
   */
  public FeeData getContractSolidityIDQueryFeeMatrices(ResponseType responseType) {

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
     * GetBySolidityIDQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE string solidityID - SOLIDITY_ADDRESS
     */

    bpt = BASIC_QUERY_HEADER + SOLIDITY_ADDRESS;
    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * ResponseHeader header = 1; //standard response from node to client, including the requested
     * fields: cost, or state proof, or both, or neither AccountID accountID = 2; // a
     * cryptocurrency account FileID fileID = 3; // a file ContractID contractID = 4;
     *
     */

    bpr = BASIC_QUERY_RES_HEADER + getStateProofSize(responseType) + BASIC_ACCTID_SIZE;


    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);

  }

  public FeeData getContractRecordsQueryFeeMatrices(List<TransactionRecord> transRecord, ResponseType responseType) {
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
     * ContractGetRecordsQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
     * Transaction processing) ResponseType - INT_SIZE ContractID - 3 * LONG_SIZE
     *
     */

    bpt = BASIC_QUERY_HEADER + BASIC_ACCTID_SIZE;

    /*
     *
     * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
     *
     * ContractGetRecordsResponse { ResponseHeader header = 1; //standard response from node to
     * client, including the requested fields: cost, or state proof, or both, or neither ContractID
     * contractID = 2; // the smart contract instance that this record is for repeated
     * TransactionRecord records = 3; // list of records, each with contractCreateResult or
     * contractCallResult as its body }
     *
     */
    int txRecordListsize = 0;
    if (transRecord != null) {
      for (TransactionRecord record : transRecord) {
        txRecordListsize = txRecordListsize + getTransactionRecordSize(record);
      }
    }
    bpr = BASIC_QUERY_RES_HEADER + txRecordListsize + getStateProofSize(responseType);

   
    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getQueryFeeDataMatrices(feeMatrices);
  }

  

  /**
   * Renewal Fee Metrics for SmartContract
   */
  public FeeData getSmartContractRenewalFeeMatrices(long autoRenewal, long storageBytes) {

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;


    /*
     * long balance - LONG_SIZE long receiverThreshold - LONG_SIZE long senderThreshold - LONG_SIZE
     * boolean receiverSigRequired - BOOL_SIZE Key accountKeys - 3 * LONG_SIZE AccountID
     * proxyAccount - 3 * LONG_SIZE long autoRenewPeriod - LONG_SIZE boolean deleted - BOOL_SIZE
     */

    rbs = (7 * LONG_SIZE + 2 * BOOL_SIZE + 3 * LONG_SIZE) * autoRenewal;

    // sbs - Storage bytes seconds
    sbs = storageBytes * autoRenewal;
    
  
    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, DEFAULT_PAYER_ACC_SIG_COUNT,DEFAULT_RBS_NETWORK);

  }

  private long getContractUpdateStorageBytesSec(TransactionBody txBody,
      Timestamp contractExpiryTime) {
    long storageSize = 0;
    ContractUpdateTransactionBody contractUpdateTxBody = txBody.getContractUpdateInstance();
    if (contractUpdateTxBody.hasAdminKey()) {
      storageSize += getAccountKeyStorageSize(contractUpdateTxBody.getAdminKey());
    }
    if (contractUpdateTxBody.getMemo() != null) {
      storageSize += contractUpdateTxBody.getMemoBytes().size();
    }
    Instant expirationTime = RequestBuilder.convertProtoTimeStamp(contractExpiryTime);
    Timestamp txValidStartTimestamp = txBody.getTransactionID().getTransactionValidStart();
    Instant txValidStartTime = RequestBuilder.convertProtoTimeStamp(txValidStartTimestamp);
    Duration duration = Duration.between(txValidStartTime, expirationTime);
    long seconds = duration.getSeconds();
    storageSize = storageSize * seconds;
    return storageSize;
  }


  public FeeData getContractDeleteTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj)
      throws InvalidTxBodyException {

    if (txBody == null || !txBody.hasContractDeleteInstance()) {
      throw new InvalidTxBodyException("ContractDelete Tx Body not available for Fee Calculation");
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
    /*
     * ContractID contractID = 3 * LONG_SIZE oneof obtainers { AccountID transferAccountID = 3 *
     * LONG_SIZE ContractID transferContractID=3 * LONG_SIZE }
     */

    bpt = txBodySize + BASIC_ACCTID_SIZE + BASIC_ACCTID_SIZE + sigValObj.getSignatureSize();

    // vpt - verifications per transactions
    vpt = sigValObj.getTotalSigCount();

    bpr = INT_SIZE;

    rbs =  getBaseTransactionRecordSize(txBody) * RECIEPT_STORAGE_TIME_SEC;
    long rbsNetwork = getDefaultRBHNetworkSize() + 3 *(LONG_SIZE) * (RECIEPT_STORAGE_TIME_SEC);

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(),rbsNetwork);
  }


}
