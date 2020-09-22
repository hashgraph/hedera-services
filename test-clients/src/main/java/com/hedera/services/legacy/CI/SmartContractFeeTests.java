package com.hedera.services.legacy.CI;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.regression.BaseFeeTests;
import com.hedera.services.legacy.regression.Utilities;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.SmartContractServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Test Client for SmartContract fee tests
 * -Contract Create Fee Test
 * -Contract Call Fee Test
 * -Contract GetInfo Fee Test
 * -Contract GetByteCode Fee Test
 * -Contract GetRecord Fee Test
 * @author Tirupathi Mandala Created on 2019-06-19
 */
public class SmartContractFeeTests extends BaseFeeTests {

  private static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
  private static final String BALANCE_OF_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String DECIMALS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String APPROVE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String TRANSFER_FROM_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"},{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SYMBOL_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

  private static final Logger log = LogManager.getLogger(SmartContractFeeTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  protected Random random = new Random();
  SmartContractServiceTest fit = new SmartContractServiceTest(testConfigFilePath);
  public static long CONTRACT_CREATE_SUCCESS_GAS = 2_000_000L;
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static List<String> testResults = new ArrayList<>();


  public SmartContractFeeTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }


  public static void main(String[] args) throws Throwable {
    SmartContractFeeTests tester = new SmartContractFeeTests(testConfigFilePath);
    tester.setup(args);
    accountKeys.put(account_1, CryptoServiceTest.getAccountPrivateKeys(account_1));
    accountKeys.put(queryPayerId, CryptoServiceTest.getAccountPrivateKeys(queryPayerId));

    tester.smartContractCreateFeeTest();
    tester.smartContractCreateFeeTest_90Days();
    tester.smartContractCallFeeTest();
    tester.contractCallFeeTest_Param_1000_Gas_5000();
//    tester.smartContractDeleteFeeTest();
    //  tester.callLocalFeeTest();
    tester.smartContractGetInfoFeeTest(1, 1, 30);
    tester.smartContractGetInfoFeeTest(10, 10, 90);
    tester.smartContractGetRecordsFeeTest(1, 1, 30);
    tester.smartContractGetRecordsFeeTest(10, 10, 90);
    tester.smartContractGetByteCodeFeeTest(1, 1, 30);
    tester.smartContractGetByteCodeFeeTest(10, 10, 90);
    log.info("------------ Test Results --------------");
    testResults.stream().forEach(a -> log.info(a));

  }

