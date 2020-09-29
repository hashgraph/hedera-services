package com.hedera.services.legacy.netty;

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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hedera.services.legacy.regression.BaseClient;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.FileServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;

public class FilePerformanceThread extends BaseClient implements Runnable {

  private static final Logger log = LogManager.getLogger(FilePerformanceThread.class);
  private static int MAX_RETRIES = 36000; // 10 hours if retry interval is 1 sec.
  private static long MAX_WAIT_TIME_MILLIS = 180000; // 3 minutes
  private static long RETRY_FREQUENCY_MILLIS = 1000;
  private static int threadNumber = 1;

  private int txCounts;
  private boolean retrieveTxReceipt;
  private boolean fetchFullList = false;
  private String host;
  private int port;
  private InetAddress inetAddress;
  private Thread thx;
  private int tpsDesired;
  private int napTime;
  private int channelConnects;
  private long MAX_TX_FEE = 10000000l;
  private List<TransactionID> transList = new ArrayList<>();
  private ManagedChannel channel;
  private CryptoServiceBlockingStub cryptoStub;
  private FileServiceBlockingStub fileStub;
  private int MARKER_SIZE = 1000;
  private boolean isExponentialBackoff = false;

  public FilePerformanceThread(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  public FilePerformanceThread(int port, String host, int batchSize, boolean retrieveTxReceipt,
      long defaultAccount, int tpsDesired) {
    super(null);

    this.txCounts = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
    this.host = host;
    this.port = port;
    this.tpsDesired = tpsDesired;
    this.channelConnects = 1;
    MAX_TX_FEE = TestHelper.getCryptoMaxFee();
    CryptoServiceTest.defaultListeningNodeAccountID = RequestBuilder.getAccountIdBuild(defaultAccount, 0l, 0l);

    try {
      inetAddress = InetAddress.getLocalHost();
      getTestConfig();
      readGenesisInfo();
      createStubs();
    } catch (Exception ex) {
      log.error("Exception constructing FilePerformanceThread", ex);
    }
  }


  public void start() {
    String tName = inetAddress.getHostAddress() + "-" + threadNumber;
    log.info("Starting Thread " + tName);

    if (thx == null) {
      thx = new Thread(this, tName);
      thx.start();
    }
    napTime = (1000 / tpsDesired);
    threadNumber++;
  }

  public boolean shutdownChannel() throws Exception {
    if ((channel.getState(false) == ConnectivityState.READY)
        || (channel.getState(false) == ConnectivityState.IDLE)) {
      log.info("Shutting down channel " + channelConnects);
      channel.shutdown();
      Thread.sleep(100);
    }
    if (!channel.isShutdown()) {
		channel.shutdownNow();
	}
    return channel.isShutdown();
  }

  public void run() {
    int totalGoodReceipts = 0;
    int totalBadReceipts = 0;
    long minTransInMs = 100l;
    long maxTransInMs = -1l;
    int MAX_RETRY = 1000;
    int platformNotAccepted = 0;

    new ArrayList<>();
    Random rand = new Random();
    int randomSleepTime = rand.nextInt(1000);

    try {
      log.info("This thread sleeps for " + randomSleepTime);
      Thread.sleep(randomSleepTime);

      long ts, te = 0l, tm = 0l;
      int j = 0;
      log.warn("Initiating C DISCONNECT " + txCounts + " with naptime of " + napTime);
      long transferStartTime = System.currentTimeMillis();
      long tx10klistWindowTime = 0l;
      long tx10kListMarker = transferStartTime;

      byte[] b = new byte[1024];
      new Random().nextBytes(b);
      for (int i = 0; i < txCounts; i++) {
        ts = System.currentTimeMillis();
        if (channel == null || channel.getState(false) == ConnectivityState.SHUTDOWN) {
          reConnectChannel();
        }

        List<Transaction> txHolder = new ArrayList<>();
        try {
          boolean transThrottleRetry;
          int throttleRetryCnt = 0;

          TransactionResponse transferRes;
          AccountID payerID = CryptoServiceTest.genesisAccountID;
          AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

          do {
            transThrottleRetry = false;
            ByteString fileData = ByteString.copyFrom(b);
            List<Key> waclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
            transferRes = createFile(payerID, nodeID, fileData, waclPubKeyList, CryptoServiceTest.stub, txHolder);
            if (transferRes.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY) {
              transThrottleRetry = true;
              throttleRetryCnt++;
              Thread.sleep(100);
              if (throttleRetryCnt > MAX_RETRY) {
                log.info("TOO MANY RETRIES .. " + throttleRetryCnt);
                transThrottleRetry = false;
              }
            }
          } while (transThrottleRetry);
          log.info("Server BUSY Retries " + throttleRetryCnt);
          Assert.assertNotNull(transferRes);

          if (ResponseCodeEnum.OK == transferRes.getNodeTransactionPrecheckCode()) {
            TransactionBody transferBody =
                TransactionBody.parseFrom(txHolder.get(0).getBodyBytes());
            transList.add(transferBody.getTransactionID());
          } else {
            if (transferRes
                .getNodeTransactionPrecheckCode() == ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED) {
              log.error("T Not Created ** " + transferRes.getNodeTransactionPrecheckCode());
              platformNotAccepted++;
            } else {
				totalBadReceipts++;
			}
          }
          te = System.currentTimeMillis();
          tm = te - ts;
          log.info("T # " + (i + 1) + " sent in " + tm + " millis");
          if (i == 0) {
            maxTransInMs = minTransInMs = tm;
          }
          if (tm > maxTransInMs) {
            maxTransInMs = tm;
          }
          if (tm < minTransInMs) {
            minTransInMs = tm;
          }
        } catch (Throwable thx) {
          te = System.currentTimeMillis();
          log.error("* ERROR * ", thx);
          if (thx.getMessage() != null && thx.getMessage().contains("UNAVAILABLE")) {
            log.error("GRPC UNAVAILABLE DUE TO PLATFORM");
            Thread.sleep(1000);
          }
          if ((channel.getState(false) == ConnectivityState.SHUTDOWN)
              || (channel.getState(false) == ConnectivityState.TRANSIENT_FAILURE)) {
            log.error("* Connecting due to SHUTDOWN & TRANSIENT FAIL * ");
            reConnectChannel();
          }
        }

        Thread.sleep(napTime);
        if ((i != 0) && (i % MARKER_SIZE == 0)) {
          log.warn("Cleaning Receipt Queue at " + i);
          try {
            getReceiptAndCleanQueue();
            tx10klistWindowTime = te - tx10kListMarker;
            log.warn("*** TPS for " + MARKER_SIZE + " = " + (tx10klistWindowTime / MARKER_SIZE));
            tx10kListMarker = te;
          } catch (Throwable tx) {
            log.error("Could not Fetch Receipt when txSize " + transList.size());
          }
        }
      }

      log.warn(" $$$$$$$$$$ ALL " + txCounts + " took MIN = " + minTransInMs + " took MAX = "
          + maxTransInMs + " $$$$$$$$$");

      // Now Start getting receipts
      log.info("Initiating Receipts total " + transList.size());

      if (retrieveTxReceipt && fetchFullList) {
        Iterator<TransactionID> it = transList.iterator();
        j = 0;
        while (it.hasNext()) {
          TransactionID tid = (TransactionID) it.next();
          try {
            getTxReceiptNew(tid, cryptoStub, log);
            log.info("R # " + j++ + "; got " + tid);
          } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
            log.info("InvalidNodeTransactionPrecheckCode" + invalidNodeTransactionPrecheckCode);

            totalBadReceipts++;
          }
          totalGoodReceipts++;
        }
      }

      // If not full list get the last receipt
      if (!fetchFullList) {
        log.info("Sleeping for 2 sec before fetching last receipt");
        Thread.sleep(2000);
        getReceiptAndCleanQueue();
      }
    } catch (Exception ex) {
      log.error("Exception while creating files.", ex);
    }

    try {
      log.warn("Channel is about to shutdown " + channel.toString());
      this.channel.shutdown();

    } catch (Throwable tex) {
      log.error("Error While Closing Channel " + tex.getMessage());
      tex.printStackTrace();
    }

    System.currentTimeMillis();

    log.info("Total Good Receipts " + totalGoodReceipts);
    log.info("Total BAD Receipts " + totalBadReceipts);
    log.warn("Platform Did not accept " + platformNotAccepted);
    log.warn("** Thread ***" + thx.getName() + " DID THE JOB!");
  }

