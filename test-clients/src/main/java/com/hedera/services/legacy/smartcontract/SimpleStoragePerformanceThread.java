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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TPS testing for contract calls
 *
 * @author Peter
 */
public class SimpleStoragePerformanceThread implements Runnable {
  private static final long TX_DURATION_SEC = 3 * 60; // 3 minutes for tx dedup
  private static int ITERATIONS = 2500;
  private static int PRODUCER_THREADS = 3;
  private static int TPS_TARGET = 5;
  private static int QPS_TARGET;

  private static long contractDuration;
  private final Logger log = LogManager.getLogger(SimpleStoragePerformanceThread.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final int MAX_BUSY_RETRIES = 15;
  private static final int BUSY_RETRY_MS = 200;

  private static final String SIMPLE_STORAGE_BIN = "simpleStorage.bin";
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static AccountID genesisAccount;
  private static PrivateKey genesisPrivateKey = null;

  private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String host;
  private static int port;
  private static FileID contractFileId;
  private static ContractID contractId;
  private Role role;
  private Queue<FlaggedTransactionID> txIdQueue;
  private long startTime;
  private SimpleStoragePerformanceThread monitor;
  private int completedProducerThreads = 0;
  private boolean startedConsumerThread = false;

  private ManagedChannel channel = null;
  private CryptoServiceGrpc.CryptoServiceBlockingStub cstub;
  private SmartContractServiceGrpc.SmartContractServiceBlockingStub scstub;


  public SimpleStoragePerformanceThread(Role _role, SimpleStoragePerformanceThread monitor) {
    this.role = _role;
    this.monitor = monitor;
    this.startTime = 0;
    createStubs();
  }

  /*
  Arguments are:
  0: Node name or address
  1: Node account number
  2: Number of iterations
  3: Number of producer threads
  4: TPS target for producer thread
  5: QPS target for consumer thread
   */
  public static void main(String args[]) {
    Properties properties = TestHelper.getApplicationProperties();

    host = properties.getProperty("host");
    if ((args.length) > 0) {
      host = args[0];
      System.out.println("Got host as " + host);
    }

    node_account_number = Utilities.getDefaultNodeAccount();
    if ((args.length) > 1) {
      node_account_number = Long.parseLong(args[1]);
      System.out.println("Got Node Account as " + node_account_number);
    }

    if ((args.length) > 2) {
      ITERATIONS = Integer.parseInt(args[2]);
      System.out.println("Got Number of Iterations as " + ITERATIONS);
    }

     if ((args.length) > 3) {
      PRODUCER_THREADS = Integer.parseInt(args[3]);
      System.out.println("Got Number of Producer Threads as " + PRODUCER_THREADS);
    }

    if ((args.length) > 4) {
      TPS_TARGET = Integer.parseInt(args[4]);
      System.out.println("Got TPS target as " + TPS_TARGET);
    }

    QPS_TARGET = TPS_TARGET;
    if ((args.length) > 5) {
      QPS_TARGET = Integer.parseInt(args[5]);
      System.out.println("Got QPS target as " + QPS_TARGET);
    }

    port = Integer.parseInt(properties.getProperty("port"));
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));

    SimpleStoragePerformanceThread monitor = new SimpleStoragePerformanceThread(Role.MONITOR, null);
    Thread monitorThread = new Thread(monitor);
    monitorThread.start();
  }

  @Override
  public void run() {
    try {
      switch (this.role) {
        case MONITOR:
          this.doMonitor();
          break;
        case PRODUCER:
          this.doCalls();
          break;
        case CONSUMER:
          this.doQueries();
          break;
      }
    } catch (Exception e) {
      log.error(this.role.toString() + " process failed with " + e);
      e.printStackTrace();
      Assert.fail("Exception in " + this.role + " process.");
    }
  }

  private void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");

    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
    genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

    // get the Account Object
    genesisAccount = genesisAccountList.get(0).getAccountId();
    accountKeyPairs.put(genesisAccount, genesisKeyPair);
  }

  private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer,
        accountKeyPairs.get(payer), nodeAccount, transferAmt);
    return transferTx;
  }

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Response transactionReceipts = stub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES &&
        (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN) ||
         transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.BUSY))) {
      Thread.sleep(500);
      transactionReceipts = stub.getTransactionReceipts(query);
      attempts++;
    }
    if (ResponseCodeEnum.SUCCESS != transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()) {
      System.out.println("Final getTransactionReceipts status: " +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
    }
    channel.shutdown();
    return transactionReceipts.getTransactionGetReceipt();

  }

  private ContractID createContract(AccountID payerAccount, FileID contractFile,
      long durationInSeconds) throws Exception {
    ContractID createdContract = null;
    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "", 2500, contractFile, ByteString.EMPTY, 0,
            contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

    TransactionResponse response = retryLoopTransaction(createContractRequest, "createContract");
    Assert.assertNotNull(response);

    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());

    TransactionBody createContractBody = TransactionBody
        .parseFrom(createContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
    }
    TransactionRecord trRecord = getTransactionRecord(payerAccount,
        createContractBody.getTransactionID());
    Assert.assertNotNull(trRecord);
    Assert.assertTrue(trRecord.hasContractCreateResult());
    Assert.assertEquals(trRecord.getContractCreateResult().getContractID(),
        contractCreateReceipt.getReceipt().getContractID());

    return createdContract;
  }



  /*
  Methods to run the set method
   */
  private TransactionID setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet)
      throws Exception {
    byte[] dataToSet = encodeSet(valuetoSet);
    //set value to simple storage smart contract
    TransactionID tID = callContractOnly(payerAccount, contractId, dataToSet, 0);
    return tID;
  }

  private byte[] encodeSet(int valueToAdd) {
    String retVal = "";
    CallTransaction.Function function = getSetFunction();
    byte[] encodedFunc = function.encode(valueToAdd);

    return encodedFunc;
  }

  private CallTransaction.Function getSetFunction() {
    String funcJson = SC_SET_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private TransactionID callContractOnly(AccountID payerAccount, ContractID contractToCall,
      byte[] data,
      int value) throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), node_account_number, 0l, 0l, 100l, timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, value,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = retryLoopTransaction(callContractRequest, "contractCallMethod");
    Assert.assertNotNull(response);
    if (response.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK) {
      System.out.println(
          " callContractOnly Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
              .name());
    }
    if (ResponseCodeEnum.OK != response.getNodeTransactionPrecheckCode()) {
      return null;
    }
    TransactionBody callContractBody = TransactionBody
        .parseFrom(callContractRequest.getBodyBytes());
    TransactionID txId = callContractBody.getTransactionID();

    return txId;
  }

