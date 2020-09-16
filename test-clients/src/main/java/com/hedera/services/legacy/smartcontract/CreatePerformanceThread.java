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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Create contracts for TPS testing
 *
 * @author Peter
 */
public class CreatePerformanceThread implements Runnable {

  private static int ITERATIONS = 1;
  private static int PRODUCER_THREADS = 1;
  private static int TPS_TARGET = 10;
  private static int QPS_TARGET;

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private final Logger log = LogManager.getLogger(CreatePerformanceThread.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final int MAX_BUSY_RETRIES = 15;
  private static final int BUSY_RETRY_MS = 200;

  private static final String SIMPLE_STORAGE_BIN = "simpleStorage.bin";

  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  private static long contractDuration;

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
  private CreatePerformanceThread monitor;
  private int completedProducerThreads = 0;
  private boolean startedConsumerThread = false;

  private ManagedChannel channel = null;
  private CryptoServiceGrpc.CryptoServiceBlockingStub cstub;
  private SmartContractServiceGrpc.SmartContractServiceBlockingStub scstub;

  public CreatePerformanceThread(Role _role, CreatePerformanceThread monitor) {
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

    CreatePerformanceThread monitor = new CreatePerformanceThread(Role.MONITOR, null);
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
    genesisPrivateKey = genesisAccountList.get(0).getKeyPairList().get(0).getPrivateKey();
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
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
    Response transactionReceipts = cstub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES &&
        (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN) ||
            transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.BUSY))) {
      Thread.sleep(500);
      transactionReceipts = cstub.getTransactionReceipts(query);
      attempts++;
    }
    if (ResponseCodeEnum.SUCCESS != transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()) {
      System.out.println("Final getTransactionReceipts status: " +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
    }
    return transactionReceipts.getTransactionGetReceipt();

  }

  private TransactionID createContractOnly(AccountID payerAccount, FileID contractFile,
      long durationInSeconds) throws Exception {

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(180);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), 100l, timestamp,
            transactionDuration, true, "", 2500000, contractFile, ByteString.EMPTY, 0,
            contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

    TransactionResponse response = retryLoopTransaction(createContractRequest, "createContract");
    Assert.assertNotNull(response);

    if (ResponseCodeEnum.OK != response.getNodeTransactionPrecheckCode()) {
      System.out.println(
          " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
              .name());
      return null;
    }
      TransactionBody createContractBody = TransactionBody
        .parseFrom(createContractRequest.getBodyBytes());
    TransactionID txId = createContractBody.getTransactionID();
    return txId;
  }

  private ContractID getReceipt(AccountID payerAccount, TransactionID txId) throws Exception {
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(txId);
    if (contractCreateReceipt.getReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
      log.warn("Create contract receipt status was " +
          contractCreateReceipt.getReceipt().getStatus());
      return null;
    }
    try {
      TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
    } catch (Exception e) {
      return null;
    }
    return contractCreateReceipt.getReceipt().getContractID();
  }

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    long fee = FeeClient.getCostForGettingTxRecord();
    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, fee,
        ResponseType.COST_ANSWER);
    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    recordResp = executeQueryForTxRecord(payerAccount, transactionId, fee,
        ResponseType.ANSWER_ONLY);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
//    System.out.println("tx record = " + txRecord);
    return txRecord;
  }

  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
      long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);

    Response recordResp = retryLoopQuery(getRecordQuery, "getTxRecordByTxID");
    Assert.assertNotNull(recordResp);
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
    List<PrivateKey> genesisAccountPrivateKeys = new ArrayList<>();
    genesisAccountPrivateKeys.add(genesisPrivateKey);

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    contractFileId = LargeFileUploadIT
        .uploadFile(genesisAccount, SIMPLE_STORAGE_BIN, genesisAccountPrivateKeys, host,
            nodeAccount);
    Assert.assertNotNull(contractFileId);
    log.info("Smart Contract file uploaded successfully");
  }

  public void doCalls() throws Exception {
    loadGenesisAndNodeAcccounts();

    sleepToNextSecond(); // Infrastructure
    long begin = System.currentTimeMillis();
    monitor.producerReady(begin); // Infrastructure
    try {
      for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
        if ((iteration % 100) == 0) {
          log.info("Create number " + iteration);
        }
        try {
          TransactionID txId = createContractOnly(genesisAccount, contractFileId, contractDuration);
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
            ContractID createdContractId = getReceipt(genesisAccount, item.getTransactionID());
            if (createdContractId != null) {
              items++;
              if ((items % 100) == 0) {
                log.info("receipt fetched for iteration " + item.getSeq() +
                    ", created contract " + createdContractId);
              }
            } else {
              log.warn("receipt not found for iteration " + item.getSeq());
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
    log.info (nullReceipts + " receipt not found ignored");
  }

  public void doMonitor() throws Exception {
    txIdQueue = new ConcurrentLinkedQueue<>();
    doCommonSetup();
    CreatePerformanceThread producer;
    Thread producerThread;
    log.info("Starting " + PRODUCER_THREADS + " producer threads");
    for (int thread = 0; thread < PRODUCER_THREADS; thread++) {
      producer = new CreatePerformanceThread(Role.PRODUCER, this);
      producerThread = new Thread(producer);
      producerThread.start();
    }
  }

  // Consumer does not start until the producer declares that it's finished with setup.
  void producerReady(long startTime) {
    synchronized (CreatePerformanceThread.class) {
      if (!startedConsumerThread) {
        log.info("Starting a consumer thread");
        this.startTime = startTime;
        CreatePerformanceThread consumer = new CreatePerformanceThread(
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
          case "createAccount":
            response = cstub.createAccount(transaction);
            break;
          case "createContract":
            response = scstub.createContract(transaction);
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
