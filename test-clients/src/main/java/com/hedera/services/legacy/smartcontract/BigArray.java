package com.hedera.services.legacy.smartcontract;

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
import com.hedera.services.legacy.regression.ServerAppConfigUtility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estimate TPS for calling/saving contracts with different state sizes
 *
 * @author Peter
 */
public class BigArray {
  private final static Logger log = LogManager.getLogger(BigArray.class);
  private static final int MAX_RECEIPT_RETRIES = 120;
  private static final int MAX_BUSY_RETRIES = 15;
  private static final int BUSY_RETRY_MS = 200;
  private static final int BATCH_SIZE = 5;
  private static final int GROWTH_STEP = 16; // Size per step, in KiB, to grow the smart contract storage.

  private static final String BA_CHANGEARRAY_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"changeArray\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String BA_GROWTO_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_limit\",\"type\":\"uint256\"}],\"name\":\"growTo\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

  private static long nodeAccountNum;
  private static AccountID nodeAccount;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static final String BIG_ARRAY_BIN = "testfiles/GrowArray.bin";
  private static AccountID genesisAccount;
  private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String grpcHost;
  private static int grpcPort;
  private static long contractDuration;
  private static ManagedChannel channelShared = null;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;
  private static SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;
  private static long gasToOffer;

  private static void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");

    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

