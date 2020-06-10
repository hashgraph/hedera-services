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
import com.hedera.services.legacy.client.util.Common;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

/**
 * Successful create, read, update, and delete of smart contracts with and without admin keys
 *
 * @author Peter
 */
public class SmartContractCRUD {

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  //  private final static String CONTRACT_MEMO_STRING_1 = "This is a memo string with non-ascii characters: ȀĊ.";
  private final static String CONTRACT_MEMO_STRING_1 = "This is a memo string with only Ascii characters";
  private final static String CONTRACT_MEMO_STRING_2 = "This is an updated memo string.";
  private final static String CONTRACT_MEMO_STRING_3 = "Yet another memo.";
  private final Logger log = LogManager.getLogger(SmartContractCRUD.class);


  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final long MAX_TX_FEE = TestHelper.getCryptoMaxFee();
  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
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

    int numberOfReps = 1;
//    if ((args.length) > 0) {
//      numberOfReps = Integer.parseInt(args[0]);
//    }
    for (int i = 0; i < numberOfReps; i++) {
      SmartContractCRUD scc = new SmartContractCRUD();
      scc.demo();
    }

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

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId,
      ResponseCodeEnum expectedStatus) throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
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
    Assert.assertEquals(expectedStatus,
        transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());
    return transactionReceipts.getTransactionGetReceipt();

  }

  private ContractID createContractWithKey(AccountID payerAccount, FileID contractFile,
      long durationInSeconds, Key adminKey,
      AccountID adminAccount) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    List<KeyPair> keyPairList = new ArrayList<>();
    keyPairList.add(accountKeyPairs.get(payerAccount));
    if (adminAccount != null && !adminAccount.equals(payerAccount)) {
      keyPairList.add(accountKeyPairs.get(adminAccount));
    }

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
            transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
            contractAutoRenew, keyPairList, CONTRACT_MEMO_STRING_1, adminKey);

    TransactionResponse response = stub.createContract(createContractRequest);
    System.out.println(
        " createContractWithKey Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody createContractBody = TransactionBody
        .parseFrom(createContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
        createContractBody.getTransactionID(), ResponseCodeEnum.SUCCESS);
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

  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), 1l, 0l, 0l, MAX_TX_FEE, timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    System.out.println(
        " callContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(1000);
    TransactionBody callContractBody = TransactionBody
        .parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID(), ResponseCodeEnum.SUCCESS);
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount,
          callContractBody.getTransactionID());
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (StringUtils.isEmpty(errMsg)) {
          if (!callResults.getContractCallResult().isEmpty()) {
            dataToReturn = callResults.getContractCallResult().toByteArray();
          }
        } else {
          log.info("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
    channel.shutdown();

    return dataToReturn;
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


  private CallTransaction.Function getGetValueFunction() {
    String funcJson = SC_GET_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private byte[] encodeGetValue() {
    String retVal = "";
    CallTransaction.Function function = getGetValueFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  private int decodeGetValueResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getGetValueFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }


  private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    AccountID createdAccount = null;
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
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);

    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.COST_ANSWER);

    Response callResp = stub.contractCallLocalMethod(contractCallLocal);

    fee = callResp.getContractCallLocal().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.ANSWER_ONLY);

    callResp = stub.contractCallLocalMethod(contractCallLocal);

    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    System.out.println("callContractLocal response = " + callResp);
    channel.shutdown();
    return functionResults.toByteArray();
  }


  private int getValueFromContract(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeGetValue();
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeGetValueResult(result);
    }
    return retVal;
  }


  private void setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet)
      throws Exception {
    byte[] dataToSet = encodeSet(valuetoSet);
    //set value to simple storage smart contract
    byte[] retData = callContract(payerAccount, contractId, dataToSet);
  }

  public void updateContractWithKey(AccountID payerAccount, ContractID contractToUpdate,
      Duration autoRenewPeriod, Timestamp expirationTime, String contractMemo,
      AccountID adminAccount, ResponseCodeEnum expectedStatus,
      Key newAdminKey, AccountID newAdminAccount) throws Exception {

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    KeyPair payerKeyPair = accountKeyPairs.get(payerAccount);
    keyList.add(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    if (adminAccount != null && !adminAccount.equals(payerAccount)) {
      KeyPair adminKeyPair = accountKeyPairs.get(adminAccount);
      keyList.add(Common.PrivateKeyToKey(adminKeyPair.getPrivate()));
      Common.addKeyMap(adminKeyPair, pubKey2privKeyMap);
    }
    if (newAdminAccount != null && !newAdminAccount.equals(payerAccount)) {
      KeyPair adminKeyPair = accountKeyPairs.get(newAdminAccount);
      keyList.add(Common.PrivateKeyToKey(adminKeyPair.getPrivate()));
      Common.addKeyMap(adminKeyPair, pubKey2privKeyMap);
    }

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    Transaction updateContractRequest = RequestBuilder
        .getContractUpdateRequest(payerAccount, nodeAccount, MAX_TX_FEE, timestamp,
            transactionDuration, true, "", contractToUpdate, autoRenewPeriod, newAdminKey, null,
            expirationTime, SignatureList.newBuilder().addSigs(Signature.newBuilder()
                .setEd25519(ByteString.copyFrom("testsignature".getBytes()))).build(),
            contractMemo);
    updateContractRequest = TransactionSigner
        .signTransactionComplexWithSigMap(updateContractRequest, keyList, pubKey2privKeyMap);

    TransactionResponse response = stub.updateContract(updateContractRequest);
    System.out.println(
        " update contract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(1000);
    TransactionBody updateContractBody = TransactionBody
        .parseFrom(updateContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(
        updateContractBody.getTransactionID(), expectedStatus);
    Assert.assertNotNull(contractUpdateReceipt);
    TransactionRecord trRecord = getTransactionRecord(payerAccount,
        updateContractBody.getTransactionID());
    Assert.assertNotNull(trRecord);
    validateTransactionRecordForErrorCase(trRecord);
    channel.shutdown();

  }

  private ResponseCodeEnum deleteContract(AccountID payerAccount, ContractID contractId,
      AccountID adminAccount, ResponseCodeEnum expectedStatus) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);

    List<KeyPair> keyPairList = new ArrayList<>();
    keyPairList.add(accountKeyPairs.get(payerAccount));
    if (adminAccount != null && !adminAccount.equals(payerAccount)) {
      keyPairList.add(accountKeyPairs.get(adminAccount));
    }

    Transaction deleteContractRequest = TestHelper.getDeleteContractRequestSigMap(
        payerAccount, nodeAccount, MAX_TX_FEE * 2, timestamp, transactionDuration,
        true, "", contractId, null, null, keyPairList);

    TransactionResponse response = stub.deleteContract(deleteContractRequest);
    System.out.println(
        " deleteContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody deleteContractBody = TransactionBody
        .parseFrom(deleteContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
        deleteContractBody.getTransactionID(), expectedStatus);
    TransactionRecord txRecord = getTransactionRecord(payerAccount,
            deleteContractBody.getTransactionID());
    Assert.assertNotNull(txRecord);
    validateTransactionRecordForErrorCase(txRecord);
    channel.shutdown();

    return contractDeleteReceipt.getReceipt().getStatus();
  }

  private String getContractByteCode(AccountID payerAccount,
      ContractID contractId) throws Exception {
    String byteCode = "";
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetBytecode);
    Response respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
        ResponseType.COST_ANSWER);

    fee = respToReturn.getContractGetBytecodeResponse().getHeader().getCost();
    respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
        ResponseType.ANSWER_ONLY);
    ByteString contractByteCode = null;
    contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
    if (contractByteCode != null && !contractByteCode.isEmpty()) {
      byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
    }
    channel.shutdown();

    return byteCode;
  }


  private Response executeContractByteCodeQuery(AccountID payerAccount, ContractID contractId,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
      ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getContractBytecodeQuery = RequestBuilder
        .getContractGetBytecodeQuery(contractId, paymentTx, responseType);
    Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
    return respToReturn;
  }

  private AccountInfo getCryptoGetAccountInfo(
      AccountID accountID) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetInfo);

    Response respToReturn = executeGetAcctInfoQuery(accountID, stub, fee, ResponseType.COST_ANSWER);

    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
    respToReturn = executeGetAcctInfoQuery(accountID, stub, fee, ResponseType.ANSWER_ONLY);
    AccountInfo accInfToReturn = null;
    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
    channel.shutdown();

    return accInfToReturn;
  }


  private Response executeGetAcctInfoQuery(AccountID accountID,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      long fee, ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(accountID, fee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, responseType);

    Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
    return respToReturn;
  }

  private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
      String solidityId) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    long fee = FeeClient.getFeegetBySolidityID();
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getBySolidityIdQuery = RequestBuilder
        .getBySolidityIDQuery(solidityId, paymentTx, ResponseType.ANSWER_ONLY);

    Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
    GetBySolidityIDResponse bySolidityReturn = null;
    bySolidityReturn = respToReturn.getGetBySolidityID();
    channel.shutdown();

    return bySolidityReturn;
  }


  public void demo() throws Exception {
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    // Key pair contract initially created with
    KeyPair initialKeyPair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) initialKeyPair.getPublic()).getAbyte();
    Key initialPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    AccountID initialAccount = createAccount(initialKeyPair, genesisAccount, 1000000000000L);

    // Key pair to update it to before further testing
    KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
    pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
    Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    AccountID adminAccount = createAccount(adminKeyPair, genesisAccount, 1000000000000L);

    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount,
            TestHelper.getCryptoMaxFee() * 10);
    log.info("Account created successfully " + crAccount.getAccountNum());
    String fileName = "simpleStorage.bin";
    if (crAccount != null) {

      FileID simpleStorageFileId = LargeFileUploadIT
          .uploadFile(crAccount, fileName, crAccountKeyPair);
      if (simpleStorageFileId != null) {
        log.info("Smart Contract file uploaded successfully " + simpleStorageFileId.getFileNum());
        ContractID sampleStorageContractId = createContractWithKey(crAccount, simpleStorageFileId,
            contractDuration, initialPubKey, initialAccount);
        Assert.assertNotNull(sampleStorageContractId);
        Assert.assertNotEquals(0L, sampleStorageContractId.getContractNum());
        log.info("Contract created successfully");
        ContractInfo cInfo1 = getContractInfo(crAccount, sampleStorageContractId);
        Assert.assertEquals("Contract memo mismatch.", CONTRACT_MEMO_STRING_1, cInfo1.getMemo());
        log.info("Original expiration in seconds: " + cInfo1.getExpirationTime().getSeconds());

        log.info("Original admin public key: " + initialPubKey.toString());
        log.info("Returned admin public key: " + cInfo1.getAdminKey().toString());
        Assert.assertEquals(initialPubKey.toString(), cInfo1.getAdminKey().toString());

        // Demonstrate that changing the admin key requires signing with both the old AND the new admin keys.
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            null, null, ResponseCodeEnum.INVALID_SIGNATURE, adminPubKey, adminAccount);
        log.info("Admin key update without current admin key signing did not succeed");

        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            null, initialAccount, ResponseCodeEnum.INVALID_SIGNATURE, adminPubKey, null);
        log.info("Admin key update without new admin key signing did not succeed");

        // Update 1: Change the admin key from the initial one to the one we will test with.
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            null, initialAccount, ResponseCodeEnum.SUCCESS, adminPubKey, adminAccount);
        log.info("Admin key updated successfully");

        // Update 2: Update and check both expiration and memo
        Instant c1Expiration = Instant.ofEpochSecond(cInfo1.getExpirationTime().getSeconds(),
            cInfo1.getExpirationTime().getNanos());
        Date c1ExpirationDate = Date.from(c1Expiration);
        Thread.sleep(1000);
        Timestamp c1NewExpirationDate = Timestamp.newBuilder()
            .setSeconds(cInfo1.getExpirationTime().getSeconds() + DAY_SEC * 30).build();
        updateContractWithKey(crAccount, sampleStorageContractId, null, c1NewExpirationDate,
            CONTRACT_MEMO_STRING_2, adminAccount, ResponseCodeEnum.SUCCESS, null, null);

        ContractInfo c1InfoAfterUpdate = getContractInfo(crAccount, sampleStorageContractId);
        Timestamp c1ExpirationDateAfterUpdate = c1InfoAfterUpdate.getExpirationTime();

        log.info(
            "Updated expiration in seconds: " + c1InfoAfterUpdate.getExpirationTime().getSeconds());
        log.info("Updated memo: " + c1InfoAfterUpdate.getMemo());

        Assert.assertEquals("Expiration:", c1NewExpirationDate.getSeconds(),
            c1ExpirationDateAfterUpdate.getSeconds());
        String solidityId = c1InfoAfterUpdate.getContractAccountID();
        GetBySolidityIDResponse solIdResp = getBySolidityID(crAccount, solidityId);
        assert (solIdResp.getContractID().equals(sampleStorageContractId));
        Assert.assertEquals("Contract memo mismatch.", CONTRACT_MEMO_STRING_2,
            c1InfoAfterUpdate.getMemo());
        log.info("Contract updated successfully 2");

        ContractInfo moreC1Info;
        // Update 3: Update only memo
        Thread.sleep(1000);
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            CONTRACT_MEMO_STRING_3,
            adminAccount, ResponseCodeEnum.SUCCESS, null, null);
        moreC1Info = getContractInfo(crAccount, sampleStorageContractId);
        c1ExpirationDateAfterUpdate = moreC1Info.getExpirationTime();

        log.info("Unchanged expiration in seconds: " + moreC1Info.getExpirationTime().getSeconds());
        log.info("Updated memo: " + moreC1Info.getMemo());

        Assert.assertEquals("Expiration:", c1NewExpirationDate.getSeconds(),
            c1ExpirationDateAfterUpdate.getSeconds());
        Assert.assertEquals("Contract memo mismatch.", CONTRACT_MEMO_STRING_3,
            moreC1Info.getMemo());
        log.info("Contract updated successfully 3");

        // Update 4: Update only expiry
        Thread.sleep(1000);
        c1NewExpirationDate = Timestamp.newBuilder()
            .setSeconds(cInfo1.getExpirationTime().getSeconds() + (DAY_SEC * 30) + 17).build();
        updateContractWithKey(crAccount, sampleStorageContractId, null, c1NewExpirationDate, "",
            null, ResponseCodeEnum.SUCCESS, null,
            null); // Only expiration date updated, no admin sig required.
        moreC1Info = getContractInfo(crAccount, sampleStorageContractId);
        c1ExpirationDateAfterUpdate = moreC1Info.getExpirationTime();

        log.info("Updated expiration in seconds: " + moreC1Info.getExpirationTime().getSeconds());
        log.info("Unchanged memo: " + moreC1Info.getMemo());

        Assert.assertEquals("Expiration:", c1NewExpirationDate.getSeconds(),
            c1ExpirationDateAfterUpdate.getSeconds());
        Assert.assertEquals("Contract memo mismatch.", CONTRACT_MEMO_STRING_3,
            moreC1Info.getMemo());
        log.info("Contract updated successfully 4");

        // Attempt to reduce expiry
        Thread.sleep(1000);
        c1NewExpirationDate = Timestamp.newBuilder()
            .setSeconds(cInfo1.getExpirationTime().getSeconds() + (DAY_SEC * 30) - 17).build();
        updateContractWithKey(crAccount, sampleStorageContractId, null, c1NewExpirationDate, "",
            null, ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED, null,
            null); // Only expiration date updated, no admin sig required.
        log.info("Refused to update expiration date to an earlier time");

        // Update that should fail on no signature
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            CONTRACT_MEMO_STRING_2, null, ResponseCodeEnum.INVALID_SIGNATURE, null, null);
        log.info("Attempt to change memo with no admin signature did not succeed.");

        // Update that should fail on invalid signature
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            CONTRACT_MEMO_STRING_2, initialAccount, ResponseCodeEnum.INVALID_SIGNATURE, null, null);
        log.info("Attempt to change memo with wrong admin signature did not succeed.");

        // Update that should fail on no signature
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            CONTRACT_MEMO_STRING_2, null, ResponseCodeEnum.INVALID_SIGNATURE, null, null);
        log.info("Attempt to change memo with no admin signature did not succeed.");

        // Update that should fail on invalid signature
        updateContractWithKey(crAccount, sampleStorageContractId, null, null,
            CONTRACT_MEMO_STRING_2, genesisAccount, ResponseCodeEnum.INVALID_SIGNATURE, null, null);
        log.info("Attempt to change memo with wrong admin signature did not succeed.");

        // Now test contract with no admin key.
        ContractID noAdminKeyContractId = createContractWithKey(crAccount, simpleStorageFileId,
            contractDuration, null, null);
        Assert.assertNotNull(noAdminKeyContractId);
        Assert.assertNotEquals(0L, noAdminKeyContractId.getContractNum());
        log.info("No-admin key contract created successfully");

        // Try to update memo on contract with no admin key (immutable)
        updateContractWithKey(crAccount, noAdminKeyContractId, null, null,
            CONTRACT_MEMO_STRING_2, null, ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT, null,
            null);
        log.info("Attempt to change memo in immutable contract did not succeed.");

        // Try to delete contract with no admin key (immutable)
        deleteContract(crAccount, noAdminKeyContractId, null,
            ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT);
        log.info("Attempt to delete immutable contract did not succeed.");

        // Update only expiration for immutable contract should be allowed.
        c1NewExpirationDate = Timestamp.newBuilder()
            .setSeconds(moreC1Info.getExpirationTime().getSeconds() + (DAY_SEC * 30)).build();
        updateContractWithKey(crAccount, noAdminKeyContractId, null, c1NewExpirationDate,
            null, null, ResponseCodeEnum.SUCCESS, null, null);
        log.info("Can change only expiration in immutable contract.");

        // Delete that should fail on no signature
        deleteContract(crAccount, sampleStorageContractId, null,
            ResponseCodeEnum.INVALID_SIGNATURE);
        log.info("Attempt to delete with no admin signature did not succeed.");

        // Delete that should fail on invalid signature
        deleteContract(crAccount, sampleStorageContractId, genesisAccount,
            ResponseCodeEnum.INVALID_SIGNATURE);
        log.info("Attempt to delete with invalid admin signature did not succeed.");

        // Delete that should succeed
        deleteContract(crAccount, sampleStorageContractId, adminAccount,
            ResponseCodeEnum.SUCCESS);
        log.info("Contract deleted successfully");
      }
      // Marker message for regression report
      log.info("Regression summary: This run is successful.");
    }
  }

  private ContractInfo getContractInfo(AccountID payerAccount,
      ContractID contractId) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetInfo);

    Response respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee,
        ResponseType.COST_ANSWER);

    fee = respToReturn.getContractGetInfo().getHeader().getCost();
    respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee,
        ResponseType.ANSWER_ONLY);
    ContractInfo contractInfToReturn = null;
    contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();
    channel.shutdown();

    return contractInfToReturn;
  }


  private Response executeGetContractInfo(AccountID payerAccount, ContractID contractId,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
      ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);

    Query getContractInfoQuery = RequestBuilder
        .getContractGetInfoQuery(contractId, paymentTx, responseType);

    Response respToReturn = stub.getContractInfo(getContractInfoQuery);
    return respToReturn;
  }

  public static void validateTransactionRecordForErrorCase(TransactionRecord txRecord) {
    Assert.assertNotNull(txRecord);
    List<AccountAmount> accountAmounts = txRecord.getTransferList().getAccountAmountsList();
    Assert.assertTrue(!accountAmounts.isEmpty());
    Assert.assertTrue(accountAmounts.get(0).getAmount() != 0);
  }
}
