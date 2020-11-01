package com.hedera.services.legacy.throttling;

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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;


/**
 * TPS testing for contract calls, protected against BUSY and go-away
 *
 * @author Peter
 */
public class ContractCallThrottling {

  private static final Logger log = LogManager.getLogger(ContractCallThrottling.class);
  public static int MAX_TRIES = 3000; //maximum tries for receipts
  public static int MAX_TRANSFERS = 1000000;
  public static int MAX_CALLS = 100000;
  public static int MAX_UPDATE_ACCOUNTS = 1000;
  protected static int MAX_BUSY_RETRIES = 100;
  protected static int BUSY_RETRY_MS = 200;


  public static String fileName = TestHelper.getStartUpFile();
  private static String binFileName = "simpleStorage.bin";
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

  private static CryptoServiceGrpc.CryptoServiceBlockingStub cStub;
  private static FileServiceGrpc.FileServiceBlockingStub fStub;
  private static SmartContractServiceGrpc.SmartContractServiceBlockingStub scStub;

  private static PrivateKey genesisPrivateKey;
  private static KeyPair genesisKeyPair;
  private static AccountID payerAccount;
  private static AccountID nodeAccount2;
  private static ManagedChannel channel;

  public ContractCallThrottling(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    createStubs();
  }

  private void createStubs() {
    cStub = CryptoServiceGrpc.newBlockingStub(channel);
    fStub = FileServiceGrpc.newBlockingStub(channel);
    scStub = SmartContractServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    InetAddress address = InetAddress.getByName("34.226.77.143");
    log.info(address.isReachable(1000));
    String host = properties.getProperty("host");
    if (args.length > 0) {
      host = args[0];
    }
    int port = Integer.parseInt(properties.getProperty("port"));
    long nodeAccountNum = 3;
    if (args.length > 1) {
      nodeAccountNum = Long.parseLong(args[1]);
    }
    nodeAccount2 = RequestBuilder.getAccountIdBuild(nodeAccountNum, 0l, 0l);
    ContractCallThrottling contractCall = new ContractCallThrottling(port, host);
    contractCall.demo();

  }

  Transaction getFileCreateTransaction(ByteString fileData) {
    Duration transactionDuration = RequestBuilder.getDuration(30);
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp,
        CustomPropertiesSingleton.getInstance().getContractDuration());

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(genesisPrivateKey);

    List<Key> waclPubKeyList = new ArrayList<>();
    List<PrivateKey> waclPrivKeyList = new ArrayList<>();
    TestHelper.genWacl(1, waclPubKeyList, waclPrivKeyList);