    // get the Account Object
    genesisAccount = genesisAccountList.get(0).getAccountId();
    accountKeyPairs.put(genesisAccount, genesisKeyPair);
  }

  public static void main(String args[]) throws Exception {
    Properties properties = getApplicationProperties();
    grpcPort = Integer.parseInt(properties.getProperty("port"));
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));


    if (args.length < 4) {
      System.out.println("Must provide all four arguments to this application.");
      System.out.println("0: host");
      System.out.println("1: node number");
      System.out.println("2: number of iterations");
      System.out.println("3: storage in KB");
      return;
     }

    System.out.println("args[0], host, is " + args[0]);
    System.out.println("args[1], node account, is " + args[1]);
    System.out.println("args[2], number of transfers, is " + args[2]);
    System.out.println("args[3], storage in KB, is " + args[3]);


    grpcHost = args[0];
    System.out.println("Got Grpc host as " + grpcHost);

    nodeAccountNum = Long.parseLong(args[1]);
    System.out.println("Got Node Account number as " + nodeAccountNum);
    nodeAccount = RequestBuilder
        .getAccountIdBuild(nodeAccountNum, 0l, 0l);

    int numberOfIterations;
    numberOfIterations = Integer.parseInt(args[2]);
    System.out.println("Got number of iterations as " + numberOfIterations);

    int sizeInKb;
    sizeInKb = Integer.parseInt(args[3]);
    System.out.println("Got size in KB as " + sizeInKb);

    createStubs();
    loadGenesisAndNodeAcccounts();

    ServerAppConfigUtility appConfig = ServerAppConfigUtility.getInstance(grpcHost, nodeAccountNum);
    gasToOffer = appConfig.getMaxGasLimit() - 1;
    if (sizeInKb > appConfig.getMaxContractStateSize()) {
      log.error ("Specified storage size of " + sizeInKb + " KiB is greater than the limit, "
          + appConfig.getMaxContractStateSize());
      System.exit(1);
    }

    ManagedChannel channel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
        .usePlaintext()
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount,
            TestHelper.getCryptoMaxFee() * 10L);
    Assert.assertNotNull(crAccount);
    Assert.assertNotEquals(0, crAccount.getAccountNum());
    System.out.println("Account created successfully: " + crAccount);


    // Upload contract file
    FileID zeroContractFileId = LargeFileUploadIT
        .uploadFile(crAccount, BIG_ARRAY_BIN, crAccountKeyPair);
    Assert.assertNotNull(zeroContractFileId);
    Assert.assertNotEquals(0, zeroContractFileId.getFileNum());
    System.out.println("Contract file uploaded successfully");

    // Create contract
    ContractID zeroContractId = createContract(crAccount, zeroContractFileId, null);
    Assert.assertNotNull(zeroContractId);
    Assert.assertNotEquals(0, zeroContractId.getContractNum());
    System.out.println("Contract created successfully: " + zeroContractId);

    // Initialize storage size
    List<TransactionID> txnIdList = new ArrayList<>();

    int sizeNow = 0;
    TransactionID txnId;
    while (sizeNow < sizeInKb) {
      sizeNow = Math.min(sizeNow + GROWTH_STEP, sizeInKb);
      System.out.println("Growing a step to " + sizeNow);
      txnId = doGrowArray(zeroContractId, crAccount, sizeNow);
      txnIdList.add(txnId);
    }
    clearTransactions(crAccount, txnIdList);
    System.out.println("Contract storage size set");

    // Loop of store calls
    long start = System.nanoTime();
    for (int i = 0; i < numberOfIterations; i++) {
      txnId = doChangeArray(zeroContractId, crAccount, ThreadLocalRandom.current().nextInt(1000));
      txnIdList.add(txnId);
      if (txnIdList.size() >= BATCH_SIZE) {
        log.info("Sent call " + i);
        clearTransactions(crAccount, txnIdList);
      }
    }
    if (txnIdList.size() > 0) {
      clearTransactions(crAccount, txnIdList);
    }

    long end = System.nanoTime();
    long elapsedMillis = (end - start) / 1_000_000;
    log.info("Making " + numberOfIterations + " transfers took " +
        elapsedMillis / 1000.0 + " seconds");
    double tps = (numberOfIterations * 100000 / elapsedMillis) / 100.0;
    log.info("About " + tps + " TPS for " + sizeInKb + " KB of storage");

    channelShared.shutdown();

    // Marker message for regression report
    log.info("Regression summary: This run is successful.");
  }

  private static void createStubs() {
    if (channelShared != null) {
      channelShared.shutdown();
    }
    channelShared = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
        .usePlaintext()
        .build();
    cryptoStub = CryptoServiceGrpc.newBlockingStub(channelShared);
    sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channelShared);
  }

  private static void clearTransactions(AccountID payingAccount, List<TransactionID> transactions)
      throws Exception {
    for (TransactionID id : transactions) {
      readReceiptAndRecord(payingAccount, id);
    }
    transactions.clear();
  }

  private static Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer,
        accountKeyPairs.get(payer), nodeAccount, transferAmt);
    return transferTx;
  }

  private static AccountID createAccount(AccountID payerAccount, long initialBalance)
      throws Exception {
    KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
    return createAccount(keyGenerated, payerAccount, initialBalance);
  }

  private static AccountID createAccount(KeyPair keyPair, AccountID payerAccount,
      long initialBalance) throws Exception {

    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
            accountKeyPairs.get(payerAccount));
    TransactionResponse response = retryLoopTransaction(transaction, "createAccount");
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    System.out.println(
        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), cryptoStub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    return newlyCreateAccountId;
  }

  private static TransactionGetReceiptResponse getReceipt(TransactionID transactionId)
      throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();

    Response transactionReceipts = cryptoStub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES &&
        (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN) ||
         transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.BUSY))) {
      Thread.sleep(1000);
      transactionReceipts = cryptoStub.getTransactionReceipts(query);
      attempts++;
    }
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());

    return transactionReceipts.getTransactionGetReceipt();

  }

  private static ContractID createContract(AccountID payerAccount, FileID contractFile,
      byte[] constructorData) throws Exception {
    ContractID createdContract = null;
    ByteString dataToPass = ByteString.EMPTY;
    if (constructorData != null) {
      dataToPass = ByteString.copyFrom(constructorData);
    }

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();;
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "", gasToOffer, contractFile, dataToPass, 0,
            Duration.newBuilder().setSeconds(contractDuration).build(), accountKeyPairs.get(payerAccount), "",
            null);

    TransactionResponse response = retryLoopTransaction(createContractRequest, "createContract");
    Assert.assertNotNull(response);
    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
    		createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
    }

    return createdContract;
  }


  private static TransactionID callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    ContractID createdContract = null;

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();;
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccountNum, 0l, 0l,
            TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, gasToOffer, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = retryLoopTransaction(callContractRequest, "contractCallMethod");
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK,response.getNodeTransactionPrecheckCode());

    TransactionID txId = TransactionBody.parseFrom(callContractRequest.getBodyBytes())
        .getTransactionID();
    return txId;
  }

  private static void readReceiptAndRecord(AccountID payerAccount, TransactionID txId)
      throws Exception {
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(txId);
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (!StringUtils.isEmpty(errMsg)) {
           System.out.println("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
  }

  private static TransactionRecord getTransactionRecord(AccountID payer,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    long fee = FeeClient.getCostForGettingTxRecord();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc
        .newBlockingStub(channelShared);
    Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.COST_ANSWER);
    Response recordResp = retryLoopQuery(getRecordQuery, "getTxRecordByTxID");
    Assert.assertNotNull(recordResp);

    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
    recordResp = stub.getTxRecordByTxID(getRecordQuery);

    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();

    return txRecord;
  }

  /*
  Methods to run growArray method
   */
  private static TransactionID doGrowArray(ContractID contractId, AccountID payerAccount, int sizeInKB)
      throws Exception {
    byte[] dataToSet = encodeGrowArray(sizeInKB);
    //set value to simple storage smart contract
    return callContract(payerAccount, contractId, dataToSet);
  }

  private static byte[] encodeGrowArray(int sizeInKB) {
    String retVal = "";
    CallTransaction.Function function = getGrowArrayFunction();
    byte[] encodedFunc = function.encode(sizeInKB);

    return encodedFunc;
  }

  private static CallTransaction.Function getGrowArrayFunction() {
    String funcJson = BA_GROWTO_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run changeArray method
   */
  private static TransactionID doChangeArray(ContractID contractId, AccountID payerAccount, int newValue)
      throws Exception {
    byte[] dataToSet = encodeChangeArray(newValue);
    //set value to simple storage smart contract
    return callContract(payerAccount, contractId, dataToSet);
  }

  private static byte[] encodeChangeArray(int newValue) {
    String retVal = "";
    CallTransaction.Function function = getChangeArrayFunction();
    byte[] encodedFunc = function.encode(newValue);

    return encodedFunc;
  }

  private static CallTransaction.Function getChangeArrayFunction() {
    String funcJson = BA_CHANGEARRAY_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static Properties getApplicationProperties() {
    Properties prop = new Properties();
    InputStream input = null;
    try {
      String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
      input = new FileInputStream(rootPath + "application.properties");
      prop.load(input);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return prop;
  }

  private static TransactionResponse retryLoopTransaction(Transaction transaction, String apiName) {
    TransactionResponse response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {
      try {
        switch (apiName) {
          case "createAccount":
            response = cryptoStub.createAccount(transaction);
            break;
          case "createContract":
            response = sCServiceStub.createContract(transaction);
            break;
          case "contractCallMethod":
            response = sCServiceStub.contractCallMethod(transaction);
            break;
          default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          createStubs();
        }
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      try {
        Thread.sleep(BUSY_RETRY_MS);
      } catch (InterruptedException e) {
        ;
      }
    }
    return response;
  }

  private static Response retryLoopQuery(Query query, String apiName) {
    Response response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {
      ResponseCodeEnum precheckCode;
      try {
        switch (apiName) {
          case "getTxRecordByTxID":
            response = cryptoStub.getTxRecordByTxID(query);
            precheckCode = response.getTransactionGetRecord()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
         default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          createStubs();
        }
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(precheckCode)) {
        break;
      }
      try {
        Thread.sleep(BUSY_RETRY_MS);
      } catch (InterruptedException e) {
        ;
      }
    }
    return response;
  }
}
