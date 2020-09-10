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
import com.hederahashgraph.exception.InvalidTxBodyException;

import java.math.BigInteger;
import java.util.List;

/**
 * This is the base class for building Fee Matrices and calculating the Total as well as specific
 * component Fee for a given Transaction or Query. It includes common methods which is used to
 * calculate Fee for Crypto, File and Smart Contracts Transactions and Query
 */


public class FeeBuilder {

  public static final int LONG_SIZE = 8;
  public static final int FEE_MATRICES_CONST = 1;
  public static final int INT_SIZE = 4;
  public static final int BOOL_SIZE = 4;
  public static final int SOLIDITY_ADDRESS = 20;
  public static final int KEY_SIZE = 32;
  public static final int TX_HASH_SIZE = 48;
  public static final int DEFAULT_PAYER_ACC_SIG_COUNT = 0;
  public static final int RECIEPT_STORAGE_TIME_SEC = 180;
  public static final int THRESHOLD_STORAGE_TIME_SEC = 90000;
  public static final int DEFAULT_RBS_NETWORK = 0;
  public static final int FEE_DIVISOR_FACTOR = 1000;
  public static final int SIGNATURE_SIZE = 64;
  public static final int HRS_DIVISOR = 3600;
  public static final int BASIC_ENTITY_ID_SIZE = (3 * LONG_SIZE);
  public static final int BASIC_ACCT_AMT_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
  public static final int BASIC_TX_ID_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
  public static final int EXCHANGE_RATE_SIZE = 2 * INT_SIZE + LONG_SIZE;
  /**
   * Fields included: status, exchangeRate.
   */
  public static final int BASIC_RECEIPT_SIZE = INT_SIZE + 2 * EXCHANGE_RATE_SIZE;
  /**
   * Fields included: transactionID, nodeAccountID, transactionFee, transactionValidDuration, generateRecord
   */
  public static final int BASIC_TX_BODY_SIZE =
      BASIC_ENTITY_ID_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;
  public static final int STATE_PROOF_SIZE = 2000;
  public static final int BASE_FILEINFO_SIZE =
      BASIC_ENTITY_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;
  public static final int BASIC_ACCOUNT_SIZE = 8 * LONG_SIZE + BOOL_SIZE;
  /**
   * Fields included: nodeTransactionPrecheckCode, responseType, cost
   */
  public static final int BASIC_QUERY_RES_HEADER = 2 * INT_SIZE + LONG_SIZE;
  public static final int BASIC_QUERY_HEADER = 212;
  public static final int BASIC_CONTRACT_CREATE_SIZE = BASIC_ENTITY_ID_SIZE + 6 * LONG_SIZE;
  public static final int BASIC_CONTRACT_INFO_SIZE =
      2 * BASIC_ENTITY_ID_SIZE + SOLIDITY_ADDRESS + BASIC_TX_ID_SIZE;
  /**
   * Fields included in size: receipt (basic size), transactionHash, consensusTimestamp, transactionID
   * transactionFee.
   */
  public static final int BASIC_TX_RECORD_SIZE =
      BASIC_RECEIPT_SIZE + TX_HASH_SIZE + LONG_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE;


  /**
   * This method calculates Fee for specific component (Noe/Network/Service) based upon param
   * componentCoefficients and componentMetrics
   */
  public static long getComponentFeeInTinyCents(FeeComponents componentCoefficients,
      FeeComponents componentMetrics) {

    long bytesUsageFee = componentCoefficients.getBpt() * componentMetrics.getBpt();
    long verificationFee = componentCoefficients.getVpt() * componentMetrics.getVpt();
    long ramStorageFee = componentCoefficients.getRbh() * componentMetrics.getRbh();
    long storageFee = componentCoefficients.getSbh() * componentMetrics.getSbh();
    long evmGasFee = componentCoefficients.getGas() * componentMetrics.getGas();
    long txValueFee = Math.round((componentCoefficients.getTv() * componentMetrics.getTv()) / 1000);
    long bytesResponseFee = componentCoefficients.getBpr() * componentMetrics.getBpr();
    long storageBytesResponseFee = componentCoefficients.getSbpr() * componentMetrics.getSbpr();
    long componentUsage = componentCoefficients.getConstant() * componentMetrics.getConstant();

    long totalComponentFee = componentUsage + (bytesUsageFee + verificationFee + ramStorageFee
        + storageFee + evmGasFee + txValueFee + bytesResponseFee + storageBytesResponseFee);

    if (totalComponentFee < componentCoefficients.getMin()) {
      totalComponentFee = componentCoefficients.getMin();
    } else if (totalComponentFee > componentCoefficients.getMax()) {
      totalComponentFee = componentCoefficients.getMax();
    }
    return Math.max(totalComponentFee > 0 ? 1 : 0, (totalComponentFee) / FEE_DIVISOR_FACTOR);
  }