  private long getAccountBalance(CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount)
      throws Exception {
    long accountBalance = 0l;

    Response accountInfoResponse = TestHelper.executeAccountInfoQuery(stub, accountID,
        payerAccount, payerKeyPair, nodeAccount, MAX_TX_FEE, ResponseType.ANSWER_ONLY);
    if (accountInfoResponse != null) {
      accountBalance = accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance();
    }
    return accountBalance;

  }

  private Transaction doTransfer(AccountID fromAccount, PrivateKey fromKey, AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount, long amount) {
    boolean generateRecord = false;
    Timestamp timestamp =
        RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
        transactionDuration, generateRecord, "Test Transfer", fromAccount.getAccountNum(),
        -amount, toAccount.getAccountNum(), amount);
    // sign the tx
    List<PrivateKey> privKeysList = new ArrayList<>();
    privKeysList.add(payerAccountKey);
    privKeysList.add(fromKey);

    Transaction signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);

    return signedTx;
  }

  /**
   * for long running tests Just hold & clean the queue depth
   */
  private synchronized void getReceiptAndCleanQueue() throws Exception {

    TransactionID tid = this.transList.get(this.transList.size() - 1);
    TransactionReceipt txReceipt = null;
    try {
      txReceipt = getTxReceiptNew(tid, cryptoStub, log);
      log.warn("Receipt # txID = " + tid + "\nreceipt = " + txReceipt);
      if (txReceipt != null) {
        // Flush the receipt queue
        this.transList.clear();
      }
    } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
      log.info("InvalidNodeTransactionPrecheckCode" + invalidNodeTransactionPrecheckCode);

    }
  }

  /**
   * Creates a file on the ledger.
   * 
   * @param txHolder
   *
   * @return transaction response
   */
  public TransactionResponse createFile(AccountID payerID, AccountID nodeID, ByteString fileData,
      List<Key> waclKeyList, FileServiceBlockingStub stub, List<Transaction> txHolder)
      throws Throwable {
    log.debug("@@@ upload file: file size in byte = " + fileData.size());
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(CryptoServiceTest.DAY_SEC);

    Transaction FileCreateRequest = RequestBuilder.getFileCreateBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, CryptoServiceTest.transactionDuration, true,
        "FileCreate", fileData, fileExp, waclKeyList);
    TransactionBody body =
        com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(FileCreateRequest);
    body.getTransactionID();

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(waclKey);
    Transaction filesigned =
        TransactionSigner.signTransactionComplexWithSigMap(FileCreateRequest, keys, TestHelperComplex.pubKey2privKeyMap);

    log.debug("\n-----------------------------------");
    log.debug("FileCreate: request = " + filesigned);
    checkTxSize(filesigned);

    TransactionResponse response = fileStub.createFile(filesigned);
    txHolder.add(filesigned);
    log.debug("FileCreate Response :: " + response);
    return response;
  }

  protected void createStubs() throws URISyntaxException, IOException {
    channel = NettyChannelBuilder.forAddress(host, port).negotiationType(NegotiationType.PLAINTEXT)
        .directExecutor().enableRetry().build();
    cryptoStub = CryptoServiceGrpc.newBlockingStub(channel);
    fileStub = FileServiceGrpc.newBlockingStub(channel);
  }

  /**
   * Fetches the receipts, wait if necessary.
   *
   * @return the response
   */
  public Response fetchReceiptsConstant(final Query query, final CryptoServiceBlockingStub cstub,
      final Logger log)
      throws InvalidNodeTransactionPrecheckCode {
    if (log != null) {
      log.debug("GetTxReceipt: query=" + query);
    }
  
    Response transactionReceipts = cstub.getTransactionReceipts(query);
    Assert.assertNotNull(transactionReceipts);
    ResponseCodeEnum precheckCode = transactionReceipts.getTransactionGetReceipt().getHeader()
        .getNodeTransactionPrecheckCode();
    if (!precheckCode.equals(ResponseCodeEnum.OK)) {
      throw new InvalidNodeTransactionPrecheckCode("Invalid node transaction precheck code " +
          precheckCode.name() +
          " from getTransactionReceipts");
    }
  
    int cnt = 0;
    String status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
    while (cnt < MAX_RETRIES && status.equals(ResponseCodeEnum.UNKNOWN.name())) {
      cnt++;
      CommonUtils.napMillis(RETRY_FREQUENCY_MILLIS);
      try {
        transactionReceipts = cstub.getTransactionReceipts(query);
        status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
      } catch (StatusRuntimeException e) {
        if (log != null) {
          log.warn("getTransactionReceipts: RPC failed!");
        }
        status = ResponseCodeEnum.UNKNOWN.name();
      }
    }
  
    if (!status.equals(ResponseCodeEnum.SUCCESS.name())) {
      if (log != null) {
        log.warn("GetTxReceipt: took = " + cnt + " retries; receipt=" + transactionReceipts);
      }
    } else {
      if (log != null) {
        log.info("GetTxReceipt: took = " + cnt + " retries; receipt=" + transactionReceipts);
      }
    }
    return transactionReceipts;
  }

  /**
   * Sets the marker window size for getting receipts.
   * 
   * @param markerWindow number to transactions executed before receipt is retrieved
   */
  public void setMarkerWindow(int markerWindow) {
    MARKER_SIZE = markerWindow;
  }

  /**
   * Sets the frequency for retrying getting receipts when it's not available.
   * 
   * @param millis number of millisecs for the retry frequency
   */
  public void setRetryFreq(long millis) {
    RETRY_FREQUENCY_MILLIS  = millis;
  }

  /**
   * Gets the transaction receipt.
   *
   * @return the transaction receipt
   */
  public TransactionReceipt getTxReceiptNew(TransactionID transactionID,
      CryptoServiceBlockingStub stub, Logger log)
      throws InvalidNodeTransactionPrecheckCode {
    Query query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
        .build();
  
    TransactionReceipt rv;
    Response transactionReceipts = null;
    if(isExponentialBackoff) {
		transactionReceipts = fetchReceiptsWithExponentialBackoff(query, stub, log);
	} else {
		transactionReceipts = fetchReceiptsConstant(query, stub, log);
	}
    rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
    return rv;
  }

  /**
   * Fetches the receipts, wait with exponential backoff for retries.
   *
   * @return the response
   */
  public Response fetchReceiptsWithExponentialBackoff(final Query query, final CryptoServiceBlockingStub cstub,
      final Logger log)
      throws InvalidNodeTransactionPrecheckCode {
    if (log != null) {
      log.debug("GetTxReceipt: query=" + query);
    }
  
    Response transactionReceipts = cstub.getTransactionReceipts(query);
    Assert.assertNotNull(transactionReceipts);
    ResponseCodeEnum precheckCode = transactionReceipts.getTransactionGetReceipt().getHeader()
        .getNodeTransactionPrecheckCode();
    if (!precheckCode.equals(ResponseCodeEnum.OK)) {
      throw new InvalidNodeTransactionPrecheckCode("Invalid node transaction precheck code " +
          precheckCode.name() +
          " from getTransactionReceipts");
    }
  
    int cnt = 0;
    String status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
    while (cnt < MAX_RETRIES && status.equals(ResponseCodeEnum.UNKNOWN.name())) {
      CommonUtils.napMillis(getExpWaitTimeMillis(cnt++, MAX_WAIT_TIME_MILLIS));
      try {
        transactionReceipts = cstub.getTransactionReceipts(query);
        status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
      } catch (StatusRuntimeException e) {
        if (log != null) {
          log.warn("getTransactionReceipts: RPC failed!");
        }
        status = ResponseCodeEnum.UNKNOWN.name();
      }
    }
  
    if (!status.equals(ResponseCodeEnum.SUCCESS.name())) {
      if (log != null) {
        log.warn("GetTxReceipt: took = " + cnt + " retries; receipt=" + transactionReceipts);
      }
    } else {
      if (log != null) {
        log.info("GetTxReceipt: took = " + cnt + " retries; receipt=" + transactionReceipts);
      }
    }
    return transactionReceipts;
  }

  /**
   * Exponential wait time in millis capped by a max wait time.
   * 
   * @param retries num of retries
   * @param maxWaitMillis beyond which, the wait time will be this value
   * @return the wait time in millis
   */
  private static long getExpWaitTimeMillis(int retries, long maxWaitMillis) {
    long rv = 0;
    rv = (long) (Math.pow(2, retries) * RETRY_FREQUENCY_MILLIS);
    
    if(rv > maxWaitMillis) {
		rv = maxWaitMillis;
	}
    
    return rv;
  }

  public void setExpBackoff(boolean isExponentialBackoff) {
    this.isExponentialBackoff  = isExponentialBackoff;
  }

}