    Transaction FileCreateRequest = RequestBuilder
        .getFileCreateBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount2.getAccountNum(), nodeAccount2.getRealmNum(), nodeAccount2.getShardNum(),
                TestHelper.getContractMaxFee(), timestamp, transactionDuration, true, "FileCreate",
            fileData, fileExp, waclPubKeyList);
    Transaction filesignedByPayer = TransactionSigner.signTransaction(FileCreateRequest, keyList);
    // append wacl sigs
    Transaction filesigned = TransactionSigner.signTransaction(filesignedByPayer, waclPrivKeyList, true);
    return filesigned;
  }

  private FileID createFile(String binFileName)
      throws IOException, URISyntaxException, InterruptedException {
    Transaction transaction;
    byte[] fileBytes = CommonUtils.readBinaryFileAsResource(binFileName);
    ByteString fileData = ByteString.copyFrom(fileBytes);
    transaction = getFileCreateTransaction(fileData);
    TransactionResponse result = retryLoopTransaction(transaction, "createFile");
    Assert.assertEquals(ResponseCodeEnum.OK, result.getNodeTransactionPrecheckCode());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    Response txReceipt = getTxReceipt(body.getTransactionID());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, txReceipt.getTransactionGetReceipt().getReceipt().getStatus());
    return txReceipt.getTransactionGetReceipt().getReceipt().getFileID();
  }

 private ContractID createContract(FileID contractFile) throws Exception {
    Duration contractAutoRenew = Duration.newBuilder()
        .setSeconds(CustomPropertiesSingleton.getInstance().getContractDuration()).build();
    Duration txDuration = Duration.newBuilder().setSeconds(30).build();

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(genesisPrivateKey);
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();

    Transaction transaction = RequestBuilder
        .getCreateContractRequest(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount2.getAccountNum(), nodeAccount2.getRealmNum(), nodeAccount2.getShardNum(),
                TestHelper.getContractMaxFee(), timestamp,
            txDuration, true, "", 250000L, contractFile, ByteString.EMPTY, 0L,
            contractAutoRenew, "Contract Memo", null);
    transaction = TransactionSigner.signTransaction(transaction, keyList);
    TransactionResponse result = retryLoopTransaction(transaction,  "createContract");
    Assert.assertEquals(ResponseCodeEnum.OK, result.getNodeTransactionPrecheckCode());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    Response txReceipt = getTxReceipt(body.getTransactionID());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, txReceipt.getTransactionGetReceipt().getReceipt().getStatus());
    return txReceipt.getTransactionGetReceipt().getReceipt().getContractID();
  }

  private TransactionResponse setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet)
      throws Exception {
    byte[] dataToSet = encodeSet(valuetoSet);
    //set value to simple storage smart contract
    TransactionResponse response = callContract(payerAccount, contractId, dataToSet);
    return response;
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


  public void demo() throws Exception {

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);
    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    genesisPrivateKey = genKeyPairObj.getPrivateKey();
    genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    payerAccount = genesisAccount.get(0).getAccountId();
    log.info(payerAccount);
     TestHelper.initializeFeeClient(channel, payerAccount, genesisKeyPair, nodeAccount2);

    // create Account
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper.createAccountWithFee(payerAccount, nodeAccount2, firstPair,
        10000l, Collections.singletonList(genesisPrivateKey));
    AccountID newAccountID = createAccount(transaction);
    log.info("Account ID is ::" + newAccountID.getAccountNum());

    // create file
    FileID fileID = createFile(binFileName);
    log.info("File ID is ::" + fileID.getFileNum());

    // create contract
    ContractID contractID = createContract(fileID);
    log.info("Contract ID is ::" + contractID.getContractNum());



    long start = System.currentTimeMillis();
    double transactions = 0;
    for (int i =  0; i <= MAX_CALLS; i++) {
      TransactionResponse callResponse = setValueToContract(payerAccount, contractID, 23);
      log.info(callResponse.getNodeTransactionPrecheckCode() + ":: is the precheck status of the call");
      if (callResponse.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
        double elapsed = System.currentTimeMillis() - start;
        transactions += 1.0;
        double tps = transactions * 1000.0 / elapsed;
        log.info(transactions + " transactions at about " + tps + " TPS.");
      }
    }

    channel.shutdown();

  }

  public AccountID createAccount(Transaction transaction) throws InterruptedException {
    AccountID accountID = AccountID.newBuilder().setAccountNum(100000000).build();
    try {
      TransactionResponse response = cStub.createAccount(transaction);
      int count = 0;
      while ((response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)) {
        log.info("in create account busy loop");
        log.info(response.getNodeTransactionPrecheckCode());
        Thread.sleep(10);
        response = cStub.createAccount(transaction);
        if (count > MAX_TRIES) {
          break;
        }
        count++;
      }
      log.info(response.getNodeTransactionPrecheckCode() + ":: is the pre check code");
      TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
      accountID =
          getTxReceipt(body.getTransactionID()).getTransactionGetReceipt()
              .getReceipt().getAccountID();
      return accountID;

    } catch (Exception e) {
      e.printStackTrace();
      log.info("There was an error");
    }
    return accountID;

  }

  public Response getTxReceipt(TransactionID transactionID) throws InterruptedException {
    Query query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
        .build();
    Response transactionReceipts = Response.newBuilder()
        .setTransactionGetReceipt(TransactionGetReceiptResponse
            .newBuilder().setReceipt(TransactionReceipt.newBuilder().setStatusValue(0).build())
            .build()).build();
    try {
      transactionReceipts = cStub.getTransactionReceipts(query);
      int count = 0;
      while ((transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
          != ResponseCodeEnum.SUCCESS)
          || (transactionReceipts.getTransactionGetReceipt().getHeader()
          .getNodeTransactionPrecheckCode()
          == ResponseCodeEnum.BUSY)) {
        Thread.sleep(10);
        log.info("in get receipt busy loop");
        transactionReceipts = cStub.getTransactionReceipts(query);
        if (count > MAX_TRIES) {
          break;
        }
        count++;
      }
      log.info(transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
          + ":: is the status");
      return transactionReceipts;

    } catch (Exception e) {
      e.printStackTrace();
      log.info("There was an error");
    }
    return transactionReceipts;
  }

  private TransactionResponse callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount2.getAccountNum(), 0l, 0l, 100l, timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0,
            genesisKeyPair);

    TransactionResponse callResponse = scStub.contractCallMethod(callContractRequest);
    log.info(callResponse.getNodeTransactionPrecheckCode());
    int count = 0;
    while ((callResponse.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)
        || (callResponse.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK)
    ) {
      Thread.sleep(1);
      callResponse = scStub.contractCallMethod(callContractRequest);
      if (count > MAX_TRIES) {
        break;
      }
      count++;
    }
    return callResponse;
  }

  public Response doTransfer(Transaction transaction) throws Exception {
    TransactionResponse transferResponse = cStub.cryptoTransfer(transaction);
    log.info(transferResponse.getNodeTransactionPrecheckCode());
    int count = 0;
    while ((transferResponse.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)
        || (transferResponse.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK)
    ) {
      Thread.sleep(1);
      transferResponse = cStub.cryptoTransfer(transaction);
      if (count > MAX_TRIES) {
        break;
      }
      count++;
    }
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    Response transferReceipt = getTxReceipt(body.getTransactionID());
    return transferReceipt;
  }

  public Response getTransactionRecord(Transaction transaction)
      throws Exception {
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    Query query = TestHelper.getTxRecordByTxId(body.getTransactionID(),
        payerAccount, genesisKeyPair, nodeAccount2, TestHelper.getCryptoMaxFee(),
        ResponseType.ANSWER_ONLY);
    Response transactionRecord = cStub.getTxRecordByTxID(query);
    int count = 0;
    while ((transactionRecord.getTransactionGetReceipt().getReceipt().getStatus()
        != ResponseCodeEnum.SUCCESS)
        || (
        transactionRecord.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode()
            == ResponseCodeEnum.BUSY)) {
      Thread.sleep(10);
      transactionRecord = cStub.getTxRecordByTxID(query);
      if (count > MAX_TRIES) {
        break;
      }
      count++;
    }
    return transactionRecord;
  }

  public Response updateAccount(Transaction transaction) throws Exception {
    TransactionResponse updateAccountResponse = cStub.updateAccount(transaction);
    log.info(updateAccountResponse.getNodeTransactionPrecheckCode());
    int count = 0;
    while ((updateAccountResponse.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)
        || (updateAccountResponse.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK)
    ) {
      Thread.sleep(10);
      updateAccountResponse = cStub.updateAccount(transaction);
      if (count > MAX_TRIES) {
        break;
      }
      count++;
    }
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    Response transferReceipt = getTxReceipt(body.getTransactionID());
    return transferReceipt;
  }

  private TransactionResponse retryLoopTransaction(Transaction transaction, String apiName)
      throws InterruptedException {
    TransactionResponse response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

      if (i > 0) {
        log.info("retrying api call " + apiName);
      }

      try {
        switch (apiName) {
          case "createContract":
            response = scStub.createContract(transaction);
            break;
          case "createFile":
            response = fStub.createFile(transaction);
            break;
          case "contractCallMethod":
            response = scStub.contractCallMethod(transaction);
            break;
          default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          try {
            createStubs();
          } catch (Exception e) {
            log.error("Reconnect channel failed..");
            break;
          }
        }
        Thread.sleep(BUSY_RETRY_MS);
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Thread.sleep(BUSY_RETRY_MS);
    }
    return response;
  }

}
