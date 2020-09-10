package com.hedera.services.legacy.client.test;

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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.builder.RequestBuilder;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CryptoCreate extends ClientBaseThread {

  private static final Logger log = LogManager.getLogger(CryptoCreate.class);
  public static String fileName = TestHelper.getStartUpFile();

  public static long initialBalance = 100000L;
  private int TPS = 200;
  private int numberOfIterations;

  private AccountID payerAccount;

  private boolean checkRunning = true;

  private LinkedBlockingQueue<Pair<Transaction, KeyPair>> accountTranList = new LinkedBlockingQueue<>();

  /**
   * Each client runs with two thread, one sending account creation request, one checking for its
   * results.
   *
   * Create a batch of account, then verify transaction success or not, then repeat
   */
  public CryptoCreate(String host, int port, long nodeAccountNumber, String [] args, int index) {
    super(host, port, nodeAccountNumber, args, index);
    log.info("host {} nodeAccountNumber {}", host, nodeAccountNumber);
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    numberOfIterations = Integer.parseInt(args[0]);

    if ((args.length) > 1) {
      TPS = Integer.parseInt(args[1]);
    }

    try {
      initAccountsAndChannels();
      payerAccount = genesisAccount;

      //start an extra thread to check account transaction result
      Thread checkThread = new Thread("New Thread") {
        public void run() {
          long queryCount = 0;
          while (checkRunning) {
            try {
              Pair<Transaction, KeyPair> item = accountTranList.poll(500, TimeUnit.MILLISECONDS);
              if (item != null) {
                TransactionBody body = TransactionBody.parseFrom(item.getLeft().getBodyBytes());
                while (true) { // keep trying ask for account info until confirmed
                  try {
                    TransactionGetReceiptResponse receipt = Common.getReceiptByTransactionId(stub, body.getTransactionID());

                    if (receipt != null){
                      AccountID newID = receipt.getReceipt().getAccountID();
                      if (newID != null && newID.getAccountNum() != 0){

                      }else{
                        log.error("Did not get expected account ID {}", receipt);
                      }
                    }
                    queryCount++;

                    if ((queryCount % 1000) == 0) {
                      log.info("{} accountTranList size {} ", getName(), accountTranList.size());
                    }
                    break;

                  } catch (StatusRuntimeException e) {
                    if (!tryReconnect(e)) {
                      accountTranList.clear();
                      return;
                    }
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                }
              } else {//if empty wait a while
                Thread.sleep(50);
              }
            } catch (InterruptedException | InvalidProtocolBufferException e) {
              log.error("Exception {}", e);
              System.exit(-4);
            }
          }
          log.info("{} finished", getName());
        }
      };
      checkThread.setName("checkThread" + index);
      checkThread.start();


    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  static long lastReduceTime = 0;

  public void reduceTPS() {
    if (lastReduceTime == 0 || (System.currentTimeMillis() - lastReduceTime) > 10000) {
      TPS = TPS - 5;
      log.info("{} Reduce target TPS to {}", getName(), TPS);
      lastReduceTime = System.currentTimeMillis();
    }
  }


  @Override
  void demo() throws Exception {

    int accumulatedTransferCount = 0;
    long startTime = System.currentTimeMillis();
    try {
      while (accumulatedTransferCount < numberOfIterations) {
        KeyPair newKeyPair = new KeyPairGenerator().generateKeyPair();
        Common.addKeyMap(newKeyPair, pubKey2privKeyMap);

        // send create account transaction only, confirm later
        AccountID newAccount = RequestBuilder.getAccountIdBuild(nodeAccountNumber, 0l, 0l);

        try {
          Transaction transaction = Common.tranSubmit(() -> {
            byte[] pubKey = ((EdDSAPublicKey) newKeyPair.getPublic()).getAbyte();
            Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
            Key payerKey = acc2ComplexKeyMap.get(payerAccount);
            Transaction createRequest = Common.createAccountComplex(payerAccount, payerKey, newAccount, key, initialBalance,
                    pubKey2privKeyMap);
            return createRequest;
          }, stub::createAccount);

          accumulatedTransferCount++;

          accountTranList.add(Pair.of(transaction, newKeyPair));
          float currentTPS = Common.tpsControl(startTime, accumulatedTransferCount, TPS);

          if ((accumulatedTransferCount % 100) == 0) {
            log.info("{} currentTPS {}", getName(), currentTPS);
          }
        } catch (StatusRuntimeException e) {
          if (!tryReconnect(e)) return;
        }catch (Exception e){
          log.error("Unexpected error ", e);
          return;
        }
      }
    }finally {
      while(accountTranList.size()>0); //wait query thread to finish
      sleep(1000);         //wait check thread done query
      log.info("{} query queue empty", getName());
      channel.shutdown();
      checkRunning = false;
    }
  }
}
