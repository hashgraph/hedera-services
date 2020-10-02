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

import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.sun.management.OperatingSystemMXBean;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongycastle.util.Arrays;

/**
 * Does this 1) Creates 2 accounts and performs N transfers at X TPS 2) Tallies the last transfer &
 * gets Receipts 3) Repeats Step 1-2 for Z iterations
 *
 * @author oc
 */
public class MultiAccountBurstTransferSeqThread implements Runnable {

  private static final Logger log = LogManager.getLogger("MTASeq");
  private static int threadNumber = 1;
  private static long MAX_THRESHOLD_FEE = 5000000000000000000L;
  private static long initialMaxAccountBal = 2500000000000l;
  private static boolean tallyAccountInfo = false;
  public static String fileName = TestHelper.getStartUpFile();
  private CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private int transferCounts;
  private int numIterationsOfAccounts;
  private boolean retrieveTxReceipt;
  private ManagedChannel channel;
  private String host;
  private int port;
  private AccountID payerAccount;
  private AccountID nodeAccount3;
  private PrivateKey genesisPrivateKey;
  private KeyPair genKeyPair;
  private InetAddress inetAddress;
  private Thread thx;
  private int tpsDesired;
  private int napTime;
  private int channelConnects;
  private boolean lifeLine;
  private HashMap<AccountID, KeyPair> accountMap;
  private String threadName;

  public MultiAccountBurstTransferSeqThread(int port, String host, int batchSize,
                                            boolean retrieveTxReceipt, boolean _retrieveRecords, long defaultAccount, int _tpsDesired,
                                            int _numIterations) {

    reCreateChannel(host, port);

    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.transferCounts = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
    this.host = host;
    this.port = port;
    this.tpsDesired = _tpsDesired;
    this.channelConnects = 1;
    try {
    //
      Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

      List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
      // get Private Key
      KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
      this.genesisPrivateKey = genKeyPairObj.getPrivateKey();
      this.genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), this.genesisPrivateKey);
      this.payerAccount = genesisAccount.get(0).getAccountId();
      this.nodeAccount3 = RequestBuilder.getAccountIdBuild(defaultAccount, 0l, 0l);

      this.inetAddress = InetAddress.getLocalHost();

