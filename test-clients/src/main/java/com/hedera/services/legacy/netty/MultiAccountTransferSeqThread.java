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
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
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
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Does this
 * 1) Creates 2 accounts and performs N transfers at X TPS
 * 2) Tallies the last transfer & gets Receipts
 * 3) Repeats Step 1-2 for Z iterations
 *
 * @author oc
 */
public class MultiAccountTransferSeqThread implements Runnable  {

  private static final Logger log = LogManager.getLogger("MTASeq");
  private static int threadNumber =1;
  private static long MAX_THRESHOLD_FEE = 5000000000000000000L;
  private long initialMaxAccountBal=250000000000l;
  public static String fileName = TestHelper.getStartUpFile();
  private  CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private int transferCounts;
  private int numIterationsOfAccounts;
  private boolean retrieveTxReceipt;
  private boolean retrieveAccRecords;
  private ManagedChannel channel;
  private String host;
  private int port;
  private long defaultAccount;
  private long nodeAccount;
  private AccountID payerAccount;
  private AccountID nodeAccount3;
  private PrivateKey genesisPrivateKey;
  private InetAddress inetAddress;
  private Thread thx;
  private int tpsDesired ;
  private int napTime;
  private int channelConnects;
  private int channelShutdowns;
  private List<TransactionID> transList ;
  private List<Transaction> badTransList ;
  private boolean retryBadOnes;
  private boolean lifeLine;

  public MultiAccountTransferSeqThread(int port, String host, int batchSize,
      boolean retrieveTxReceipt, boolean _retrieveRecords, long defaultAccount, int _tpsDesired, int _numIterations) {

    reCreateChannel(host,port);

    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.transferCounts = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
    this.host = host;
    this.port = port;
    this.defaultAccount = defaultAccount;
    this.tpsDesired = _tpsDesired;
    this.channelConnects =1;
    this.channelShutdowns =0;



    try {

      Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

      List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
      // get Private Key
      this.genesisPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
      this.payerAccount = genesisAccount.get(0).getAccountId();
      this.nodeAccount3 = RequestBuilder
          .getAccountIdBuild(defaultAccount, 0l, 0l);

      this.inetAddress =InetAddress.getLocalHost();

      this.transList = new ArrayList<>();
      this.badTransList = new ArrayList<>();
      this.numIterationsOfAccounts = _numIterations;
      this.retryBadOnes = false;
      this.retrieveAccRecords = _retrieveRecords;
      this.lifeLine = true;

    }catch(Exception ex) {
      log.error("Exception EX" + ex.getMessage());
    }
  }

  public void reCreateChannel(String _ho, int _po){
    this.channel = NettyChannelBuilder.forAddress(_ho, _po)
        .negotiationType(NegotiationType.PLAINTEXT)
        .directExecutor()
        .enableRetry()
        .build();
  }


  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();