  public void smartContractCreateFeeTest() throws Throwable {
    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, queryPayerId, 10000000000000L);

    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 10000000000000L);
    log.info("Account created successfully");
    String fileName = "simpleStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    log.info("Smart Contract file uploaded successfully");
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 30, CONTRACT_CREATE_SUCCESS_GAS, 0L, 100L, null,
        accountKeyPairs.get(crAccount));
    TransactionResponse response = sstub.createContract(sampleStorageTransaction);
    System.out.println(
        " createContractWithOptions Pre Check Response :: " + response
            .getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(300);
    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      ContractID createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);

    long transactionFee = getTransactionFee(sampleStorageTransaction);
    long recordTransactionFee = getTransactionFeeFromRecord(sampleStorageTransaction, queryPayerId,
        "30 Days");
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("SmartContract Create 30 Days transactionFee=" + transactionFee);
    log.info("SmartContract Create 30 Days RecordTransactionFee=" + recordTransactionFee);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = SMARTCONTRACT_CREATE_STORAGE_10_RECSIZE_10_DUR_30 + feeVariance;
    long minTransactionFee = SMARTCONTRACT_CREATE_STORAGE_10_RECSIZE_10_DUR_30 - feeVariance;
    Assert.assertTrue(maxTransactionFee > transactionFee);
    Assert.assertTrue(minTransactionFee < transactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    checkRecord(sampleStorageTransaction, queryPayerId, "30 Days");
  }

  public void smartContractCreateFeeTest_90Days() throws Throwable {

    AccountID crAccount = getMultiSigAccount(1, 1, CryptoServiceTest.DAY_SEC * 30);
    accountKeys.put(crAccount, CryptoServiceTest.getAccountPrivateKeys(crAccount));
    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 100000000000000L);
    log.info("Account created successfully");
    String fileName = "LargeStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    log.info("Smart Contract file uploaded successfully");
    CONTRACT_CREATE_SUCCESS_GAS = 250;
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 90, CONTRACT_CREATE_SUCCESS_GAS, 0L, 100000000000L, null,
        accountKeyPairs.get(crAccount));

    TransactionResponse response = sstub.createContract(sampleStorageTransaction);
    log.info(" createContractWithOptions Pre Check Response :: " +
        response.getNodeTransactionPrecheckCode().name());

    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      ContractID createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);

    long transactionFee = getTransactionFee(sampleStorageTransaction);
    long recordTransactionFee = getTransactionFeeFromRecord(sampleStorageTransaction, queryPayerId,
        "90 Days");
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("SmartContract Create 90 Days transactionFee=" + transactionFee);
    log.info("SmartContract Create 90 Days RecordTransactionFee=" + recordTransactionFee);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = SMARTCONTRACT_CREATE_STORAGE_1000_RECSIZE_1000_DUR_90 + feeVariance;
    long minTransactionFee = SMARTCONTRACT_CREATE_STORAGE_1000_RECSIZE_1000_DUR_90 - feeVariance;
    Assert.assertTrue(maxTransactionFee > transactionFee);
    Assert.assertTrue(minTransactionFee < transactionFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    checkRecord(sampleStorageTransaction, queryPayerId, "90 Days");
  }

  public void contractCallFeeTest_Param_1000_Gas_5000() throws Throwable {
    Map<String, String> tokenOwners = new HashMap<String, String>();

    KeyPair tokenIssureKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID tokenIssuer = createAccount(tokenIssureKeyPair, queryPayerId, 10000000000000000L);
    System.out.println("Token Isssuer account created");
    AccountInfo crAccInfo = getCryptoGetAccountInfo(tokenIssuer);

    String tokenIssuerEthAddress = crAccInfo.getContractAccountID();

    AccountID alice = createAccount(tokenIssuer, 10000000000000L);
    System.out.println("Alice  account created");
    AccountInfo accInfoAlice = getCryptoGetAccountInfo(alice);
    String aliceEthAddress = accInfoAlice.getContractAccountID();

    tokenOwners.put("Issuer", tokenIssuerEthAddress);
    tokenOwners.put("Alice", aliceEthAddress);

    String fileName = "octoken.bin";

    if (tokenIssuer != null) {
      FileID ocTokenCode = LargeFileUploadIT.uploadFile(tokenIssuer, fileName, tokenIssureKeyPair);
      if (ocTokenCode != null) {
        long initialSupply = 100000000L;
        long autoRenewPeriod = CustomPropertiesSingleton.getInstance().getContractDuration();
        ContractID ocTokenContract = createTokenContract(tokenIssuer, ocTokenCode, 1000000000L,
            "OpenCrowd Token", "OCT", autoRenewPeriod);
        crAccInfo = getCryptoGetAccountInfo(tokenIssuer);
        log.info("crAccInfo========>>>>>   " + crAccInfo);
        Assert.assertNotNull(ocTokenContract);
        System.out.println("@@@ Contract Adress is  " + ocTokenContract.toString());
        long tokenDecimals = decimals(ocTokenContract, tokenIssuer);
        String symbol = symbol(ocTokenContract, tokenIssuer);
        System.out.println("decimals = " + tokenDecimals);
        long tokenMultiplier = (long) Math.pow(10, tokenDecimals);
        long balanceOfTokenIssuer = balanceOf(ocTokenContract, tokenIssuerEthAddress, tokenIssuer);

        System.out.println(
            "@@@ Balance of token issuer  " + balanceOfTokenIssuer / tokenMultiplier + " " + symbol
                + "  decimals = " + tokenDecimals);
        assert (initialSupply * tokenMultiplier) == balanceOfTokenIssuer;

        System.out.println("token owner transfers 1000 tokens to Alice ");
        long gas = 2_000_000;
        TransactionRecord trRecord = transfer(ocTokenContract, tokenIssuer, aliceEthAddress,
            10 * tokenMultiplier, gas);
        long transactionFee = trRecord.getTransactionFee();
        System.out.println("transactionFee = " + transactionFee + " ,GAS = " + gas);

        log.info("--------------------Done----------------------");
      }
    }
  }


  public void smartContractCallFeeTest() throws Throwable {
    AccountID crAccount = createAccountWithListKey(queryPayerId, nodeID, 100000000000000L,
        true, true, 1);
    accountKeys.put(crAccount, CryptoServiceTest.getAccountPrivateKeys(crAccount));
    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 100000000000000L);
    log.info("Account created successfully");
    String fileName = "LargeStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    CONTRACT_CREATE_SUCCESS_GAS = 2_000_000;
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 90, CONTRACT_CREATE_SUCCESS_GAS, 0L,
        1000000000L, null, accountKeyPairs.get(crAccount));

    TransactionResponse response = sstub.createContract(sampleStorageTransaction);

    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    ContractID createdContract = null;
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    byte[] data = encodeSet(10000);
    long contractCallFee = contractCall(crAccount, createdContract, data,
        CONTRACT_CREATE_SUCCESS_GAS);
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("contractCallFee=" + contractCallFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    long feeVariance = (contractCallFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CONTRACT_CALL_FUNC_10_RECSIZE_10 + feeVariance;
    long minTransactionFee = CONTRACT_CALL_FUNC_10_RECSIZE_10 - feeVariance;
    Assert.assertTrue(maxTransactionFee > contractCallFee);
    Assert.assertTrue(minTransactionFee < contractCallFee);

  }

  public void smartContractDeleteFeeTest() throws Throwable {
    AccountID crAccount = createAccountWithListKey(queryPayerId, nodeID, 100000000000000L,
        true, true, 1);
    accountKeys.put(crAccount, CryptoServiceTest.getAccountPrivateKeys(crAccount));
    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 100000000000000L);
    log.info("Account created successfully");
    String fileName = "LargeStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    CONTRACT_CREATE_SUCCESS_GAS = 2_000_000;
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 90, CONTRACT_CREATE_SUCCESS_GAS, 0L,
        1000000000L, null, accountKeyPairs.get(crAccount));

    TransactionResponse response = sstub.createContract(sampleStorageTransaction);

    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    ContractID createdContract = null;
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    byte[] data = encodeSet(10000);
    long contractDeleteFee = contractDelete(crAccount, createdContract, data,
        CONTRACT_CREATE_SUCCESS_GAS, fileAccountKeyPair);
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("contractDeleteFee=" + contractDeleteFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    long feeVariance = (contractDeleteFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CONTRACT_CALL_FUNC_10_RECSIZE_10 + feeVariance;
    long minTransactionFee = CONTRACT_CALL_FUNC_10_RECSIZE_10 - feeVariance;
  }

  public void callLocalFeeTest() throws Throwable {
    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, queryPayerId, 100000000000000L);
    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 100000000000000L);
    log.info("Account created successfully");
    String fileName = "LargeStorage.bin";
    //String fileName = "simpleStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    CONTRACT_CREATE_SUCCESS_GAS = 2_000_000;
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 90, CONTRACT_CREATE_SUCCESS_GAS, 0L,
        1000000000L, null, accountKeyPairs.get(crAccount));

    TransactionResponse response = sstub.createContract(sampleStorageTransaction);

    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    ContractID createdContract = null;
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    byte[] data = encodeSet(1000000);

    long callLocalFee = callLocal(crAccount, createdContract, data,
        CONTRACT_CREATE_SUCCESS_GAS);

    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("callLocalFee=" + callLocalFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    long feeVariance = (callLocalFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CONTRACT_CALL_FUNC_10_RECSIZE_10 + feeVariance;
    long minTransactionFee = CONTRACT_CALL_FUNC_10_RECSIZE_10 - feeVariance;
    Assert.assertTrue(maxTransactionFee > callLocalFee);
    Assert.assertTrue(minTransactionFee < callLocalFee);

  }


  public void smartContractGetInfoFeeTest(int keyCount, int memoSize, int durationDays)
      throws Throwable {
    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, queryPayerId, 10000000000000L);

    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 10000000000000L);
    log.info("Account created successfully");
    String fileName = "simpleStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    log.info("Smart Contract file uploaded successfully");
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 30, CONTRACT_CREATE_SUCCESS_GAS, 0L,
        100L, null, accountKeyPairs.get(crAccount));
    TransactionResponse response = sstub.createContract(sampleStorageTransaction);
    System.out.println(
        " createContractWithOptions Pre Check Response :: " + response
            .getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(300);
    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    ContractID createdContract = null;
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    AccountID newAccountID = getMultiSigAccount(keyCount, memoSize, CryptoServiceTest.DAY_SEC * durationDays);
    Transaction paymentTx = CryptoServiceTest.getQueryPaymentSigned(newAccountID, nodeID, TestHelperComplex.getStringMemo(10));
    Query getContractInfoQuery = RequestBuilder
        .getContractGetInfoQuery(createdContract, paymentTx, ResponseType.COST_ANSWER);

    Response contractInfoResponse = sstub.getContractInfo(getContractInfoQuery);

    log.info("Contract Info: " + contractInfoResponse);
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);

    long transactionFee = contractInfoResponse.getContractGetInfo().getHeader().getCost();
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    String result = "SmartContract GetInfo Sig=" + keyCount + ", memo=" + memoSize + " :"
        + getTransactionFeeFromRecord(paymentTx, queryPayerId,
        "30 Days");
    testResults.add(result);
    String result2 = "Header SmartContract GetInfo Sig=" + keyCount + ", memo=" + memoSize + " :"
        + contractInfoResponse.getContractGetInfo().getHeader().getCost();
    testResults.add(result2);

    log.info(result);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = 0;
    long minTransactionFee = 0;
    if (keyCount == 1) {
      maxTransactionFee = SMARTCONTRACT_GET_INFO_SIG_1 + feeVariance;
      minTransactionFee = SMARTCONTRACT_GET_INFO_SIG_1 - feeVariance;
    } else if (keyCount == 10) {
      maxTransactionFee = SMARTCONTRACT_GET_INFO_SIG_10 + feeVariance;
      minTransactionFee = SMARTCONTRACT_GET_INFO_SIG_10 - feeVariance;
    } else {
      return;
    }
    if (paymentTx.getSigMap().getSigPairCount() != 0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void smartContractGetRecordsFeeTest(int keyCount, int memoSize, int durationDays)
      throws Throwable {
    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, queryPayerId, 10000000000000L);

    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 10000000000000L);
    log.info("Account created successfully");
    String fileName = "simpleStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    log.info("Smart Contract file uploaded successfully");
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 30, CONTRACT_CREATE_SUCCESS_GAS, 0L,
        100L, null, accountKeyPairs.get(crAccount));
    TransactionResponse response = sstub.createContract(sampleStorageTransaction);
    System.out.println(
        " createContractWithOptions Pre Check Response :: " + response
            .getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(300);
    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    ContractID createdContract = null;
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    AccountID newAccountID = getMultiSigAccount(keyCount, memoSize, CryptoServiceTest.DAY_SEC * durationDays);
    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetInfo);
    Transaction paymentTx = CryptoServiceTest.getQueryPaymentSigned(newAccountID, nodeID, TestHelperComplex.getStringMemo(10));
    Query contractRecordsQuery = RequestBuilder.getContractRecordsQuery(createdContract,
        paymentTx, ResponseType.COST_ANSWER);

    Response recordByContractID = sstub.getTxRecordByContractID(contractRecordsQuery);

    log.info("Contract Record Response: " + recordByContractID);
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);

    long transactionFee = recordByContractID.getContractGetRecordsResponse().getHeader().getCost();
    long recordTransactionFee = getTransactionFeeFromRecord(paymentTx, queryPayerId,
        "30 Days");
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("SmartContract GetRecord  RecordTransactionFee=" + recordTransactionFee);
    String result =
        "SmartContract GetRecord Sig=" + keyCount + ", memo=" + memoSize + " :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = 0;
    long minTransactionFee = 0;
    if (keyCount == 1) {
      maxTransactionFee = SMARTCONTRACT_GET_RECORD_SIG_1 + feeVariance;
      minTransactionFee = SMARTCONTRACT_GET_RECORD_SIG_1 - feeVariance;
    } else if (keyCount == 10) {
      maxTransactionFee = SMARTCONTRACT_GET_RECORD_SIG_10 + feeVariance;
      minTransactionFee = SMARTCONTRACT_GET_RECORD_SIG_10 - feeVariance;
    } else {
      return;
    }
    if (paymentTx.getSigMap().getSigPairCount() != 0) {
      Assert.assertTrue(maxTransactionFee >= transactionFee);
      Assert.assertTrue(minTransactionFee <= transactionFee);
    }
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void smartContractGetByteCodeFeeTest(int keyCount, int memoSize, int durationDays)
      throws Throwable {
    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, queryPayerId, 10000000000000L);

    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 10000000000000L);
    log.info("Account created successfully");
    String fileName = "simpleStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
    log.info("Smart Contract file uploaded successfully");
    Transaction sampleStorageTransaction = createContractWithOptions(crAccount, simpleStorageFileId,
        nodeID, CryptoServiceTest.DAY_SEC * 30, CONTRACT_CREATE_SUCCESS_GAS, 0L,
        100L, null, accountKeyPairs.get(crAccount));
    TransactionResponse response = sstub.createContract(sampleStorageTransaction);
    System.out.println(
        " createContractWithOptions Pre Check Response :: " + response
            .getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(300);
    TransactionBody createContractBody = TransactionBody
        .parseFrom(sampleStorageTransaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    ContractID createdContract = null;
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract = " + createdContract);
    }
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);
    AccountID newAccountID = getMultiSigAccount(keyCount, memoSize, CryptoServiceTest.DAY_SEC * durationDays);
    Transaction paymentTx = CryptoServiceTest.getQueryPaymentSigned(newAccountID, nodeID, TestHelperComplex.getStringMemo(10));
    Query getContractGetByteCodeQuery = RequestBuilder
        .getContractGetBytecodeQuery(createdContract, paymentTx, ResponseType.COST_ANSWER);

    Response contractByteCodeResponse = sstub.contractGetBytecode(getContractGetByteCodeQuery);

    log.info("Contract GetByteCode: " + contractByteCodeResponse);
    ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
    log.info("Status : " + statusCode);

    long transactionFee = contractByteCodeResponse.getContractGetBytecodeResponse().getHeader()
        .getCost();
    long recordTransactionFee = getTransactionFeeFromRecord(paymentTx, queryPayerId,
        "30 Days");
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("SmartContract GetByteCode Days recordTransactionFee=" + recordTransactionFee);
    String result =
        "SmartContract GetByteCode Sig=" + keyCount + ", memo=" + memoSize + " :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = 0;
    long minTransactionFee = 0;
    if (keyCount == 1) {
      maxTransactionFee = SMARTCONTRACT_GET_BYTECODE_SIG_1 + feeVariance;
      minTransactionFee = SMARTCONTRACT_GET_BYTECODE_SIG_1 - feeVariance;
    } else if (keyCount == 10) {
      maxTransactionFee = SMARTCONTRACT_GET_BYTECODE_SIG_10 + feeVariance;
      minTransactionFee = SMARTCONTRACT_GET_BYTECODE_SIG_10 - feeVariance;
    } else {
      return;
    }
    if (paymentTx.getSigMap().getSigPairCount() != 0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  private long callLocal(AccountID payerAccount, ContractID contractToCall, byte[] data,
      long gas)
      throws Exception {
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    log.info(" callParamSize ****  : " + data.length);
    // Passing a value to this method is not valid.
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, 90_000_000L);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, gas, dataBstr, 0L, 5000, paymentTx,
            ResponseType.COST_ANSWER);

    Response callResp = sstub.contractCallLocalMethod(contractCallLocal);
    log.info("callLocal Response" + callResp);

    TransactionBody callContractBody = TransactionBody
        .parseFrom(paymentTx.getBodyBytes());
    TransactionGetReceiptResponse callLocalReceipt = getReceipt(
        callContractBody.getTransactionID());
    long callLocalFee = 0;
    if (callResp.hasContractCallLocal()) {
      ContractCallLocalResponse callLocalResponse = callResp.getContractCallLocal();
      callLocalFee = callLocalResponse.getHeader().getCost();
      log.info("******callLocalFee = " + callLocalFee);
    }
    log.info("#########getTransactoinFee=" + getTransactionFee(paymentTx));
    log.info("#########getTransactionFeeFromRecord=" + getTransactionFeeFromRecord(paymentTx,
        queryPayerId, "getFeeFromTransactionRecrod"));

    return callLocalFee;
  }


  private ContractCallLocalResponse callContractLocal(AccountID payerAccount,
      ContractID contractToCall, byte[] data, long gas) throws Exception {
    ByteString callData = ByteString.EMPTY;
    int callDataSize = 0;
    if (data != null) {
      callData = ByteString.copyFrom(data);
      callDataSize = callData.size();
    }
    long fee = FeeClient.getCostContractCallLocalFee(callDataSize);
    Response callResp;
    callResp = executeContractCall(payerAccount, contractToCall, sstub, callData, fee,
        gas, ResponseType.COST_ANSWER);
    fee = callResp.getContractCallLocal().getHeader().getCost();

    log.info("===> Fee offered is " + fee + ", gas offered is " + gas);
    callResp = executeContractCall(payerAccount, contractToCall, sstub, callData, fee,
        gas, ResponseType.ANSWER_ONLY);
    log.info("callContractLocal response = " + callResp);
    Assert.assertTrue(callResp.hasContractCallLocal());
    ContractCallLocalResponse response = callResp.getContractCallLocal();
    log.info("######  callLocal Fee REsponse :  " + response.getHeader().getCost());
    return response;
  }

  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
      long gas, ResponseType resposeType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, gas, callData, 0L, 5000, paymentTx,
            resposeType);

    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
    return callResp;
  }


  private long contractCall(AccountID payerAccount, ContractID contractToCall, byte[] data,
      long gas)
      throws Exception {
    long totalCost = 0; // gas plus fees
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    long fee = FeeClient.getCostContractCallLocalFee(dataBstr.size());
    // Passing a value to this method is not valid.
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeID.getAccountNum(), 0l, 0l, 100l, timestamp,
            transactionDuration, gas, contractToCall, dataBstr, fee,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = sstub.contractCallMethod(callContractRequest);
    System.out.println(
        " invalidCallContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());

    Thread.sleep(1000);
    TransactionBody callContractBody = TransactionBody
        .parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID());

    if (contractCallReceipt != null) {

      Assert.assertEquals(contractCallReceipt.getReceipt().getStatus(),
          ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);
      TransactionRecord trRecord = getTransactionRecord(payerAccount,
          callContractBody.getTransactionID());
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        totalCost =
            trRecord.getTransactionFee() + (CONTRACT_CREATE_SUCCESS_GAS * callResults.getGasUsed());
        if (!StringUtils.isEmpty(errMsg)) {
          log.info("Error in Contract Call: " + errMsg);
        }
      }
    }

    return totalCost;
  }

  private long contractDelete(AccountID payerAccount, ContractID contractToCall, byte[] data,
      long gas, KeyPair adminKeyPair)
      throws Exception {
    long totalCost = 0; // gas plus fees
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }

    List<PrivateKey> keyList = new ArrayList<>(accountKeys.get(payerAccount));
    keyList.add(adminKeyPair.getPrivate()); // existing admin key

    long fee = TestHelper.getContractMaxFee();
    // Passing a value to this method is not valid.
    Transaction deleteContractRequest = TestHelper
        .getDeleteContractRequest(payerAccount, nodeID, fee,
            timestamp, transactionDuration, true, TestHelperComplex.getStringMemo(10), contractToCall,
            account_1, contractToCall, keyList);

    TransactionResponse response = sstub.deleteContract(deleteContractRequest);
    System.out.println(
        " deleteContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());

    Thread.sleep(1000);
    TransactionBody deleteContractBody = TransactionBody
        .parseFrom(deleteContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
        deleteContractBody.getTransactionID());

    if (contractDeleteReceipt != null) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount,
          deleteContractBody.getTransactionID());
      totalCost =
          trRecord.getTransactionFee();
    }

    return totalCost;
  }

  private Transaction createContractWithOptions(AccountID payerAccount, FileID contractFile,
      AccountID useNodeAccount, long autoRenewInSeconds, long gas, long balance,
      long transactionFee, ByteString constructorParams, KeyPair adminPrivateKeys)
      throws Exception {

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(autoRenewInSeconds).build();

    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    Common.addKeyMap(adminPrivateKeys, pubKey2privKeyMap);
    final KeyPair payerKeyPair = accountKeyPairs.get(payerAccount);
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    Key adminPubKey = Common.PrivateKeyToKey(payerKeyPair.getPrivate());
    List<Key> keyList = Collections.singletonList(adminPubKey);
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    return createContractRequest(
        payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
        useNodeAccount.getAccountNum(), useNodeAccount.getRealmNum(), useNodeAccount.getShardNum(),
        transactionFee, timestamp, transactionDuration, true, "Transaction Memo", gas, contractFile,
        constructorParams, balance, contractAutoRenew, pubKey2privKeyMap, "Contract Memo",
        adminPubKey, keyList);
  }

  public static Transaction createContractRequest(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, long gas, FileID fileId,
      ByteString constructorParameters, long initialBalance,
      Duration autoRenewalPeriod, HashMap<String, PrivateKey> keys, String contractMemo,
      Key adminKey, List<Key> keyList) throws Exception {
    Transaction transaction;

    transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters,
            initialBalance,
            autoRenewalPeriod, contractMemo, adminKey);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(transaction, keyList, keys);
    transactionFee = FeeClient.getContractCreateFee(transaction, keys.size());
    transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminKey);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(transaction, keyList, keys);
    return transaction;
  }

  /**
   * Checks if record fields are instantiated.
   */
  public static void checkRecord(Transaction transaction, AccountID payerID, String msg)
      throws Exception {
    CommonUtils.nap(2);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(transaction);
    TransactionRecord record = getTransactionRecord(payerID, body.getTransactionID());
    checkFee(record, body, msg);
  }


  /**
   * Checks the record fields against the originating transaction body.
   *
   * @param record transaction record
   * @param body the originating transaction body
   */
  public static void checkFee(TransactionRecord record, TransactionBody body, String msg) {
    TransactionID txID = body.getTransactionID();
    System.out.println("$$$$ record=" + record + ", txID=" + txID);
    Assert.assertEquals(txID, record.getTransactionID());
    Assert.assertEquals(body.getMemo(), record.getMemo());
    Assert.assertEquals(true, record.getConsensusTimestamp().getSeconds() > 0);
    log.info(msg + " record.getTransactionFee()=" + record.getTransactionFee());
    log.info(msg + " body.getTransactionFee()=" + body.getTransactionFee());
    Assert.assertEquals(true, body.getTransactionFee() >= record.getTransactionFee());
    Assert.assertEquals(true, record.getTransactionHash().size() > 0);

    if (body.hasCryptoTransfer() && ResponseCodeEnum.SUCCESS
        .equals(record.getReceipt().getStatus())) {
      TransferList transferList = body.getCryptoTransfer().getTransfers();
      Assert.assertEquals(true,
          record.getTransferList().toString().contains(transferList.toString()));
    }

    System.out.println(":) record check success!");
  }

  private static AccountID createAccount(AccountID payerAccount, long initialBalance)
      throws Exception {
    KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
    return createAccount(keyGenerated, payerAccount, initialBalance);
  }

  private static TransactionGetReceiptResponse getReceipt_new(TransactionID transactionId)
      throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();

    Response transactionReceipts = CryptoServiceTest.cstub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES && !transactionReceipts.getTransactionGetReceipt()
        .getReceipt()
        .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      Thread.sleep(2000);
      transactionReceipts = CryptoServiceTest.cstub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as Success..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }
    if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
        .equals(ResponseCodeEnum.SUCCESS)) {
      receiptToReturn = transactionReceipts.getTransactionGetReceipt();
    }

    return transactionReceipts.getTransactionGetReceipt();

  }

  private static ContractID createContract(AccountID payerAccount, FileID contractFile,
      byte[] constructorData, long autoRenewPeriod) throws Exception {
    ContractID createdContract = null;
    ByteString dataToPass = ByteString.EMPTY;
    if (constructorData != null) {
      dataToPass = ByteString.copyFrom(constructorData);
    }

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
            nodeID.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "", 2250000, contractFile, dataToPass, 0,
            Duration.newBuilder().setSeconds(autoRenewPeriod).build(),
            accountKeys.get(payerAccount), "");

    TransactionResponse response = sstub.createContract(createContractRequest);
    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody createContractBody = TransactionBody
        .parseFrom(createContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt_new(
        createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
    }

    return createdContract;
  }


  private static TransactionRecord callContract(AccountID payerAccount, ContractID contractToCall,
      byte[] data, long gas)
      throws Exception {
    byte[] dataToReturn = null;
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    System.out.println("Contract Call bytes Size: " + dataBstr.toByteArray().length);
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), Utilities.getDefaultNodeAccount(), 0l, 0l,
            TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, gas, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = sstub.contractCallMethod(callContractRequest);
    System.out.println(
        " call contract  Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody callContractBody = TransactionBody
        .parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt_new(
        callContractBody.getTransactionID());
    TransactionRecord trRecord = null;
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      trRecord = getTransactionRecord_new(payerAccount,
          callContractBody.getTransactionID());
    }
    if (trRecord != null && trRecord.hasContractCallResult()) {
      ContractFunctionResult callResults = trRecord.getContractCallResult();
      String errMsg = callResults.getErrorMessage();
      if (StringUtils.isEmpty(errMsg)) {
        if (!callResults.getContractCallResult().isEmpty()) {
          dataToReturn = callResults.getContractCallResult().toByteArray();
          System.out.println(
              "dataToReturn =" + dataToReturn.length + ", getGasUsed() = " + callResults
                  .getGasUsed());
        }
      } else {
        System.out.println("@@@ Contract Call resulted in error: " + errMsg);
      }
    }
    return trRecord;
  }

  private static TransactionRecord getTransactionRecord_new(AccountID payer,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    long fee = FeeClient.getCostForGettingTxRecord();
    Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.COST_ANSWER);
    Response recordResp = CryptoServiceTest.cstub.getTxRecordByTxID(getRecordQuery);

    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
    recordResp = CryptoServiceTest.cstub.getTxRecordByTxID(getRecordQuery);

    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    System.out.println("tx record = " + txRecord);

    return txRecord;
  }


  private static byte[] callContractLocal(ContractID contractToCall, byte[] data, AccountID payer)
      throws Exception {
    byte[] dataToReturn = null;
    AccountID createdAccount = null;
    long fee = FeeClient.getCostContractCallLocalFee(data.length);
    Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
    ByteString callData = ByteString.EMPTY;
    if (data != null) {
      callData = ByteString.copyFrom(data);
    }
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.COST_ANSWER);

    Response callResp = sstub.contractCallLocalMethod(contractCallLocal);

    fee = callResp.getContractCallLocal().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.ANSWER_ONLY);

    callResp = sstub.contractCallLocalMethod(contractCallLocal);
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    System.out.println("callContractLocal response = " + callResp);

    return functionResults.toByteArray();
  }


  private static CallTransaction.Function getConstructorFunction() {
    String funcJson = TOKEN_ERC20_CONSTRUCTOR_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] getEncodedConstructor(long initialSupply, String tokenName,
      String tokenSymbol) {
    String retStr = "";
    CallTransaction.Function func = getConstructorFunction();
    byte[] encodedFunc = func.encodeArguments(initialSupply, tokenName, tokenSymbol);

    return encodedFunc;
  }


  private static ContractID createTokenContract(AccountID payerAccount, FileID contractFile,
      long initialTokensSupply, String tokenName, String tokenSymbol, long autoRenewPeriod)
      throws Exception {
    byte[] constructorData = getEncodedConstructor(initialTokensSupply, tokenName, tokenSymbol);
    ContractID createdContract = null;
    createdContract = createContract(payerAccount, contractFile, constructorData, autoRenewPeriod);

    return createdContract;
  }

  private static CallTransaction.Function getDecimalsFunction() {
    String funcJson = DECIMALS_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeDecimals() {
    String retVal = "";
    CallTransaction.Function function = getDecimalsFunction();
    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private static long decodeDecimalsResult(byte[] value) {
    long decodedReturnedValue = 0;
    CallTransaction.Function function = getDecimalsFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.longValue();
    }
    return decodedReturnedValue;
  }

  public static long decimals(ContractID contractAddress, AccountID payer) throws Exception {
    long decimalsToReturn = 0;

    byte[] dataEncodeDecimals = encodeDecimals();

    byte[] valueOfDecimals = callContractLocal(contractAddress, dataEncodeDecimals, payer);
    //decode value from results
    decimalsToReturn = decodeDecimalsResult(valueOfDecimals);
    return decimalsToReturn;

  }

  private static CallTransaction.Function getSymbolFunction() {
    String funcJson = SYMBOL_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeSymbol() {
    String retVal = "";
    CallTransaction.Function function = getSymbolFunction();

    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private static String decodeSymbolResult(byte[] value) {
    String decodedReturnedValue = "";
    CallTransaction.Function function = getSymbolFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      decodedReturnedValue = (String) retResults[0];
    }
    return decodedReturnedValue;
  }

  public static String symbol(ContractID contractAddress, AccountID payer) throws Exception {
    String symbolToReturn;
    byte[] dataEncodeSymbol = encodeSymbol();

    byte[] valueOfSymbol = callContractLocal(contractAddress, dataEncodeSymbol, payer);
    //decode value from results
    symbolToReturn = decodeSymbolResult(valueOfSymbol);
    return symbolToReturn;

  }

  private static AccountInfo getCryptoGetAccountInfo(
      AccountID accountID) throws Exception {

    long fee = FeeClient.getCostForGettingAccountInfo();
    Transaction paymentTx = createQueryHeaderTransfer(accountID, fee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, ResponseType.COST_ANSWER);

    Response respToReturn = CryptoServiceTest.cstub.getAccountInfo(cryptoGetInfoQuery);

    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(accountID, fee);
    cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, ResponseType.ANSWER_ONLY);
    respToReturn = CryptoServiceTest.cstub.getAccountInfo(cryptoGetInfoQuery);

    AccountInfo accInfToReturn = null;
    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();

    return accInfToReturn;
  }

  private static CallTransaction.Function getBalanceOfFunction() {
    String funcJson = BALANCE_OF_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeBalanceOf(String address) {
    String retVal = "";
    CallTransaction.Function function = getBalanceOfFunction();

    byte[] encodedFunc = function.encode(address);

    return encodedFunc;
  }

  private static long decodeBalanceOfResult(byte[] value) {
    long decodedReturnedValue = 0;
    CallTransaction.Function function = getBalanceOfFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.longValue();
    }
    return decodedReturnedValue;
  }

  public static long balanceOf(ContractID contractAddress, String accountAdddressEthFormat,
      AccountID payer) throws Exception {
    long balance = 0;
    byte[] dataEncodeBalanceOF = encodeBalanceOf(accountAdddressEthFormat);

    byte[] valueOfBalance = callContractLocal(contractAddress, dataEncodeBalanceOF, payer);
    //decode value from results
    balance = decodeBalanceOfResult(valueOfBalance);
    return balance;

  }

  private static CallTransaction.Function getTransferFunction() {
    String funcJson = TRANSFER_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeTransfer(String toAccountAddress, long valueToTransfer) {
    String retVal = "";
    CallTransaction.Function function = getTransferFunction();
    byte[] encodedFunc = function.encode(toAccountAddress, valueToTransfer);

    return encodedFunc;
  }

  private static TransactionRecord transfer(ContractID contractId, AccountID payerAccount,
      String toAccountAddress, long valueToTransfer, long gas) throws Exception {
    byte[] dataToSet = encodeTransfer(toAccountAddress, valueToTransfer);
    //set value to simple storage smart contract
    return callContract(payerAccount, contractId, dataToSet, gas);
  }


  private static CallTransaction.Function getApproveFunction() {
    String funcJson = APPROVE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static CallTransaction.Function getTransferFromFunction() {
    String funcJson = TRANSFER_FROM_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static void printBalances(ContractID contractAddress, Map<String, String> tokenOwners,
      long multiplier, String symbol, AccountID payer) throws Exception {
    Map<String, Long> balancePerOwner = new HashMap<String, Long>(tokenOwners.size());
    for (String tokenOwnerName : tokenOwners.keySet()) {
      String tokenOwnerAccountEthFormat = tokenOwners.get(tokenOwnerName);
      long balance = balanceOf(contractAddress, tokenOwnerAccountEthFormat, payer);
      balancePerOwner.put(tokenOwnerName, balance);

    }
    System.out.println(
        "---------------------------------------------balances-----------------------------------------------------------");
    for (String currentTokenOwnerName : balancePerOwner.keySet()) {
      long currBalance = balancePerOwner.get(currentTokenOwnerName);
      System.out.println(
          currentTokenOwnerName + " has a balance of " + currBalance / multiplier + " " + symbol);
    }
    System.out.println(
        "----------------------------------------------------------------------------------------------------------------");
  }

}