      this.numIterationsOfAccounts = _numIterations;
      this.lifeLine = true;
      this.accountMap = new HashMap<>();

    } catch (Exception ex) {
      log.error(threadName + "Exception EX" + ex.getMessage());
    }
  }

  public void reCreateChannel(String _ho, int _po) {
    this.channel =
            NettyChannelBuilder.forAddress(_ho, _po).negotiationType(NegotiationType.PLAINTEXT)
                    .directExecutor().enableRetry().build();
  }

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    Integer.parseInt(properties.getProperty("port"));

  }

  public void start() {
    String tName = inetAddress.getHostAddress() + "-" + threadNumber;
    log.info("Started Thread " + tName);

    if (thx == null) {
      thx = new Thread(this, tName);
      thx.start();
    }
    napTime = (1000 / tpsDesired);
    threadName = "T" + threadNumber + "::";
    // if(napTime < 10)
    threadNumber++;

  }

  public void reConnectChannel() throws Exception {
    long stTime = System.nanoTime();
    if (channel == null || channel.getState(false) != ConnectivityState.READY) {
      log.warn(threadName + "Recon " + channelConnects);
      channel =
              NettyChannelBuilder.forAddress(host, port).negotiationType(NegotiationType.PLAINTEXT)
                      // gRPC
                      // encryption
                      .directExecutor().enableRetry().build();
      channelConnects++;
      long endTime = System.nanoTime() - stTime;
      log.error("Reconnect took NS " + endTime);
    }


  }

  public boolean shutdownChannel() throws Exception {
    System.currentTimeMillis();

    if ((channel.getState(false) == ConnectivityState.READY)
            || (channel.getState(false) == ConnectivityState.IDLE)) {
      log.warn(threadName + "Shutting down channel " + channelConnects);
      channel.shutdown();
      Thread.sleep(100);
    }
    if (!channel.isShutdown()) {
		channel.shutdownNow();
	}
    return channel.isShutdown();
  }



  public void run() {
    log.warn(threadName + "Starting Iterations of " + numIterationsOfAccounts
            + " For Multi Transfer Test on " + inetAddress.getHostAddress());
    for (int z = 0; (z < numIterationsOfAccounts && this.lifeLine); z++) {
      try {
        createOneAccount(z + 1, numIterationsOfAccounts);
        log.info("------------ Account " + z + " done ---------------");
        // Thread.sleep(1000);
      } catch (Exception tx) {
        log.error(threadName + z + " -> Exception ", tx);
      }

    }
    log.warn(threadName + "Accounts done, 5s break ");
    try {
      Thread.sleep(5000);
    } catch (Exception slx) {
      log.error(threadName + "Colog.error(threadName + eep ", slx);
    }
    // start crazy volumes of transfers
    doTransfersBetweenAccounts();
    try {
      log.info("Channel is abt to shutdown " + channel.toString());
      this.channel.shutdown();

    } catch (Throwable tex) {
      log.error(threadName + "Error While Closing Channel " + tex.getMessage());
      tex.printStackTrace();
    }
    log.warn(threadName + "**Thread ***" + thx.getName() + "DID THE JOB");

  }

  /**
   *
   * @param _acItr
   */
  public void createOneAccount(int _acItr, int _totals) {

    System.currentTimeMillis();
    try {

      if (channel == null || channel.getState(false) == ConnectivityState.SHUTDOWN) {
        reConnectChannel();
        log.warn(threadName + "Reconn " + _acItr + " with " + channelConnects);
      }

      KeyPair firstPair = new KeyPairGenerator().generateKeyPair();

      AccountID newAccountId1;
      int maxAttempts = 100;
      int attempts = 0;
      do {
        newAccountId1 = createAccount(payerAccount, nodeAccount3, firstPair, initialMaxAccountBal);
        attempts++;
        log.info("Account A1 itr " + attempts);
        if (attempts > maxAttempts) {
          log.error(threadName + "100 Retries for A1 - BYE BYE NOW");
          this.lifeLine = false;
          break;
        }
        if ((newAccountId1 != null) && (newAccountId1.getAccountNum() == 0)) {
          log.warn(threadName + "A1 Account Response as Zero, wait one sec and retry.");
          newAccountId1 = null;
          Thread.sleep(1000);
        }
        Thread.sleep(500);

      } while (newAccountId1 == null);

      if (newAccountId1 != null) {
		  this.accountMap.put(newAccountId1, firstPair);
	  }

      String pKeyHex =
              HexUtils.bytes2Hex(((EdDSAPublicKey) firstPair.getPublic()).getAbyte());
      log.info(threadName + " Account  " + _acItr + " created: " + newAccountId1.getAccountNum()
              + ", pKeyHex=" + pKeyHex);
    } catch (Exception ex) {
      log.error(threadName + "Exception while creating accounts ", ex);
    }
  }

  /**
   *
   * @param payerAccount
   * @param nodeAccount
   * @param pair
   * @param initialBalance
   * @return
   */
  public AccountID createAccount(AccountID payerAccount, AccountID nodeAccount, KeyPair pair,
                                 long initialBalance) {

    AccountID newlyCreateAccountId = null;

    try {
      Transaction transaction = TestHelper.createAccount(payerAccount, nodeAccount3, pair,
              initialBalance, 50000000l, MAX_THRESHOLD_FEE, MAX_THRESHOLD_FEE);
      Transaction signTransaction = TransactionSigner.signTransaction(transaction,
              Collections.singletonList(genesisPrivateKey));
      // try this until the platform is ok
      TransactionResponse response = stub.createAccount(signTransaction);
      log.info("createAccount response :: " + response);

      if(response != null && ResponseCodeEnum.OK != response.getNodeTransactionPrecheckCode()) {
		  return null;
	  }

      stub = CryptoServiceGrpc.newBlockingStub(channel);
      Thread.sleep(100);
      TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
      try {
        TransactionReceipt accReceipt = TestHelper.getTxReceipt(body.getTransactionID(), stub, log,
            host);
        log.info("Receipt" + accReceipt);
        if (ResponseCodeEnum.SUCCESS != accReceipt.getStatus()) {
          log.error(threadName + "Account Receipt was NOT SUCCESS: " + accReceipt);
          return null;
        }

        newlyCreateAccountId = accReceipt.getAccountID();

        log.info("Got Account " + newlyCreateAccountId);
        if(tallyAccountInfo) {
          //check if the this account has the correct key to avoid problem of duplicate tx ids
          Response infoQueryResponse = TestHelper.getCryptoGetAccountInfo(stub,
              newlyCreateAccountId, payerAccount, genKeyPair, nodeAccount);
          byte[] keyBytesFromLedger = infoQueryResponse.getCryptoGetInfo().getAccountInfo().getKey().getKeyList().getKeys(0).getEd25519().toByteArray();
          byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
          if (!Arrays.areEqual(pubKey, keyBytesFromLedger)) {
            log.error(":( Key on ledger different from client: account=" + newlyCreateAccountId + "\nclient key hex=" + HexUtils.bytes2Hex(pubKey)
                    + "\nledger key hex=" + HexUtils.bytes2Hex(keyBytesFromLedger));
            return null;
          }
        }
      } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
        log.error(threadName + "invalidNodeTransactionPrecheckCode: ",
                invalidNodeTransactionPrecheckCode);
        return null;
      }
    } catch (Throwable thx) {
      log.error(threadName + "Error in createAccount " + thx.getMessage());
    }
    return newlyCreateAccountId;

  }



  /**
   * The previous step must have created X Accounts This method does transferCounts of Transfers
   * between two accounts repeats that step X * X times ignoring same account transfers If there are
   * 10 accounts, it will do 98 transfer batches of 100 each
   */
  public void doTransfersBetweenAccounts() {
    log.info("Starting Transfers between Accounts");
    OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    List<AccountID> accountsList = new ArrayList<>();

    Iterator it = this.accountMap.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry pair = (Map.Entry) it.next();
      accountsList.add((AccountID) pair.getKey());
    }
    try {
      int totalGoodReceipts = 0;
      int totalBadReceipts = 0;
      long startTime = 0l;
      long memoryCommitted = 0l;
      double cpuLoad = 0.0d;

      int accIters = accountsList.size();

      log.warn(threadName + "Will perform  " + ((accIters * accIters) - 2) + " itr with " + napTime
              + " sleep");
      AccountID fromAccount, toAccount;
      KeyPair fromKey, toKey;
      double percDone = 0.0d;
      BigDecimal bd, cpu;

      int totalTransferCnt = 0;
      int p = 0;
      int s = 0;
      int o = 0;
      for (int i = 0; i < accIters; i++) {
        fromAccount = accountsList.get(i);
        fromKey = this.accountMap.get(fromAccount);
        for (int j = 0; j < accIters; j++) {
          toAccount = accountsList.get(j);
          toKey = this.accountMap.get(toAccount);

          if (fromAccount.getAccountNum() == toAccount.getAccountNum()) {
            continue;
          }
          log.info("Initiating transfers between " + fromAccount.getAccountNum() + " to "
                  + toAccount.getAccountNum());

          startTime = System.currentTimeMillis();
          for (int x = 0; x < transferCounts; x++) {

            if (channel == null || channel.getState(false) == ConnectivityState.SHUTDOWN) {
              reConnectChannel();
            }

            Transaction transfer;
            if (x % 2 == 0) {
              transfer = createTransfer(toAccount, toKey.getPrivate(), fromAccount, toAccount,
                      toKey.getPrivate(), nodeAccount3, 9999l);
            } else {
              transfer = createTransfer(fromAccount, fromKey.getPrivate(), toAccount, fromAccount,
                      fromKey.getPrivate(), nodeAccount3, 999l);
            }

            try {
              stub = CryptoServiceGrpc.newBlockingStub(channel);
              TransactionResponse transferRes = null;

              try {
                totalTransferCnt++;
                transferRes = stub.cryptoTransfer(transfer);
              } catch (Exception dex) {
                log.error(threadName + "Skip this due to " + dex.getMessage());
              }

              if ((transferRes != null)
                      && (ResponseCodeEnum.OK == transferRes.getNodeTransactionPrecheckCode())) {
                TransactionBody transferBody = TransactionBody.parseFrom(transfer.getBodyBytes());
                totalGoodReceipts++;
                try {
                  if (this.retrieveTxReceipt && (x != 0) && (x % 50 == 0)) {
                    getReceiptAndCleanup(transferBody.getTransactionID(), x);
                  }
                } catch (Exception ex) {
                  log.warn(threadName + "Error in receipt fetch ", ex);
                }
              } else {
                if ((transferRes != null) && (ResponseCodeEnum.INVALID_SIGNATURE == transferRes
                        .getNodeTransactionPrecheckCode())) {
                  s++;
                  String pKey1Hex =
                          HexUtils.bytes2Hex(((EdDSAPublicKey) fromKey.getPublic()).getAbyte());
                  String pKey2Hex =
                          HexUtils.bytes2Hex(((EdDSAPublicKey) toKey.getPublic()).getAbyte());

                  log.error(threadName + "transfer Response=" + transferRes + "\ntransfer tx="
                          + transfer + "\nbody="
                          + com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(transfer)
                          + "\npublicKey: newlyCreateAccountId1=" + fromAccount.getAccountNum()
                          + ", pubKey1Hex=" + pKey1Hex + "\npublicKey: newlyCreateAccountId2="
                          + toAccount.getAccountNum() + ", pubKey2Hex=" + pKey2Hex);
                } else if ((transferRes != null)
                        && (ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED == transferRes
                        .getNodeTransactionPrecheckCode())) {
                  // Platform did not create -- slow down
                  p++;
                  log.info(threadName + "transfer Response=" + transferRes + ", total transfer=" + totalTransferCnt + ", ptx=" + p);
                  Thread.sleep(250);
                } else {
                  o++;
                  log.error(threadName + "OTHER PRECHECK erros: transfer Response=" + transferRes);
                }
                totalBadReceipts++;
              }
            } catch (Exception ex) {
              log.error(threadName + "Transfer Err Happened ", ex);
            }

            Thread.sleep(napTime);
          }
          log.warn(threadName + "Tot tx=" + totalTransferCnt + ", Good="
                  + totalGoodReceipts + ", Fail=" + totalBadReceipts + ", ptx=" + p + ", sig=" + s
                  + ", other=" + o + "-> Itr " + transferCounts + " [" + fromAccount.getAccountNum()
                  + "," + toAccount.getAccountNum() + "] in " + (System.currentTimeMillis() - startTime)
                  + " ms");
        }
        percDone = ((double) i + 1) / ((double) accIters);
        bd = new BigDecimal(percDone);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        cpuLoad = osBean.getProcessCpuLoad();
        cpu = new BigDecimal(cpuLoad);
        cpu = cpu.setScale(2, RoundingMode.HALF_UP);
        memoryCommitted = osBean.getCommittedVirtualMemorySize() / 1048576;
        log.warn(
                threadName + "................ " + 100 * bd.doubleValue() + " % done  ..............");
        log.warn(threadName + "................ " + cpu.doubleValue() + " CpuLoad, "
                + memoryCommitted + " Mb CMem ..........");
      }
    } catch (Exception tex) {
      log.error(threadName + "Error Happened ", tex);
    }
  }


  /**
   *
   * @param fromAccount
   * @param fromKey
   * @param toAccount
   * @param payerAccount
   * @param payerAccountKey
   * @param nodeAccount
   * @param amount
   * @return
   */
  private Transaction createTransfer(AccountID fromAccount, PrivateKey fromKey, AccountID toAccount,
                                     AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount, long amount) {
    boolean generateRecord = true;
    long maxTransfee = 8000000l;
    Timestamp timestamp =
            RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(90);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), maxTransfee, timestamp,
            transactionDuration, generateRecord, "PTestxTransfer", fromAccount.getAccountNum(),
            -amount, toAccount.getAccountNum(), amount);
    // sign the tx
    List<PrivateKey> privKeysList = new ArrayList<>();
    privKeysList.add(payerAccountKey);
    privKeysList.add(fromKey);

    Transaction signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);

    return signedTx;
  }

  /**
   *
   * @param tid
   * @param index
   */
  public void getReceiptAndCleanup(TransactionID tid, int index) {

    TransactionReceipt txReceipt = null;

    if (this.retrieveTxReceipt) {

      try {
        txReceipt = TestHelper.getTxReceipt(tid, stub);
        if (txReceipt != null && txReceipt.getStatus() == ResponseCodeEnum.SUCCESS) {
          log.warn(threadName + "Good R # got at " + index);
        } else {
          log.warn(threadName + "Bad R # got at " + index + " ~~~ maxed Out ~~~ "
                  + txReceipt.getStatus());
        }
      } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
        log.warn(threadName + "Invalid NodeTransactionPrecheckCode"
                + invalidNodeTransactionPrecheckCode);

      }

    }

  }

  /**
   *
   * @param query
   * @param cstub
   * @param maxReceiptRetry
   * @param retrySleepTimeMs
   * @return
   * @throws InvalidNodeTransactionPrecheckCode
   */
  public static Response fetchReceipts(final Query query,
                                       final CryptoServiceGrpc.CryptoServiceBlockingStub cstub, int maxReceiptRetry,
                                       int retrySleepTimeMs) throws InvalidNodeTransactionPrecheckCode {
    long start = System.currentTimeMillis();
    if (log != null) {
      log.debug("GetTxReceipt: query=" + query);
    }

    Response transactionReceipts = cstub.getTransactionReceipts(query);
    ResponseCodeEnum precheckCode =
            transactionReceipts.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode();
    if (!precheckCode.equals(ResponseCodeEnum.OK)) {
      throw new InvalidNodeTransactionPrecheckCode("Invalid transaction precheck code "
              + precheckCode.name() + " from getTransactionReceipts");
    }

    int cnt = 0;
    String status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
    while (cnt < maxReceiptRetry && status.equals(ResponseCodeEnum.UNKNOWN.name())) {
      long napMillis = retrySleepTimeMs;


      cnt++;
      try {
        Thread.sleep(napMillis);
        transactionReceipts = cstub.getTransactionReceipts(query);
        status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
      } catch (Throwable te) {
        if (log != null) {
          log.warn("getTransactionReceipts: RPC failed!", te);
        }
        status = ResponseCodeEnum.UNKNOWN.name();
      }
    }

    long elapse = System.currentTimeMillis() - start;
    long secs = elapse / 1000;
    long milliSec = elapse % 1000;
    String msg = "GetTxReceipt: took = " + secs + " second " + milliSec + " millisec; retries = "
            + cnt + "; receipt=" + transactionReceipts;
    if (!status.equals(ResponseCodeEnum.SUCCESS.name())) {
      if (log != null) {
        log.warn(msg);
      }
    } else {
      if (log != null) {
        log.info(msg);
      }
    }
    return transactionReceipts;
  }

}