/*
  private boolean getReceiptAndRecord(AccountID payerAccount, TransactionID txId) throws Exception {
    byte[] dataToReturn = null;
    String retVal = null;

    TransactionGetReceiptResponse contractCallReceipt = getReceipt(txId);
    if (contractCallReceipt.getHeader().getNodeTransactionPrecheckCode() ==
        ResponseCodeEnum.RECEIPT_NOT_FOUND) {
      return false;
    }

    Assert.assertEquals(ResponseCodeEnum.SUCCESS, contractCallReceipt.getReceipt().getStatus());
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (StringUtils.isEmpty(errMsg)) {
          if (!callResults.getContractCallResult().isEmpty()) {
            dataToReturn = callResults.getContractCallResult().toByteArray();
          }
        } else {
          log.info("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
    return true;
  }
*/

  private boolean getReceiptOrRecord(AccountID payerAccount, TransactionID txId) throws Exception {
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(txId);
    if (contractCallReceipt.getReceipt().getStatus() == ResponseCodeEnum.SUCCESS) {
      return true;
    }

    // Receipt not found, check for longer-lived record instead
    TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
    if (trRecord != null && trRecord.getReceipt() != null &&
        trRecord.getReceipt().getStatus() == ResponseCodeEnum.SUCCESS) {
      return true;
    }

    return false;
  }

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    long fee = FeeClient.getCostForGettingTxRecord();
    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, fee,
        ResponseType.COST_ANSWER);
    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    recordResp = executeQueryForTxRecord(payerAccount, transactionId, fee,
        ResponseType.ANSWER_ONLY);
    return recordResp.getTransactionGetRecord().getTransactionRecord();
  }


  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
      long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);

    Response recordResp = retryLoopQuery(getRecordQuery, "getTxRecordByTxID");
    return recordResp;
  }

  private void sleepToNextSecond() {
    long now = System.currentTimeMillis();
    long napTime = 1000 - (now % 1000);
    try {
      Thread.sleep(napTime);
    } catch (InterruptedException e) {
      ;
    }
    now = System.currentTimeMillis();
    log.info("Slept to " + now);
  }

  public void doCommonSetup() throws Exception {
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    List<PrivateKey> genesisAccountPrivateKeys = new ArrayList<>();
    genesisAccountPrivateKeys.add(genesisPrivateKey);
    contractFileId = LargeFileUploadIT
        .uploadFile(genesisAccount, SIMPLE_STORAGE_BIN, genesisAccountPrivateKeys, host,
            nodeAccount);
    Assert.assertNotNull(contractFileId);
    log.info("Smart Contract file uploaded successfully");

    contractId = createContract(genesisAccount, contractFileId, contractDuration);
    Assert.assertNotNull(contractId);
    log.info("Contract created successfully");
  }

  public void doCalls() throws Exception {
    loadGenesisAndNodeAcccounts();

    sleepToNextSecond(); // Infrastructure
    long begin = System.currentTimeMillis();
    monitor.producerReady(begin); // Infrastructure
    try {
      for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
        if ((iteration % 1000) == 0) {
          log.info("Call number " + iteration);
        }
        int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
        try {
          TransactionID txId = setValueToContract(genesisAccount, contractId, currValueToSet);
          if (txId != null) {
            monitor.addFlaggedTransactionID(
                new FlaggedTransactionID(txId, iteration, false)); // Infrastructure
          }
        } catch (StatusRuntimeException e) {
          log.warn("Producer skipping StatusRuntimeException " + e);
        }
        if ((iteration % TPS_TARGET) == 0) { // Infrastructure
          sleepToNextSecond(); // Infrastructure
        }
      }
    } catch (Exception e) {
      log.error("Exception in producer: " + e);
      e.printStackTrace();
    }
    monitor.addFlaggedTransactionID(new FlaggedTransactionID(null, 0, true)); // Infrastructure
  }

  public void doQueries() throws Exception {
    long QUEUE_SLEEP = Math.min(50, 1000 / QPS_TARGET); // Sleep if queue is empty
    int QUEUE_DIED = 20 * QPS_TARGET; // Fail if queue empty for 20 seconds

    int empties = 0;
    long items = 0L;
    int ioErrors = 0;
    int nullReceipts = 0;
    while (true) {
      FlaggedTransactionID item = monitor.pollFlaggedTransactionID();
      if (item == null) {
        empties += 1;
        if (empties > QUEUE_DIED) {
          log.error("Queue retry limit exceeded with no transactions");
          Assert.fail("Queue retry limit exceeded with no transactions");
        }
        Thread.sleep(QUEUE_SLEEP);
      } else {
        empties = 0;
        if (item.isEndFlag()) {
          log.info("End flag encountered; quitting consumer");
          break;
        } else {
          try {
            if (getReceiptOrRecord(genesisAccount, item.getTransactionID())) {
              items++;
              if ((items % 1000) == 0) {
                log.info("receipt or record fetched for iteration " + item.getSeq());
              }
            } else {
              log.warn("NEITHER receipt nor record found for iteration " + item.getSeq());
              nullReceipts++;
            }
          } catch (StatusRuntimeException e) {
            ioErrors++;
            log.warn("Ignoring exception " + e);
            e.printStackTrace();
          }
        }
      }
    }
    long finish = System.currentTimeMillis();
    log.info(items + " iterations in " + (finish - monitor.getStartTime()) +
        " msec for target " + TPS_TARGET + " TPS per thread");
    double rate = ((items * 10_000L) / (finish - monitor.getStartTime())) / 10.0;
    log.info("Approximately " + rate + " TPS total");
    log.info (ioErrors + " grpc IO errors ignored");
    log.info (nullReceipts + " receipt nor record found ignored");
  }

  public void doMonitor() throws Exception {
    txIdQueue = new ConcurrentLinkedQueue<>();
    doCommonSetup();
    Thread.sleep(30_000L);
    SimpleStoragePerformanceThread producer;
    Thread producerThread;
    log.info("Starting " + PRODUCER_THREADS + " producer threads");
    for (int thread = 0; thread < PRODUCER_THREADS; thread++) {
      producer = new SimpleStoragePerformanceThread(Role.PRODUCER, this);
      producerThread = new Thread(producer);
      producerThread.start();
    }
  }

  // Consumer does not start until the producer declares that it's finished with setup.
  void producerReady(long startTime) {
    synchronized (SimpleStoragePerformanceThread.class) {
      if (!startedConsumerThread) {
        log.info("Starting a consumer thread");
        this.startTime = startTime;
        SimpleStoragePerformanceThread consumer = new SimpleStoragePerformanceThread(
            Role.CONSUMER, this);
        Thread consumerThread = new Thread(consumer);
        consumerThread.start();
        startedConsumerThread = true;
      }
    }
  }

  long getStartTime() {
    return this.startTime;
  }

  FlaggedTransactionID pollFlaggedTransactionID() {
    FlaggedTransactionID item;
    /* Eat all last flags but one */
    do {
      item = txIdQueue.poll();
      if (item != null && item.isEndFlag()) {
        completedProducerThreads += 1;
      }
    } while (item != null && item.isEndFlag() && completedProducerThreads < PRODUCER_THREADS);

    return item;
  }

  boolean addFlaggedTransactionID(FlaggedTransactionID txn) {
    return txIdQueue.add(txn);
  }

  private void createStubs() {
    if (channel != null) {
      channel.shutdown();
    }
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    cstub = CryptoServiceGrpc.newBlockingStub(channel);
    scstub = SmartContractServiceGrpc.newBlockingStub(channel);
  }

  private TransactionResponse retryLoopTransaction(Transaction transaction, String apiName) {
    TransactionResponse response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {
      try {
        switch (apiName) {
          case "createContract":
            response = scstub.createContract(transaction);
            break;
          case "contractCallMethod":
            response = scstub.contractCallMethod(transaction);
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

  private Response retryLoopQuery(Query query, String apiName) {
    Response response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {
      ResponseCodeEnum precheckCode;
      try {
        switch (apiName) {
          case "getTxRecordByTxID":
            // The use of cstub here, instead of scstub, is deliberate.
            response = cstub.getTxRecordByTxID(query);
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
