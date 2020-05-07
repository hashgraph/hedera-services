package com.hedera.services.legacy.regression;

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
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test to create same transaction and submit to multiple nodes, and validate only one node process the transaction
 *
 * @author Tirupathi Mandala Created on 2019-06-19
 */
public class SameTxMultipleNodesTests extends BaseFeeTests {

  private static final Logger log = LogManager.getLogger(SameTxMultipleNodesTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  protected Random random = new Random();
  public static long CONTRACT_CREATE_SUCCESS_GAS = 2500000L;
  private static List<String> testResults = new ArrayList<>();
  private static int nummberOfNodesToTest = 3;
  public SameTxMultipleNodesTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }


  public static void main(String[] args) throws Throwable {
    SameTxMultipleNodesTests tester = new SameTxMultipleNodesTests(testConfigFilePath);
    tester.setup(args);
    nummberOfNodesToTest = Integer.parseInt(appProperties.getProperty("nummberOfNodesToTest","3"));
    accountKeys.put(account_1, getAccountPrivateKeys(account_1));
    accountKeys.put(queryPayerId, getAccountPrivateKeys(queryPayerId));
    tester.smartContractCreateFeeTest_90Days();
    //   channelList.stream().forEach(c->c.shutdown()); //shutdown channels created
    for(ManagedChannel ch:channelList) {
      ch.shutdown();
    }
    channel.shutdown();
  }


