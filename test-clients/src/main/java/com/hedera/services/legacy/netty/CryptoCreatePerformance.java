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
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Single Thread of a performance test that connects to a node & creates X accounts (command line
 * args), fetches records every 20 accounts and winds up the test.
 *
 * @author oc
 */
public class CryptoCreatePerformance implements Runnable {

  private static final Logger log = LogManager.getLogger(CryptoCreatePerformance.class);
  private static int threadNumber =1;
  private static long MAX_THRESHOLD_FEE = 5000000000000000000L;
  public static String fileName = TestHelper.getStartUpFile();
  private CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private int BATCH_SIZE = 200;
  private boolean retrieveTxReceipt;
  private boolean retrieveTxRecord;
  private ManagedChannel channel;
  private String host;
  private int port;
  private long nodeAccount;
  private AccountID payerAccount;
  private AccountID nodeAccount3;
  private PrivateKey genesisPrivateKey;
  private KeyPair genKeyPair;
  private InetAddress inetAddress;
  private Thread thx;
  private HashMap<AccountID,Response> testResults;
  private ArrayList<TransactionID> transIdList;

  private int goodResponse =0;
  private int badResponse=0;
  private int goodReceipt=0;
  private int badReceipt=0;
  private int channelConnects =0;
  private boolean variableTPS = true;

  public CryptoCreatePerformance(int port, String host, int batchSize, boolean retrieveTxReceipt, long nodeAccount, boolean retrieveTxRecord) {
    // connecting to the grpc server on the port
//    channel = ManagedChannelBuilder.forAddress(host, port)
//        .usePlaintext()
//        .build();

    this.channel = NettyChannelBuilder.forAddress(host, port)
        .negotiationType(NegotiationType.PLAINTEXT)
        .directExecutor()
        .build();

    this.nodeAccount = nodeAccount;

    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.BATCH_SIZE = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
    this.host = host;
    this.port = port;

    try {

      Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

      List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
      // get Private Key
      KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
      this.genesisPrivateKey = genKeyPairObj.getPrivateKey();
      this.genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
      this.payerAccount = genesisAccount.get(0).getAccountId();
      this.nodeAccount3 = RequestBuilder
          .getAccountIdBuild(nodeAccount, 0l, 0l);

      TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, nodeAccount3);

      this.inetAddress =InetAddress.getLocalHost();
      this.retrieveTxRecord = retrieveTxRecord;
      this.transIdList = new ArrayList<TransactionID>();
      this.testResults = new HashMap<AccountID,Response>();

    }catch(Exception ex) {
      log.error("Exception EX" + ex.getMessage());
    }
  }

  public CryptoCreatePerformance() {
    log.error("Dont do this please ..");
  }

  public static void main(String args[])
      throws IOException, URISyntaxException, InvalidNodeTransactionPrecheckCode {
    String host;
    Properties properties = TestHelper.getApplicationProperties();

    if ((args.length) > 0) {
      host = args[0];
    }
    else
    {
      host = "107.21.183.51";// properties.getProperty("host");
    }


    int port = Integer.parseInt(properties.getProperty("port"));
    log.info("Connecting host = " + host + "; port = " + port);
    int numTransfer = 1000000;
    boolean retrieveTxReceipt = true;
//    CryptoCreatePerformance cryptoCreatePerformance =
//        new CryptoCreatePerformance(port, host, numTransfer, retrieveTxReceipt);
    //cryptoCreatePerformance.demo();

  }

  public void start ()
  {
    String threadName = inetAddress.getHostAddress() + "-" + threadNumber;
    log.warn("Starting Thread " + threadName);

    if (thx == null) {
      thx = new Thread (this,threadName );
      thx.start ();
    }
    threadNumber++;

  }

  public void run()  {


    long start = System.currentTimeMillis();

    for (int i = 0; i < this.BATCH_SIZE; i++) {
      try {

        if(this.channel == null || this.channel.getState(false) == ConnectivityState.SHUTDOWN)
        { reConnectChannel(); }

        // create 1st account by payer as genesis
        log.info("Create Req " + (i + 1));
        KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
        Transaction transaction = TestHelper
            .createAccount(payerAccount, nodeAccount3, firstPair, 12300000000l,
                FeeClient.getMaxFee(), MAX_THRESHOLD_FEE, MAX_THRESHOLD_FEE);
        Transaction signTransaction = TransactionSigner
            .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
        TransactionResponse response = stub.createAccount(signTransaction);
        Assert.assertNotNull(response);
        TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
        if (ResponseCodeEnum.OK == response.getNodeTransactionPrecheckCode()) {
          goodResponse++;
          transIdList.add(body.getTransactionID());

        } else {
          badResponse++;
          log.info("Got a bad response " + response.getNodeTransactionPrecheckCode());
        }
        stub = CryptoServiceGrpc.newBlockingStub(channel);

        if(i!=0 && (i % 20 ==0)) {
			getReceiptRecords(20, i);
		}

        if(variableTPS)
        { Thread.sleep((long)(Math.random() * 100)); }

      } catch (Throwable thx) {

        log.error("Exception " , thx);
      }
    }
    long end = System.currentTimeMillis();
    log.warn("Total time for 200 createAccount  " + (end - start) + "Mil seconds");
    log.info("Total Good Account Response" + goodResponse);
    log.info("Total Good Account Receipts" + goodReceipt);
    log.info("Total BAD Account Response" + badResponse);
    log.info("Total Good Account Receipts" + badReceipt);
    log.warn("The records are as follows");
    log.warn("Got : " + testResults.size() + " Records");


    try{
      this.channel.shutdown();

    }catch(Throwable thx){
      log.error("Shutdown Channel had Error " + thx.getMessage());
    }
  }

  /**
   * Burst mode fetch of records
   * @param num
   * @param itr
   */
  private void getReceiptRecords(int num, int itr) {

    TransactionID newId = null;
    TransactionReceipt tReceipt = null;
    log.warn("Fetching R&R " + itr);
    for (int i = 0 ;(i < num && this.transIdList.size() > 0); i++) {
      try {

        newId = this.transIdList.get(0);
        AccountID newlyCreateAccountId1 = null;
        if (this.retrieveTxReceipt) {
          try {
            tReceipt = TestHelper.getTxReceipt(newId, stub);
            newlyCreateAccountId1 = tReceipt.getAccountID();
            goodReceipt++;
          } catch (Exception ex) {
            log.info("Error Happened " ,ex);
            badReceipt++;

          }

          if (this.retrieveTxRecord) {
            try {
              Response accountRecords = TestHelper
                  .getAccountRecords(stub, newlyCreateAccountId1, payerAccount, genKeyPair,
                      nodeAccount3);
              testResults.put(newlyCreateAccountId1, accountRecords);
            } catch (Exception ex) {
              log.error("Fetch Records Failed.", ex);
            }

            if((itr + 50) > this.BATCH_SIZE) {
				log.warn(itr + " Acct= " + newlyCreateAccountId1);
			}
          }

        }
        this.transIdList.remove(0);
      }catch(Exception ex) {
        log.error("Something Went Wrong", ex);
      }
    }
  }

  public void reConnectChannel() throws Exception {
    long stTime = System.nanoTime();
    if (channel == null || channel.getState(false) != ConnectivityState.READY) {
      log.info("Connectivity is not READY reConnectChannel " + channelConnects);
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


  }