    int port = Integer.parseInt(properties.getProperty("port"));

  }

  public void start ()
  {
    String tName = inetAddress.getHostAddress() + "-" + threadNumber;
    log.info("Starting Thread " + tName);

    if (thx == null) {
      thx = new Thread (this,tName );
      thx.start ();
    }
    napTime = (1000 / tpsDesired);
    threadNumber++;

  }

  public void reConnectChannel() throws Exception {
    long stTime = System.nanoTime();
  if(channel==null || channel.getState(false) != ConnectivityState.READY) {
    log.warn("Recon " + channelConnects);
    channel = NettyChannelBuilder.forAddress(host, port)
        .negotiationType(NegotiationType.PLAINTEXT)
        .directExecutor()
        .enableRetry()
        .build();
    channelConnects++;
    long endTime = System.nanoTime() - stTime;
    log.error("Reconnect took NS " + endTime);
  }


  }

  public boolean shutdownChannel() throws Exception{
    long stTime = System.currentTimeMillis();

    if( (channel.getState(false) == ConnectivityState.READY) ||
        (channel.getState(false) == ConnectivityState.IDLE) )
    {
      log.warn("Shutting down channel " + channelConnects);
      channel.shutdown();
      Thread.sleep(100);
    }
    if(! channel.isShutdown()) {
		channel.shutdownNow();
	}
    channelShutdowns++;

    return channel.isShutdown();
  }



  public void run()
  {
  log.warn("Starting Iterations of " + numIterationsOfAccounts + " For Multi Transfer Test on " +  inetAddress.getHostAddress());
  for(int z = 0 ;(z<numIterationsOfAccounts && this.lifeLine );z++)
  {
    try {
      doOneIteration(z);
      log.warn("------------ Iteration " + z + " complete ---------------");
    }catch(Exception tx) {
      log.error(z + " -> Exception " , tx);
    }
    }

    try{
      log.info("Channel is about to shutdown " + channel.toString());
      this.channel.shutdown();

    }catch(Throwable tex)
    {
      log.error("Error While Closing Channel " + tex.getMessage());
      tex.printStackTrace();
    }
    log.warn("**Thread ***" + thx.getName() + "DID THE JOB");

  }

  public void doOneIteration(int _acItr)
  {

    // create 1st account by payer as genesis
    long sentAmtToAcc1 = 0l;
    long sentAmtToAcc2 = 0l;
    long minTransInMs = 100l;
    long maxTransInMs = -1l;

    try {

      if(channel == null || channel.getState(false) == ConnectivityState.SHUTDOWN)
      { reConnectChannel(); log.warn("Recon " + _acItr + " with " + channelConnects);}

      KeyPair firstPair = new KeyPairGenerator().generateKeyPair();

      AccountID newlyCreateAccountId1 , newlyCreateAccountId2;
      int maxAttempts = 100;
      int attempts =0;
      do {
        newlyCreateAccountId1 = createAccount(payerAccount,nodeAccount3,firstPair,initialMaxAccountBal);
        attempts++;
        log.info("Create account A1 itr " + attempts);
        if(attempts > maxAttempts) {
          log.error("200 Retries for A1 - BYE BYE NOW");
          this.lifeLine = false;
          break;
        }
        if((newlyCreateAccountId1 != null) && (newlyCreateAccountId1.getAccountNum() == 0))
        {
          log.warn("Got A1 Account Response as Zero");
          newlyCreateAccountId1 = null;
        }
        Thread.sleep(500);

      }while(newlyCreateAccountId1 == null);

      KeyPair secondPair = new KeyPairGenerator().generateKeyPair();

      attempts =0;
      do {
        newlyCreateAccountId2 = createAccount(payerAccount,nodeAccount3,secondPair,initialMaxAccountBal);
        attempts++;
        log.info("Create account A2 itr " + attempts);
        if(attempts > maxAttempts) {
          log.error("200 Retries for A2 - BYE BYE NOW");
          this.lifeLine = false;
          break;
        }
        if((newlyCreateAccountId2 != null) && (newlyCreateAccountId2.getAccountNum() == 0))
        {
          log.warn("Got A2 Account Response as Zero");
          newlyCreateAccountId2 = null;
        }
        Thread.sleep(500);

      }while(newlyCreateAccountId2 == null);


      // 2nd account


    long start = System.currentTimeMillis();

    long ts,te,tm=0l;

    log.warn("Autoconnect " + transferCounts + " with naptime of " + napTime +
        " between " + newlyCreateAccountId1.getAccountNum()  + " and " +  newlyCreateAccountId2.getAccountNum()  );
    long transferStartTime = System.currentTimeMillis();
    for (int i = 0; ( i < transferCounts && this.lifeLine ); i++) {
      ts = System.currentTimeMillis();
      if(channel == null || channel.getState(false) == ConnectivityState.SHUTDOWN)
      { reConnectChannel(); }

      Transaction transfer1;
      if (i % 2 == 0) {
        transfer1 = createTransfer(newlyCreateAccountId2,secondPair.getPrivate(),
                newlyCreateAccountId1,newlyCreateAccountId2,secondPair.getPrivate(),nodeAccount3,9999l);



        sentAmtToAcc1 += 9999l;
      } else {
        transfer1 = createTransfer(newlyCreateAccountId1, firstPair.getPrivate(),
                newlyCreateAccountId2, newlyCreateAccountId1,
                firstPair.getPrivate(), nodeAccount3, 999l);

        sentAmtToAcc2 += 999l;
      }

      try {
        stub = CryptoServiceGrpc.newBlockingStub(channel);
        long deadLineMs = 200l;

        TransactionResponse transferRes = null;

        try{
          transferRes = stub.cryptoTransfer(transfer1);
        }catch(Exception dex) {
          log.error("Skipping this due to " + dex.getMessage());
        }

        if ((transferRes != null) && (ResponseCodeEnum.OK == transferRes.getNodeTransactionPrecheckCode())) {
        TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
          transList.add(transferBody.getTransactionID());
        } else
          {
          log.error("Got Bad Precheck Stuff ** Adding to Retry Queue ** " + transferRes
              .getNodeTransactionPrecheckCode());
          if(transferRes.getNodeTransactionPrecheckCode() == ResponseCodeEnum.TRANSACTION_OVERSIZE){
            badTransList.add(transfer1);
          }
        }
        te = System.currentTimeMillis();
        tm = te - ts;
        log.info("T # " + i + " sent in " + tm + " millis");
        if( i==0) { maxTransInMs = minTransInMs = tm; }
        if( tm > maxTransInMs) {
          maxTransInMs = tm;
        }
        if( tm < minTransInMs) {
          minTransInMs = tm;
        }

        try {
          if ((i != 0) && (i % 50 == 0)) {
            log.info("T # " + i + " sent in " + tm + " millis");
            getReceiptAndCleanup(1, i);

          }
        }catch(Exception ex) { log.warn("Error in receipt cleanup " , ex); }

      }catch(Throwable thx){

        log.error("* ERROR * " + thx.getMessage());
        if((channel.getState(false) == ConnectivityState.SHUTDOWN) ||
            (channel.getState(false) == ConnectivityState.TRANSIENT_FAILURE)){
          log.error("* Connecting due to SHUTDOWN & TRANSIENT FAIL * " );
          reConnectChannel();
        }
    }
      //This is calculated by TPS Desired Param
      Thread.sleep(napTime);

    }
    log.warn(" $$ ALL " + transferCounts + " took MIN= " + minTransInMs  + " took MAX= " + maxTransInMs + " $$$$$$$$$");

    long transferEndTime = System.currentTimeMillis() - transferStartTime ;
    log.warn("** Total Time for  " + transferCounts + " is " + transferEndTime);

    if(retryBadOnes && this.lifeLine) {
      log.warn("Submitting the pending " +  badTransList.size() + " Retry Trans " ) ;
      try {
        Iterator it2 = badTransList.iterator();
        transferStartTime = System.currentTimeMillis();
        while (it2.hasNext()) {
          Transaction nextT = (Transaction) it2.next();
          TransactionResponse t2retry = stub.cryptoTransfer(nextT);
          if (ResponseCodeEnum.OK == t2retry.getNodeTransactionPrecheckCode()) {
            TransactionBody transferBody = TransactionBody.parseFrom(nextT.getBodyBytes());
            transList.add(transferBody.getTransactionID());
          }
        }
        transferEndTime = transferStartTime - System.currentTimeMillis();
        log.info("** Total Time for RETRY is " + transferEndTime);
      } catch (Throwable t2x) {
        log.error("Error Happened in Retry " + t2x.getMessage());
      }
    }
    else {
      if(badTransList.size() > 0) {
		  badTransList.clear();
	  }
    }

// Get Records Now
      if(this.retrieveAccRecords && this.lifeLine) {
        long recordFetchStart = System.currentTimeMillis();
        Response account1Records = TestHelper
            .getAccountRecords(stub, newlyCreateAccountId1, newlyCreateAccountId1, firstPair,
                nodeAccount3);

        log.warn("Num Records A1 " + account1Records.getCryptoGetAccountRecords().getRecordsCount());

        long totalRecordFetchTime =  System.currentTimeMillis() - recordFetchStart;
        recordFetchStart = System.currentTimeMillis();
        log.warn("Time to fetch Account 1 Records " + totalRecordFetchTime);

        Response account2Records = TestHelper
            .getAccountRecords(stub, newlyCreateAccountId2, newlyCreateAccountId2, secondPair,
                nodeAccount3);
        log.warn("Num Records A2 " + account2Records.getCryptoGetAccountRecords().getRecordsCount());
        totalRecordFetchTime =  System.currentTimeMillis() - recordFetchStart;
        log.warn("Time to fetch Account 2 Records " + totalRecordFetchTime);
      }


    } catch(Exception ex) {
      log.error("Exception while creating accounts " + ex.getMessage());
      ex.printStackTrace();
    }



    long end = System.currentTimeMillis();

    log.warn("Total Amount sent from Account 1 to 2 " + sentAmtToAcc1);
    log.warn("Total Amount sent from Account 2 to 1 " + sentAmtToAcc2);

  }

  /**
   *
   * @param payerAccount
   * @param nodeAccount
   * @param pair
   * @param initialBalance
   * @return
   */
  public AccountID createAccount(AccountID payerAccount, AccountID nodeAccount,
      KeyPair pair, long initialBalance){

    AccountID newlyCreateAccountId = null;

    try {
      Transaction transaction = TestHelper
          .createAccount(payerAccount, nodeAccount3, pair, initialBalance,
                  50000000l,MAX_THRESHOLD_FEE,MAX_THRESHOLD_FEE);
      Transaction signTransaction = TransactionSigner
          .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
      // try this until the platform is ok

      TransactionResponse response = stub.createAccount(signTransaction);
      log.info("Response" + response);
      log.info(" Got Precheck " + response.getNodeTransactionPrecheckCode());
      log.info(
          "PreCheck Response of Create first account :: " + response
              .getNodeTransactionPrecheckCode()
              .name());
      stub = CryptoServiceGrpc.newBlockingStub(channel);

      TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
      try {
        TransactionReceipt accReceipt = TestHelper.getTxReceipt(body.getTransactionID(), stub);
        log.info("Receipt" + accReceipt);
        if( ResponseCodeEnum.SUCCESS != accReceipt.getStatus())
        {
          log.error("Account Receipt was not SUCCESS" + accReceipt );
        }

        newlyCreateAccountId = accReceipt.getAccountID();


        log.info("Got Account " + newlyCreateAccountId);

      } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
        invalidNodeTransactionPrecheckCode.printStackTrace();
      }
    }catch (Throwable thx) {
      log.error("Ex createAccount" + thx.getMessage());
    }
    return newlyCreateAccountId;

  }

  //Cleanup the queue
  public void getReceiptAndCleanup(int num, int index) {

    int iterations = num;
    if (this.retrieveTxReceipt) {
      Iterator it = this.transList.iterator();
      int j=0;
      while(it.hasNext() && iterations > 0){
        TransactionID tid = (TransactionID)it.next();
        TransactionReceipt txReceipt = null;
        try {

          txReceipt = TestHelper.getTxReceipt(tid, stub);

         log.warn("R # " + j++ + "got at " + index);
        } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
          log.info("InvalidNodeTransactionPrecheckCode" + invalidNodeTransactionPrecheckCode);
        }
        iterations --;
      }
    }
    this.transList.clear();
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
  private Transaction createTransfer(AccountID fromAccount, PrivateKey fromKey,
                                 AccountID toAccount,AccountID payerAccount,
                                     PrivateKey payerAccountKey, AccountID nodeAccount,
                                     long amount) {
    boolean generateRecord = true;
    long maxTransfee = 8000000l;
    Timestamp timestamp = RequestBuilder
            .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), maxTransfee, timestamp,
            transactionDuration,
            generateRecord, "PTestxTransfer", fromAccount.getAccountNum(), -amount,
            toAccount.getAccountNum(), amount);
    // sign the tx
    List<PrivateKey> privKeysList = new ArrayList<>();
    privKeysList.add(payerAccountKey);
    privKeysList.add(fromKey);

    Transaction signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);

    return signedTx;
  }

}
