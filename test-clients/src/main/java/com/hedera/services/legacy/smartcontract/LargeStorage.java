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
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Compare timing for writing different contract storage sizes.
 *
 * @author Peter
 */
public class LargeStorage {
  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private static final Logger log = LogManager.getLogger(LargeStorage.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  public static final String LARGE_STORAGE_BIN = "LargeStorage.bin";
  
  private static final String SCL_STOREARRAY_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_index\",\"type\":\"uint256\"},{\"name\":\"_array\",\"type\":\"uint256[]\"}],\"name\":\"storeArray\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String host;
  private static int port;
  private static long contractDuration;
  private static long gasToOffer;

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    host = properties.getProperty("host");
    port = Integer.parseInt(properties.getProperty("port"));
    node_account_number = Utilities.getDefaultNodeAccount();
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

    int iterations = 126;
    if ((args.length) > 0) {
      iterations = Integer.parseInt(args[0]);
    }

    ServerAppConfigUtility appConfig = ServerAppConfigUtility.getInstance("localhost", 3L);
    gasToOffer = appConfig.getMaxGasLimit() - 1;
    int storageNeededInKb = iterations * 128 * 65 / 1024;
    if (storageNeededInKb > appConfig.getMaxContractStateSize()) {
      log.error("Specified iterations of " + iterations + " will require "
              + storageNeededInKb + "Kb, which is greater than the limit of "
              + appConfig.getMaxContractStateSize());
      System.exit(1);
    }

    LargeStorage scSs = new LargeStorage();
    scSs.demo(iterations);

  }

  private void loadGenesisAndNodeAcccounts() throws Exception {
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

  private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer,
        accountKeyPairs.get(payer), nodeAccount, transferAmt);
    return transferTx;
  }

  private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
            accountKeyPairs.get(payerAccount));
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    System.out.println(
        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    channel.shutdown();
    return newlyCreateAccountId;
  }

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Response transactionReceipts = stub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
        .getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
      Thread.sleep(1000);
      transactionReceipts = stub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }
    channel.shutdown();
    return transactionReceipts.getTransactionGetReceipt();
  }

  private ContractID createContract(AccountID payerAccount, FileID contractFile) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(contractDuration).build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), 100l, timestamp,
            transactionDuration, true, "", 3_000_000, contractFile, ByteString.EMPTY, 0,
            contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

    TransactionResponse response = stub.createContract(createContractRequest);
    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
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

    channel.shutdown();

    return createdContract;
  }

  /*
  Methods to run the storeArray method
   */
  private void setStoreArray(AccountID payerAccount, ContractID contractId, int index, int[]data)
      throws Exception {
    byte[] dataToGet = encodeStoreArray(index, data);
    callContract(payerAccount, contractId, dataToGet, ResponseCodeEnum.SUCCESS);
  }

  public static byte[] encodeStoreArray(int index, int[] data) {
    CallTransaction.Function function = getStoreArrayFunction();
    byte[] encodedFunc = function.encode(index, data);
    return encodedFunc;
  }

  public static CallTransaction.Function getStoreArrayFunction() {
    String funcJson = SCL_STOREARRAY_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }


  private TransactionRecord callContract(AccountID payerAccount, ContractID contractToCall,
      byte[] data, ResponseCodeEnum expectedStatus)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), node_account_number, 0l, 0l, 100l, timestamp,
            transactionDuration, gasToOffer, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    System.out.println(
        " callContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(1000);
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID());
    Assert.assertEquals(expectedStatus, contractCallReceipt.getReceipt().getStatus());

    TransactionRecord txRecord = getTransactionRecord(payerAccount,
        callContractBody.getTransactionID());
    Assert.assertTrue(txRecord.hasContractCallResult());

    log.info("Gas used this contract call is " + txRecord.getContractCallResult().getGasUsed());

    String errMsg = txRecord.getContractCallResult().getErrorMessage();
    if (!StringUtils.isEmpty(errMsg)) {
      log.info("@@@ Contract Call resulted in error: " + errMsg);
      }

    channel.shutdown();
    return txRecord;
  }

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    long fee = FeeClient.getCostForGettingTxRecord();
    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.COST_ANSWER);
    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.ANSWER_ONLY);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    System.out.println("tx record = " + txRecord);
    channel.shutdown();
    return txRecord;
  }


  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
    return recordResp;
  }

  private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    ByteString callData = ByteString.EMPTY;
    int callDataSize = 0;
    if (data != null) {
      callData = ByteString.copyFrom(data);
      callDataSize = callData.size();
    }
    long fee = FeeClient.getCostContractCallLocalFee(callDataSize);
    Response callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.COST_ANSWER);
    fee = callResp.getContractCallLocal().getHeader().getCost();
    callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.ANSWER_ONLY);
    System.out.println("callContractLocal response = " + callResp);
    Assert.assertEquals(ResponseCodeEnum.OK,
        callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    channel.shutdown();
    return functionResults.toByteArray();
  }

  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
      ResponseType resposeType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            resposeType);

    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
    return callResp;
  }

    public void demo(int iterations) throws Exception {
     loadGenesisAndNodeAcccounts();

      ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
          .usePlaintext(true)
          .build();
      TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
          nodeAccount);
      channel.shutdown();

      KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
      AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount,
              TestHelper.getCryptoMaxFee() * 20L);
      Assert.assertNotNull(crAccount);
      Assert.assertNotEquals(0, crAccount.getAccountNum());
      log.info("Account created successfully: " + crAccount);

      // Upload contract file
      FileID newContractFileId = LargeFileUploadIT
          .uploadFile(crAccount, LARGE_STORAGE_BIN, crAccountKeyPair);
      Assert.assertNotNull(newContractFileId);
      Assert.assertNotEquals(0, newContractFileId.getFileNum());
      log.info("Contract file uploaded successfully");

      // Create contract
      ContractID newContractId = createContract(crAccount, newContractFileId);
      Assert.assertNotNull(newContractId);
      Assert.assertNotEquals(0, newContractId.getContractNum());
      log.info("Contract created successfully: " + newContractId);

      int[] someIntegers =  {
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
          21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
          41, 42, 43, 44, 45, 46, 47, 48, 49, 50,
          51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
          61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
          71, 72, 73, 74, 75, 76, 77, 78, 79, 80,
          81, 82, 83, 84, 85, 86, 87, 88, 89, 90,
          91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
          101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
          111, 112, 113, 114, 115, 116, 117, 118, 119, 120,
          121, 122, 123, 124, 125, 126, 127, 128
      };

      long before, after;
      // example: 640 iterations * 128 slots/iteration * 64 bytes/slot  =  5 MB
      for (int i = 0; i < iterations; i++) {
        Thread.sleep(100);
        before = System.currentTimeMillis();
        setStoreArray(crAccount, newContractId, i, someIntegers);
        after = System.currentTimeMillis();
        log.info(i + ": 128 more slots stored in " + (after - before) + " ms");
      }
    }
}
