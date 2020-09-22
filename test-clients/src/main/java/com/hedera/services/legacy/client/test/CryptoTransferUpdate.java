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

import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.client.util.Ed25519KeyStore;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class CryptoTransferUpdate extends ClientBaseThread {

  private static final Logger log = LogManager.getLogger(CryptoTransferUpdate.class);

  public static String fileName = TestHelper.getStartUpFile();

  private static int fetchReceipt = 0;

  private long transferTimes = 1000;
  public long initialBalance = 500000000000L;
  private int TPS = 200;
  private long transferPerAccountPair = 1000;

  private final static long TRANSACTION_FEE = TestHelper.getCryptoMaxFee();

  private AccountID payerAccount;
  private boolean testingUpdate = false;
  private LinkedBlockingQueue<TransactionID> txIdQueue = new LinkedBlockingQueue<>();
  private boolean checkRunning = true;
  private String pemFileName;
  private long pemAccountNumber = 0;
  private long singleTranFee; //fee amount for every single request

  private Thread checkThread;
  /**
   * Create a pair of two accounts, do transferTimes then create new account pair and do transfer
   * again Keep total throughput at target TPS
   */
  public CryptoTransferUpdate(String host, int port, long nodeAccountNumber, String[] args, int index) {
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    if ((args.length) > 0) {
      transferTimes = Long.parseLong(args[0]);
    }

    if ((args.length) > 1) {
      TPS = Integer.parseInt(args[1]);
    }

    if ((args.length) > 2) {
      initialBalance = Long.parseLong(args[2]);
      log.info("initialBalance {}", initialBalance);
    }

    if ((args.length) > 3) {
      testingUpdate = Boolean.parseBoolean(args[3]);
      if(testingUpdate){
        log.info("Testing Crypto Update");
      }else{
        log.info("Testing Crypto Transfer");
      }
    }

    if ((args.length) > 4) {
      pemFileName = args[4];
      pemAccountNumber = Integer.parseInt(args[5]);
    }

    try {
      initAccountsAndChannels();
      payerAccount = genesisAccount;

      //start an extra thread to check account transaction result
      checkThread = new Thread("New Thread") {
        public void run() {
          TransactionRecord record;
          while (checkRunning) {
            try {
              TransactionID item = txIdQueue.poll(500, TimeUnit.MILLISECONDS);
              if (item != null) {
                //either get receipt or RECEIPT_NOT_FOUND (receipt expire already)
                Common.getReceiptByTransactionId(stub, item);
                if (isBackupTxIDRecord) {
                  record = getTransactionRecord(genesisAccount, item, false);
                  confirmedTxRecord.add(record);
                }
              }else{
                sleep(50);
              }
            } catch (io.grpc.StatusRuntimeException e) {
              if (!tryReconnect(e)) {
                break;
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          txIdQueue.clear();
          log.info("{} Check thread end", getName());
        }
      };
      checkThread.setName("checkThread" + index);


    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {

    try {

      if(!isCheckTransferList) {
        //no need to run check thread if we are checking balance using txRecord and transferList
        checkThread.start();
      }else{
        log.info("isCheckTransferList is enabled, disable checkThreadRunning");
      }

      int transferCount = 0;
      int accumulatedTransferCount = 0;
      long startTime = System.currentTimeMillis();

      Map<AccountID, Long> preBalance = null;

      log.info("Doing " + transferTimes + " Operations");

      KeyPair fromAccountKeyPair = null;
      KeyPair toAccountKeyPair = null;
      AccountID fromAccount = null;
      AccountID toAccount = null;
      TransactionID txID = null;

      if(pemFileName!=null){
        final Ed25519KeyStore restoreKeyStore = new Ed25519KeyStore("password".toCharArray(),
                pemFileName);
        fromAccountKeyPair = restoreKeyStore.get(0);
        fromAccount = RequestBuilder.getAccountIdBuild(pemAccountNumber, 0L, 0L);
      }

      for (int i = 0; i < transferTimes; i++) {
        if (transferCount == 0) {

          if(pemFileName == null){
            fromAccountKeyPair = new KeyPairGenerator().generateKeyPair();

            if (isCheckTransferList){
              preBalance = Common.createBalanceMap(stub,
                      new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount, genesisKeyPair,
                      nodeAccount);
            }

            txID = callCreateAccount(payerAccount, fromAccountKeyPair, initialBalance);
            if (isBackupTxIDRecord) {
				submittedTxID.add(txID); // used by parent thread for checking event files & record files
			}
            txIdQueue.add(txID); //local queue for retrieving receipt or record
            fromAccount = Common.getAccountIDfromReceipt(stub, txID);

            accumulatedTransferCount++;

            if (isCheckTransferList){
              preBalance.put(fromAccount, 0L); //balance before account being created
              verifyBalance(txID, preBalance, true);
            }
          }

          Common.addKeyMap(fromAccountKeyPair, pubKey2privKeyMap);

          if (!testingUpdate) {
            toAccountKeyPair = new KeyPairGenerator().generateKeyPair();

            if (isCheckTransferList){
              preBalance = Common.createBalanceMap(stub,
                      new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount, genesisKeyPair,
                      nodeAccount);
            }

            txID = callCreateAccount(payerAccount, toAccountKeyPair, initialBalance);
            if (isBackupTxIDRecord) {
				submittedTxID.add(txID);
			}
            txIdQueue.add(txID);
            toAccount = Common.getAccountIDfromReceipt(stub, txID);

            accumulatedTransferCount++;

            if (isCheckTransferList){
              preBalance.put(toAccount, 0L); //balance before account being created
              verifyBalance(txID, preBalance, true);
            }

            Common.addKeyMap(toAccountKeyPair, pubKey2privKeyMap);
          }

          if (fromAccount == null) {
            log.error("Account creation failed");
            System.exit(-1);
          }
          log.info("Creating new accounts ");
        }

        final AccountID finalFromAccount = fromAccount;
        final AccountID finalToAccount = toAccount;
        final KeyPair finalKeyPair = fromAccountKeyPair;

        try {

          if (isCheckTransferList){
            if (!testingUpdate) {
              preBalance = Common.createBalanceMap(stub,
                      new ArrayList<>(List.of(fromAccount, toAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
                      genesisAccount, genesisKeyPair,
                      nodeAccount);
            }else{
              preBalance = Common.createBalanceMap(stub,
                      new ArrayList<>(List.of(fromAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
                      genesisAccount, genesisKeyPair,
                      nodeAccount);
            }
          }


          Transaction submittedTran = Common.tranSubmit(() -> {
            Transaction request;
            try {
              if (testingUpdate) {
                Key existingKey = Common.keyPairToKey(finalKeyPair);
                request = MyUpdateTran(finalFromAccount, finalKeyPair, TRANSACTION_FEE);
                Timestamp expirationTimeStamp = RequestBuilder
                        .getTimestamp(Instant.now().plusSeconds(36000));
                singleTranFee = FeeClient.getCreateUpdateFee(request, 2, expirationTimeStamp, existingKey);
                request = MyUpdateTran(finalFromAccount, finalKeyPair, singleTranFee);
              } else {
                request = MyCreateTransfer(finalFromAccount, finalKeyPair.getPrivate(),
                    finalToAccount,
                    finalFromAccount,
                    finalKeyPair.getPrivate(), nodeAccount, 1L, TRANSACTION_FEE);
                singleTranFee = FeeClient.getCreateTransferFee(request, 2);
                request = MyCreateTransfer(finalFromAccount, finalKeyPair.getPrivate(),
                        finalToAccount,
                        finalFromAccount,
                        finalKeyPair.getPrivate(), nodeAccount, 1L, singleTranFee);

              }
            } catch (Exception e) {
              e.printStackTrace();
              return null;
            }
            return request;
          }, testingUpdate ?  stub::updateAccount : stub::cryptoTransfer);

          if(i == 0){
            transferPerAccountPair = (initialBalance/(singleTranFee+1)) - 1;
            log.info("singleTranFee = {}", singleTranFee);
            log.info("Each pair would do operation {} time ", transferPerAccountPair);
          }

          txID = TransactionBody.parseFrom(submittedTran.getBodyBytes())
                  .getTransactionID();
          if (isBackupTxIDRecord) {
			  this.submittedTxID.add(txID);
		  }
          txIdQueue.add(txID);

          transferCount++;

          if (isCheckTransferList){
            Common.getReceiptByTransactionId(stub, txID); //make sure transaction record will be ready
            verifyBalance(txID, preBalance, true);
          }

          if (transferCount > transferPerAccountPair) {
            transferCount = 0;
          }

          // TPS control
          accumulatedTransferCount++;

          float currentTPS = Common.tpsControl(startTime, accumulatedTransferCount, TPS);

          if ((accumulatedTransferCount % 2000) == 0) {
            log.info("{} currentTPS {}", getName(), currentTPS);
          }
        } catch (StatusRuntimeException e) {
          if (!tryReconnect(e)) {
            return;
          }
        } catch (Exception e) {
          log.error("Unexpected error ", e);
          return;
        }
      }
      log.info("Finish all operations");


      //no longer need account
      if (isCheckTransferList && !testingUpdate){
        preBalance = Common.createBalanceMap(stub,
                new ArrayList<>(List.of(fromAccount, toAccount, nodeAccount, genesisAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
                genesisAccount, genesisKeyPair,
                nodeAccount);

        // test balance after get account info
        Pair<List<Transaction>, CryptoGetInfoResponse.AccountInfo> result = getAccountInfo(
                stub, fromAccount, genesisAccount);
        List<TransactionID> txIDList = new ArrayList<>();
        for (Transaction item : result.getLeft()) {
          txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
          txIDList.add(txID);
        }

        // test balance after get account records
        Pair<List<Transaction>, List<TransactionRecord>> recordResult = getAccountRecords(fromAccount,
                genesisAccount);
        for (Transaction item : recordResult.getLeft()) {
          txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
          txIDList.add(txID);
        }

        //TBD test balance after get account stakes
//        Pair<List<Transaction>, AllProxyStakers> stakeResult = getAccountStakes(stub,
//                fromAccount,
//                genesisAccount);
//        for (Transaction item : stakeResult.getLeft()) {
//          txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
//          txIDList.add(txID);
//        }

        // test balance after delete account
        txID = grpcStub.deleteAccount(genesisAccount, Common.keyPairToPubKey(genesisKeyPair),
                fromAccount, Common.keyPairToPubKey(fromAccountKeyPair),
                toAccount,
                nodeAccount, pubKey2privKeyMap);

        txIDList.add(txID);

        Common.getReceiptByTransactionId(stub, txID);
        TestHelper.getFastTxRecord(txID, stub); // test get fast record
        verifyBalance(txIDList, preBalance, true);
      }
    }finally {
      if(!isCheckTransferList) {
        while (txIdQueue.size() > 0) {
			; //wait query thread to finish
		}
      }
      sleep(1000);         //wait check thread done query
      log.info("{} query queue empty", getName());
      checkRunning = false;
      channel.shutdown();
    }
  }

  private Transaction MyCreateTransfer(AccountID fromAccount, PrivateKey fromKey,
      AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount, long amount,
          long transactionFee) {
    boolean generateRecord = (fetchReceipt == 2);
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, timestamp,
        transactionDuration,
        generateRecord, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    List<PrivateKey> payerPrivKeyList = new ArrayList<>();
    payerPrivKeyList.add(payerAccountKey);
    privKeysList.add(payerPrivKeyList);

    List<PrivateKey> fromPrivKeyList = new ArrayList<>();
    fromPrivKeyList.add(fromKey);
    privKeysList.add(fromPrivKeyList);

    Transaction signedTx;
    List<Key> keys = new ArrayList<>();
    keys.add(Common.PrivateKeyToKey(payerAccountKey));
    keys.add(Common.PrivateKeyToKey(fromKey));
    try {
      signedTx = TransactionSigner.signTransactionComplexWithSigMap(transferTx, keys, pubKey2privKeyMap);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return signedTx;
  }


  public Transaction updateAccount(AccountID accountID, AccountID payerAccount,
          PrivateKey payerAccountKey, AccountID nodeAccount, Duration autoRenew, long transactionFee) {

    Timestamp startTime = RequestBuilder
            .getTimestamp(Instant.now(Clock.systemUTC()));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    long nodeAccountNum = nodeAccount.getAccountNum();
    long payerAccountNum = payerAccount.getAccountNum();
    return RequestBuilder.getAccountUpdateRequest(accountID, payerAccountNum, 0l,
            0l, nodeAccountNum, 0l,
            0l, transactionFee, startTime,
            transactionDuration, true, "Update Account", autoRenew);
  }

  private Transaction MyUpdateTran(AccountID accountID, KeyPair payerKey, long transactionFee) {
    Duration autoRenew = RequestBuilder.getDuration(9002);
    Transaction transaction = updateAccount(accountID, accountID, payerKey.getPrivate(),
            nodeAccount, autoRenew, transactionFee);

    List<PrivateKey> privateKeyList = new ArrayList<>();
    privateKeyList.add(payerKey.getPrivate());
    privateKeyList.add(payerKey.getPrivate());
    Transaction signUpdate;
    List<Key> keys = new ArrayList<>();
    keys.add(Common.keyPairToKey(payerKey));
    keys.add(Common.keyPairToKey(payerKey));
    try {
      signUpdate = TransactionSigner.signTransactionComplexWithSigMap(transaction, keys, pubKey2privKeyMap);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return signUpdate;

  }

}