  public void smartContractCreateFeeTest_90Days() throws Throwable {

    AccountID crAccount = getMultiSigAccount(1, 1,
        CustomPropertiesSingleton.getInstance().getAccountDuration());
    accountKeys.put(crAccount,getAccountPrivateKeys(crAccount));
    long payerAccountBalance_before = getAccountBalance(crAccount, queryPayerId, nodeID);


    Transaction sampleStorageTransaction[] = new Transaction[nummberOfNodesToTest];
    TransactionResponse[] response = new TransactionResponse[nummberOfNodesToTest];
    Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()));
    Collection<ParallelSubmit> tasks = new ArrayList<>();
    for(int i=0;i<nummberOfNodesToTest;i++) {
      log.info("Creating Contract Request: "+i);
      sampleStorageTransaction[i] = createContractTransaction(crAccount, nodeAccounts[i],timestamp,"ContractCreate-"+i);
      ParallelSubmit parallelSubmit = new ParallelSubmit( sampleStorageTransaction[i],contractStubs[i]);
      tasks.add(parallelSubmit);
    }
    ExecutorService executorService = Executors.newFixedThreadPool(3);
    List<Future<TransactionResponse>> taskFutureList = executorService.invokeAll(tasks);
    int counter=0;
    for(Future<TransactionResponse> resp:taskFutureList) {
      response[counter++] =resp.get();
    }
    int successCount = 0;
    for(int i=0;i<nummberOfNodesToTest;i++) {
      ResponseCodeEnum statusCode = printTxDetails(sampleStorageTransaction[i], response[i], i);
      log.info(
              " createContractWithOptions Pre Check Response :-"+i+"-: " + response[i]
                      .getNodeTransactionPrecheckCode().name());
      log.info("Status :-"+i+"-: " + statusCode);
      if("OK".equalsIgnoreCase(response[i].getNodeTransactionPrecheckCode().name()) && statusCode == ResponseCodeEnum.SUCCESS ) {
        successCount++;
      }
    }
    executorService.shutdown();
    long transactionFee = getTransactionFee(sampleStorageTransaction[0]);
    Thread.sleep(1000);
    long payerAccountBalance_after = getAccountBalance(crAccount, queryPayerId, nodeID);
    log.info("Create SmartContract transactionFee=" + transactionFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    long balanceDiff = (payerAccountBalance_before - payerAccountBalance_after);
    log.info("payerAccountBalance Diff=" + balanceDiff);
    Assert.assertTrue(balanceDiff < (transactionFee * nummberOfNodesToTest));
  }

  private ResponseCodeEnum printTxDetails(Transaction transaction, TransactionResponse response, int sequence) throws Exception {
    TransactionBody createContractBody = TransactionBody
            .parseFrom(transaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
            createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      ContractID createdContract = contractCreateReceipt.getReceipt().getContractID();
      log.info("createdContract :-"+sequence+"-: " + createdContract);
    }
    return contractCreateReceipt.getReceipt().getStatus();
  }

  private Transaction createContractTransaction(AccountID crAccount, AccountID nodeID,Timestamp timestamp, String memo) throws Exception {
    KeyPair fileAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID fileAccount = createAccount(fileAccountKeyPair, queryPayerId, 100000000000000L);
    String fileName = "LargeStorage.bin";
    Assert.assertNotNull(fileAccount);

    FileID storageFileId = LargeFileUploadIT
            .uploadFile(fileAccount, fileName, fileAccountKeyPair);
    Assert.assertNotNull("Storage file id is null.", storageFileId);
    log.info("Smart Contract file uploaded successfully");
    CONTRACT_CREATE_SUCCESS_GAS = 250;
    long autoRenewPeriod = 30 * 24 * 60 * 60; //1 Month (30 Days)
    Transaction createContractTransaction = createContractWithTimestamp(crAccount, storageFileId, nodeID, autoRenewPeriod, 1000000L,
            getAccountPrivateKeys(crAccount), timestamp, memo);
    return createContractTransaction;
  }

  private Transaction createContractWithTimestamp(AccountID payerAccount, FileID contractFile,
                                                  AccountID useNodeAccount, long autoRenewInSeconds,
                                                  long transactionFee, List<PrivateKey> adminPrivateKeys, Timestamp timestamp, String memo)
          throws Exception {

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(autoRenewInSeconds).build();

    adminPrivateKeys.add(queryPayerPrivateKey);

    Duration txDuration = RequestBuilder.getDuration(100);
    long gas = 250000L;
    Transaction transaction;
    transaction = RequestBuilder
            .getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
                    useNodeAccount.getAccountNum(), useNodeAccount.getRealmNum(), useNodeAccount.getShardNum(), transactionFee, timestamp,
                    txDuration, true, "txMemo-"+memo, gas, contractFile, null,0,
                    contractAutoRenew, SignatureList.newBuilder().addSigs(Signature.newBuilder()
                            .setEd25519(ByteString.copyFrom("testsignature".getBytes())))
                            .build(),
                    memo, null);

    transaction = TransactionSigner.signTransaction(transaction, adminPrivateKeys);
    transactionFee = FeeClient.getContractCreateFee(transaction, adminPrivateKeys.size());
    transaction = RequestBuilder
            .getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
                    useNodeAccount.getAccountNum(), useNodeAccount.getRealmNum(), useNodeAccount.getShardNum(), transactionFee, timestamp,
                    txDuration, true, "txMemo-"+memo, gas, contractFile, null, 0,
                    contractAutoRenew, SignatureList.newBuilder().addSigs(Signature.newBuilder()
                            .setEd25519(ByteString.copyFrom("testsignature".getBytes())))
                            .build(),
                    memo, null);

    transaction = TransactionSigner.signTransaction(transaction, adminPrivateKeys);
    return transaction;
  }

  public static Transaction createContractRequest(Long payerAccountNum, Long payerRealmNum,
                                                  Long payerShardNum,
                                                  Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
                                                  long transactionFee, Timestamp timestamp, Duration txDuration,
                                                  boolean generateRecord, String txMemo, long gas, FileID fileId,
                                                  ByteString constructorParameters, long initialBalance,
                                                  Duration autoRenewalPeriod, List<PrivateKey> keys, String contractMemo,
                                                  Key adminKey) throws Exception {
    Transaction transaction;

    transaction = RequestBuilder
            .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
                    nodeRealmNum, nodeShardNum, transactionFee, timestamp,
                    txDuration, generateRecord, txMemo, gas, fileId, constructorParameters,
                    initialBalance,
                    autoRenewalPeriod, SignatureList.newBuilder()
                            .addSigs(Signature.newBuilder()
                                    .setEd25519(ByteString.copyFrom("testsignature".getBytes())))
                            .build(), contractMemo, adminKey);

    transaction = TransactionSigner.signTransaction(transaction, keys);
    transactionFee = FeeClient.getContractCreateFee(transaction, keys.size());
    transaction = RequestBuilder
            .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
                    nodeRealmNum, nodeShardNum, transactionFee, timestamp,
                    txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
                    autoRenewalPeriod, SignatureList.newBuilder()
                            .addSigs(Signature.newBuilder()
                                    .setEd25519(ByteString.copyFrom("testsignature".getBytes())))
                            .build(), contractMemo, adminKey);

    transaction = TransactionSigner.signTransaction(transaction, keys);
    return transaction;
  }

}
