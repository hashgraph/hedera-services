package com.hedera.services.legacy.core;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FeeClient {

  private static int FEE_DIVISOR_TOTINYBARS = 12000;
  private static ExchangeRate exchangeRate = ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(12).build();
  private static Map<HederaFunctionality, FeeData> feeSchMap = null;

  private static final Logger log = LogManager.getLogger(FeeClient.class);
  public static void main(String args[]) {

    long minVal = Long.MIN_VALUE;
    long val = minVal +  (-minVal);
    log.info("The addition "+ val);

    getFeeScheduleMap();
  }

  public static void initialize(int hbarEquiv, int centEquiv, byte[] feeSchBytes) {
    FEE_DIVISOR_TOTINYBARS = 1000 * centEquiv / hbarEquiv;
    exchangeRate = ExchangeRate.newBuilder().setHbarEquiv(hbarEquiv).setCentEquiv(centEquiv)
            .build();

    try {
      CurrentAndNextFeeSchedule feeSch = CurrentAndNextFeeSchedule.parseFrom(feeSchBytes);
      List<TransactionFeeSchedule> transFeeSchList =
              feeSch.getCurrentFeeSchedule().getTransactionFeeScheduleList();
      feeSchMap = new HashMap<>();
      for (TransactionFeeSchedule transSch : transFeeSchList) {
        feeSchMap.put(transSch.getHederaFunctionality(), transSch.getFeeData());
      }
    } catch (InvalidProtocolBufferException ex) {
      System.out.print("ERROR: Exception while decoding Fee file");
    }
  }
  public static Map<HederaFunctionality, FeeData> getFeeScheduleMap() {
    Map<HederaFunctionality, FeeData> feeSchMap = null;
    try {
      if (feeSchMap == null) {
        feeSchMap = new HashMap<>();
        Path pathFeeSch =
            Paths.get(TestHelper.class.getClassLoader().getResource("feeSchedule.txt").toURI());
        File feeSchFile = new File(pathFeeSch.toString());
        InputStream fis = new FileInputStream(feeSchFile);
        byte[] fileBytes = new byte[(int) feeSchFile.length()];
        fis.read(fileBytes);
        CurrentAndNextFeeSchedule feeSch = CurrentAndNextFeeSchedule.parseFrom(fileBytes);
        List<TransactionFeeSchedule> transFeeSchList =
            feeSch.getCurrentFeeSchedule().getTransactionFeeScheduleList();
        for (TransactionFeeSchedule transSch : transFeeSchList) {
          feeSchMap.put(transSch.getHederaFunctionality(), transSch.getFeeData());
        }

      }
    } catch (Exception e) {
      log.info("Exception while reading Fee file: "+e.getMessage());
    }
    return feeSchMap;
  }

  public static long getTransferFee(Transaction transaction, int payerAcctSigCount)
      throws Exception {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoTransfer);
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);

    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
        signatureSize);

    FeeData feeMatrices = crBuilder.getCryptoTransferTxFeeMatrices(txBody, sigValueObj);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getBalanceQueryFee() {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoGetAccountBalance);
    FeeData feeMatrices = crBuilder.getBalanceQueryFeeMatrices(ResponseType.ANSWER_ONLY);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getFeeByID(HederaFunctionality hederaFunctionality) {
    FeeBuilder crBuilder = new FeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(hederaFunctionality);
    FeeData feeMatrices = crBuilder.getCostForQueryByIDOnly();
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }


  public static long getCreateAccountFee(Transaction transaction, int payerAcctSigCount)
      throws Exception {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoCreate);
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
        signatureSize);
    FeeData feeMatrices = crBuilder.getCryptoCreateTxFeeMatrices(txBody, sigValueObj);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getCreateTransferFee(Transaction transaction, int payerAcctSigCount)
          throws Exception {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoTransfer);
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
            signatureSize);
    FeeData feeMatrices = crBuilder.getCryptoTransferTxFeeMatrices(txBody, sigValueObj);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getCreateUpdateFee(Transaction transaction, int payerAcctSigCount,
          Timestamp expirationTimeStamp, Key existingKey)
          throws Exception {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoUpdate);
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
            signatureSize);
    FeeData feeMatrices = crBuilder.getCryptoUpdateTxFeeMatrices(txBody, sigValueObj, expirationTimeStamp, existingKey);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }



  public static long getCostForGettingTxRecord() {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    FeeData feeMatrices = crBuilder.getCostTransactionRecordQueryFeeMatrices();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.TransactionGetRecord);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getCostForGettingAccountInfo() {
    CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
    FeeData feeMatrices = crBuilder.getCostCryptoAccountInfoQueryFeeMatrices();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoGetInfo);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getCostContractCallLocalFee(int funcParamSize) {
    SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
    FeeData feeMatrices = crBuilder.getCostContractCallLocalFeeMatrices(funcParamSize);
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.ContractCallLocal);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getFeegetBySolidityID() {
    SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
    FeeData feeMatrices = crBuilder.getContractSolidityIDQueryFeeMatrices(ResponseType.ANSWER_ONLY);
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.GetBySolidityID);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getCostContractCallFee(Transaction transaction, int payerAcctSigCount)
      throws Exception {
    SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
        signatureSize);
    FeeData feeMatrices = crBuilder.getContractCallTxFeeMatrices(txBody, sigValueObj);
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.ContractCall);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }


  public static long getContractCreateFee(Transaction transaction, int payerAcctSigCount)
      throws Exception {
    SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
        signatureSize);
    FeeData feeMatrices = crBuilder.getContractCreateTxFeeMatrices(txBody, sigValueObj);
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.ContractCreate);
    return crBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getMaxFee() {
    // currently all functionalities have same max fee so just taking CryptoCreate
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.CryptoCreate);
    return ((feeData.getNodedata().getMax() + feeData.getNetworkdata().getMax()
        + feeData.getServicedata().getMax()))/FEE_DIVISOR_TOTINYBARS;
  }

  public static long getSystemDeleteFee(Transaction transaction, int payerAcctSigCount)
      throws Exception {
    FileFeeBuilder fileFeeBuilder = new FileFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.SystemDelete);
    TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
    int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
    int signatureSize = FeeBuilder.getSignatureSize(transaction);
    SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
        signatureSize);
    FeeData feeMatrices = fileFeeBuilder.getSystemDeleteFileTxFeeMatrices(txBody, sigValueObj);
    return (fileFeeBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate));
  }

  public static long getFileInfoQueryFee(KeyList keys) {
    FileFeeBuilder fileFeeBuilder = new FileFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.FileGetInfo);
    FeeData feeMatrices = fileFeeBuilder.getFileInfoQueryFeeMatrices(keys,ResponseType.ANSWER_ONLY);
    return fileFeeBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }

  public static long getFileContentQueryFee(int contentSize) {
    FileFeeBuilder fileFeeBuilder = new FileFeeBuilder();
    Map<HederaFunctionality, FeeData> feeSchMap = getFeeScheduleMap();
    FeeData feeData = feeSchMap.get(HederaFunctionality.FileGetContents);
    FeeData feeMatrices = fileFeeBuilder.getFileContentQueryFeeMatrices(contentSize,ResponseType.ANSWER_ONLY);
    return fileFeeBuilder.getTotalFeeforRequest(feeData, feeMatrices,exchangeRate);
  }
}