  /**
   * This method calculates Total Fee for Transaction or Query and returns the value in tinyBars
   */
  public static long getTotalFeeforRequest(FeeData feeCoefficients, FeeData componentMetrics,
      ExchangeRate exchangeRate) {

    FeeObject feeObject = getFeeObject(feeCoefficients, componentMetrics,exchangeRate);
    long totalFee = feeObject.getServiceFee() + feeObject.getNodeFee() + feeObject.getNetworkFee();
    return totalFee;
  }

  public static FeeObject getFeeObject(FeeData feeData, FeeData feeMatrices,
      ExchangeRate exchangeRate) {
    // get the Network Fee
    long networkFee = getComponentFeeInTinyCents(feeData.getNetworkdata(), feeMatrices.getNetworkdata());
    long nodeFee = getComponentFeeInTinyCents(feeData.getNodedata(), feeMatrices.getNodedata());
    long serviceFee = getComponentFeeInTinyCents(feeData.getServicedata(), feeMatrices.getServicedata());
    // convert the Fee to tiny hbars
    networkFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, networkFee);
    nodeFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, nodeFee);
    serviceFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, serviceFee);
    return new FeeObject(nodeFee, networkFee, serviceFee);
  }


  /**
   * This method calculates the common bytes included in a every transaction. Common bytes only
   * differ based upon memo field.
   * <p>
   * Common fields in all transaction:
   * <p>
   * <ul>
   *     <li>TransactionID transactionID - BASIC_ENTITY_ID_SIZE (accountId) + LONG_SIZE (transactionValidStart)</li>
   *     <li>AccountID nodeAccountID - BASIC_ENTITY_ID_SIZE</li>
   *     <li>uint64 transactionFee - LONG_SIZE</li>
   *     <li>Duration transactionValidDuration - (LONG_SIZE)</li>
   *     <li>bool generateRecord - BOOL_SIZE</li>
   *     <li>bytes string memo - get memo size from transaction</li>
   * </ul>
   */
  public static int getCommonTransactionBodyBytes(TransactionBody txBody) throws InvalidTxBodyException {
    if (txBody == null) {
      throw new InvalidTxBodyException("Transaction Body not available for Fee Calculation");
    }
    int memoSize = 0;
    if (txBody.getMemo() != null) {
      memoSize = txBody.getMemoBytes().size();
    }
    return BASIC_TX_BODY_SIZE + memoSize;
  }

  /**
   * This method is invoked by individual Fee builder classes to calculated the number of signatures
   * in transaction.
   */
  public static long getVPT(Transaction tx) {
    // need to verify recursive depth of signatures
    if (tx == null) {
      return 0;
    }
    Signature sig = Signature.newBuilder().setSignatureList(tx.getSigs()).build();
    return calculateNoOfSigs(sig, 0);
  }

  public static int calculateNoOfSigsInList(SignatureList signatureList) {
    if (signatureList == null) {
      return 0;
    }
    Signature sig = Signature.newBuilder().setSignatureList(signatureList).build();
    return calculateNoOfSigs(sig, 0);
  }

  /**
   * This method returns the gas converted to hashbar units. (This needs to be updated)
   */
  public static long getGas(Transaction tx) throws Exception {
    long gas = 0;
    TransactionBody body;
    if (tx.hasBody()) {
      body = tx.getBody();
    } else {
      body = TransactionBody.parseFrom(tx.getBodyBytes());
    }
    if (body.hasContractCreateInstance()) {
      gas = body.getContractCreateInstance().getGas();
    } else if (body.hasContractCall()) {
      gas = body.getContractCall().getGas();
    }
    return gas * 1; // 1 Gas = 1 hashbars - need to get from standard configuration
  }

  /**
   * This method returns the Key size in bytes
   */
  public static int getAccountKeyStorageSize(Key key) {

    if (key == null) {
      return 0;
    }
    if (key == Key.getDefaultInstance()) {
      return 0;
    }
    int keyStorageSize = 0;
    try {
      int[] countKeyMetatData = {0, 0};
      countKeyMetatData = calculateKeysMetadata(key, countKeyMetatData);
      keyStorageSize = countKeyMetatData[0] * KEY_SIZE + countKeyMetatData[1] * INT_SIZE;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return keyStorageSize;
  }


  /**
   * This method calculates number of signature in Signature object
   */
  private static int calculateNoOfSigs(Signature sig, int count) {
    if (sig.hasSignatureList()) {
      List<Signature> sigList = sig.getSignatureList().getSigsList();
      for (int i = 0; i < sigList.size(); i++) {
        count = calculateNoOfSigs(sigList.get(i), count);
      }
    } else if (sig.hasThresholdSignature()) {
      List<Signature> sigList = sig.getThresholdSignature().getSigs().getSigsList();
      for (int i = 0; i < sigList.size(); i++) {
        count = calculateNoOfSigs(sigList.get(i), count);
      }
    } else {
      count++;
    }
    return count;
  }

  /**
   * This method calculates number of keys
   */
  public static int[] calculateKeysMetadata(Key key, int[] count) {
    if (key.hasKeyList()) {
      List<Key> keyList = key.getKeyList().getKeysList();
      for (int i = 0; i < keyList.size(); i++) {
        count = calculateKeysMetadata(keyList.get(i), count);
      }
    } else if (key.hasThresholdKey()) {
      List<Key> keyList = key.getThresholdKey().getKeys().getKeysList();
      count[1]++;
      for (int i = 0; i < keyList.size(); i++) {
        count = calculateKeysMetadata(keyList.get(i), count);
      }
    } else {
      count[0]++;
    }
    return count;
  }

  /**
   * This method returns the Fee Matrices for querying based upon ID (Account / File / Smart
   * Contract)
   */
  public static FeeData getCostForQueryByIDOnly() {

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
     * Query QueryHeader Transaction - CryptoTransfer - (will be taken care in Transaction
     * processing) ResponseType - INT_SIZE ID - BASIC_ENTITY_ID_SIZE
     *
     */

    bpt = INT_SIZE + BASIC_ENTITY_ID_SIZE;

    /*
     * bpr = Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes uint64
     * cost - 8 bytes
     */

    bpr = BASIC_QUERY_RES_HEADER;
    FeeComponents feeMatrices = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    /*return getQueryFeeDataMatrices(feeMatrices);*/
    return FeeData.getDefaultInstance();

  }


  /**
   * It returns the default Fee Matrices
   */
  public static FeeComponents getDefaultMatrices() {
    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(0).setVpt(0).setRbh(0)
        .setSbh(0).setGas(0).setTv(0).setBpr(0).setSbpr(0).build();
    return feeMatricesForTx;
  }

  public static int sizeOfLiveHashKeyStorage(Key key) {

    int keyStorageSize = 0;
    try {
      int[] countKeyMetatData = {0, 0};
      countKeyMetatData = calculateKeysMetadata(key, countKeyMetatData);
      keyStorageSize = countKeyMetatData[0] * KEY_SIZE + countKeyMetatData[1] * INT_SIZE;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return keyStorageSize;
  }

  public static int getSignatureCount(Transaction transaction) {
    if (transaction.hasSigMap()) {
      return transaction.getSigMap().getSigPairCount();
    } else if (transaction.hasSigs()) {
      Signature sig = Signature.newBuilder().setSignatureList(transaction.getSigs()).build();
      return calculateNoOfSigs(sig, 0);
    } else {
      return 0;
    }
  }

  public static int getSignatureSize(Transaction transaction) {
    if (transaction.hasSigMap()) {
      return transaction.getSigMap().toByteArray().length;
    } else if (transaction.hasSigs()) {
      return transaction.getSigs().toByteArray().length;
    } else {
      return 0;
    }
  }

  /**
   * Convert tinyCents to tinybars
   *
   * @return tinyHbars
   */
  public static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
    BigInteger hbarMultiplier = BigInteger.valueOf(Long.valueOf(exchangeRate.getHbarEquiv()));
    BigInteger centsDivisor = BigInteger.valueOf(Long.valueOf(exchangeRate.getCentEquiv()));
    BigInteger feeInBigInt = BigInteger.valueOf(tinyCentsFee);
    feeInBigInt = feeInBigInt.multiply(hbarMultiplier);
    feeInBigInt = feeInBigInt.divide(centsDivisor);
    return feeInBigInt.longValue();
  }


  public static FeeData getFeeDataMatrices(FeeComponents feeComponents, int payerVpt, long rbsNetwork) {

    long rbh = Math.max(feeComponents.getRbh() > 0 ? 1 : 0, feeComponents.getRbh() / HRS_DIVISOR);
    long sbh = Math.max(feeComponents.getSbh() > 0 ? 1 : 0, feeComponents.getSbh() / HRS_DIVISOR);
    long rbhNetwork = Math.max(rbsNetwork > 0 ? 1 : 0, (rbsNetwork) / HRS_DIVISOR);
    FeeComponents feeMatricesForTxService =
        FeeComponents.newBuilder().setConstant(FEE_MATRICES_CONST)
            .setRbh(rbh).setSbh(sbh)
            .setTv(feeComponents.getTv()).build();

    FeeComponents feeMatricesForTxNetwork =
        FeeComponents.newBuilder().setConstant(FEE_MATRICES_CONST)
            .setBpt(feeComponents.getBpt()).setVpt(feeComponents.getVpt())
            .setRbh(rbhNetwork).build();

    FeeComponents feeMatricesForTxNode = FeeComponents.newBuilder()
        .setConstant(FEE_MATRICES_CONST)
        .setBpt(feeComponents.getBpt())
        .setVpt(payerVpt)
        .setBpr(feeComponents.getBpr()).setSbpr(feeComponents.getSbpr()).build();

    FeeData feeDataMatrices = FeeData.newBuilder().setNetworkdata(feeMatricesForTxNetwork)
        .setNodedata(feeMatricesForTxNode).setServicedata(feeMatricesForTxService).build();

    return feeDataMatrices;

  }


  public static FeeData getQueryFeeDataMatrices(FeeComponents feeComponents) {

    FeeComponents feeMatricesForTxService = FeeComponents.getDefaultInstance();

    FeeComponents feeMatricesForTxNetwork = FeeComponents.getDefaultInstance();

    FeeComponents feeMatricesForTxNode = FeeComponents.newBuilder()
        .setConstant(FEE_MATRICES_CONST)
        .setBpt(feeComponents.getBpt())
        .setBpr(feeComponents.getBpr())
        .setSbpr(feeComponents.getSbpr())
        .build();

    FeeData feeDataMatrices = FeeData.newBuilder().setNetworkdata(feeMatricesForTxNetwork)
        .setNodedata(feeMatricesForTxNode).setServicedata(feeMatricesForTxService).build();

    return feeDataMatrices;

  }

  public static long getDefaultRBHNetworkSize() {
    return (BASIC_RECEIPT_SIZE) * (RECIEPT_STORAGE_TIME_SEC);
  }
  
 /* public FeeData getCreateTransactionRecordFeeMatrices(int txRecordSize, int time) {

    long bpt = 0;
    long vpt = 0;
    long rbs = 0;
    long sbs = 0;
    long gas = 0;
    long tv = 0;
    long bpr = 0;
    long sbpr = 0;

    
    rbs = (txRecordSize) * time;
    // sbs - Stoarge bytes seconds
    sbs = 0; // Transaction Record fee is charged when they are saved!, so no fee is required at

    FeeComponents feeMatricesForTx = FeeComponents.newBuilder().setBpt(bpt).setVpt(vpt).setRbh(rbs)
        .setSbh(sbs).setGas(gas).setTv(tv).setBpr(bpr).setSbpr(sbpr).build();

    return getFeeDataMatrices(feeMatricesForTx, DEFAULT_PAYER_ACC_SIG_COUNT);

  }*/

  // does not account for transferlist due to threshold record generation
  public static int getBaseTransactionRecordSize(TransactionBody txBody) {
    int txRecordSize = BASIC_TX_RECORD_SIZE;
    if (txBody.getMemo() != null) {
      txRecordSize = txRecordSize + txBody.getMemoBytes().size();
    }
    // TransferList size
    if (txBody.hasCryptoTransfer()) {
      txRecordSize = txRecordSize
          + txBody.getCryptoTransfer().getTransfers().getAccountAmountsCount()
          * (BASIC_ACCT_AMT_SIZE);
    }
    return txRecordSize;
  }

  public static long getTxRecordUsageRBH(TransactionRecord txRecord, int timeInSeconds) {
	if(txRecord == null) return 0;
	long txRecordSize = getTransactionRecordSize(txRecord);    
    return (txRecordSize) * getHoursFromSec(timeInSeconds);
  }
  
  
  public static int getHoursFromSec(int valueInSeconds) {	  
	  return valueInSeconds==0 ? 0 : Math.max(1,(valueInSeconds/HRS_DIVISOR));
  }


  public static int getTransactionRecordSize(TransactionRecord txRecord) {
	
	if(txRecord == null) return 0;
	
    int txRecordSize = BASIC_TX_RECORD_SIZE;

    if (txRecord.hasContractCallResult()) {
      txRecordSize =
          txRecordSize + getContractFunctionSize(txRecord.getContractCallResult());
    } else if (txRecord.hasContractCreateResult()) {
      txRecordSize =
          txRecordSize + getContractFunctionSize(txRecord.getContractCreateResult());
    }
    if (txRecord.hasTransferList()) {
      txRecordSize =
          txRecordSize
              + (txRecord.getTransferList().getAccountAmountsCount()) * (BASIC_ACCT_AMT_SIZE);
    }

    int memoBytesSize = 0;
    if (txRecord.getMemo() != null) {
      memoBytesSize = txRecord.getMemoBytes().size();
    }

    return txRecordSize + memoBytesSize;

  }

  public static int getContractFunctionSize(ContractFunctionResult contFuncResult) {

    int contResult = 0;

    if (contFuncResult.getContractCallResult() != null) {
      contResult = contFuncResult.getContractCallResult().size();
    }

    if (contFuncResult.getErrorMessage() != null) {
      contResult = contResult + contFuncResult.getErrorMessageBytes().size();
    }

    if (contFuncResult.getBloom() != null) {
      contResult = contResult + contFuncResult.getBloom().size();
    }
    contResult = contResult + LONG_SIZE + 2 * LONG_SIZE;

    return contResult;
  }
  
  
  public static long getTransactionRecordFeeInTinyCents(TransactionRecord txRecord,long feeCoeffRBH, int timeInSec) {
	  if(txRecord == null) return 0;
	  long txRecordUsageRBH = getTxRecordUsageRBH(txRecord, timeInSec);
	  long rawFee = txRecordUsageRBH * feeCoeffRBH;
	  return Math.max(rawFee > 0 ? 1 : 0, (rawFee) / FEE_DIVISOR_FACTOR);	  
  }


  public static int getQueryTransactionSize() {
    int commonTxBodyBytes =
            BASIC_ENTITY_ID_SIZE + (LONG_SIZE) + BASIC_ENTITY_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;
    return (commonTxBodyBytes + BASIC_TX_ID_SIZE + SIGNATURE_SIZE + INT_SIZE);
  }

  public static int liveHashSize(List<LiveHash> liveHashes) {
    int liveHashsKeySize = 0;
    int liveHashDataSize = 0;
    int liveHashsAccountID = 0;

    if (liveHashes != null) {
      int liveHashsListSize = liveHashes.size();
      liveHashDataSize = TX_HASH_SIZE * liveHashsListSize;
      liveHashsAccountID = (BASIC_ENTITY_ID_SIZE) * liveHashsListSize;
      for (LiveHash liveHashs : liveHashes) {
        List<Key> keyList = liveHashs.getKeys().getKeysList();
        for (Key key : keyList) {
          liveHashsKeySize += getAccountKeyStorageSize(key);
        }
      }
    }

    return (liveHashsKeySize + liveHashDataSize + liveHashsAccountID);
  }

  public static int getStateProofSize(ResponseType responseType) {
    return (responseType == ResponseType.ANSWER_STATE_PROOF
        || responseType == ResponseType.COST_ANSWER_STATE_PROOF) ? STATE_PROOF_SIZE : 0;
  }


}
