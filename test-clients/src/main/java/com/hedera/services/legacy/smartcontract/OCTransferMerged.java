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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
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
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Performance test for ERC20 transfers
 *
 * @author Peter
 */
public class OCTransferMerged {
  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final int MAX_BUSY_RETRIES = 15;
  private static final int BUSY_RETRY_MS = 200;
  private static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
  private static final String TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String APPROVE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String TRANSFER_FROM_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"},{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";


  private static long nodeAccountNum;
  private static AccountID nodeAccount;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static AccountID genesisAccount;
  private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String grpcHost;
  private static int grpcPort;
  private static long contractDuration;
  private static ManagedChannel channelShared = null;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;
  private static SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;


  private static void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> hederaAccounts = null;
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

  public static void main(String args[]) throws Exception {
    long transferSize = 10;

    long initialTokenSupply = 10_000_000_000L;
    long initialTokens = 200_000;

    KeyPair masterKeyPair = OCTransferStepOne.getMasterKeyPair();
    long initialBalance = TestHelper.getCryptoMaxFee() * 10L;
    String contractFileName = "octoken.bin";
    String transferFileName = "transfer.txt";
    ArrayList<AccountID> accountsList = new ArrayList<>();

    Properties properties = getApplicationProperties();
    grpcPort = Integer.parseInt(properties.getProperty("port"));
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));

    grpcHost = properties.getProperty("host");
    if ((args.length) > 0) {
      grpcHost = args[0];
      System.out.println("Got host as " + grpcHost);
    }

    nodeAccountNum = Utilities.getDefaultNodeAccount();
    if ((args.length) > 1) {
      nodeAccountNum = Long.parseLong(args[1]);
      System.out.println("Got Node Account as " + nodeAccountNum);
    }
    nodeAccount = RequestBuilder
        .getAccountIdBuild(nodeAccountNum, 0l, 0l);


    int numberOfTransfers = 30; // Test change for Perf_dev branch only
    if ((args.length) > 2) {
      numberOfTransfers = Integer.parseInt(args[2]);
      System.out.println("Got number of transfers as " + numberOfTransfers);
    }

    int numberOfAccounts = 10; // Test change for Perf_dev branch only

    createStubs();
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
        .usePlaintext(true)
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    Map<AccountID, String> tokenOwners = new HashMap<>();

    // Create the token issuing account
    AccountID tokenIssuer = createAccount(masterKeyPair, genesisAccount, initialBalance);
    Assert.assertNotNull(tokenIssuer);
    Assert.assertNotEquals(0, tokenIssuer.getAccountNum());
    System.out.println("Token Issuer account created: " + tokenIssuer);
    AccountInfo crAccInfo = getCryptoGetAccountInfo(tokenIssuer);
    String tokenIssuerEthAddress = crAccInfo.getContractAccountID();


    // Create the contract
    FileID ocTokenCode = LargeFileUploadIT.uploadFile(tokenIssuer, contractFileName,
        Collections.singletonList(masterKeyPair.getPrivate()),
        grpcHost, nodeAccount);

    ContractID ocTokenContract = createTokenContract(tokenIssuer, ocTokenCode, initialTokenSupply,
        "OpenCrowd Token", "OCT");
    Assert.assertNotNull(ocTokenContract);
    Assert.assertNotEquals(0, ocTokenContract.getContractNum());
    System.out.println("@@@ Contract Address is  " + ocTokenContract.toString());

    // Create test accounts (with the same keypair) and give them tokens
    long start = System.nanoTime();
    for (int accounts = 0; accounts < numberOfAccounts; accounts++) {
      AccountID newAccount = createAccount(masterKeyPair, genesisAccount, initialBalance);
      Assert.assertNotEquals(0, newAccount.getAccountNum());
      String newEthAddress =
          CommonUtils.calculateSolidityAddress(0, 0L, newAccount.getAccountNum());
      tokenOwners.put(newAccount, newEthAddress);
      accountsList.add(newAccount);

      transfer(ocTokenContract, tokenIssuer, newEthAddress, initialTokens);
      System.out.println("---------- Account " + accounts + " initialized: " +
          newAccount.getAccountNum());
    }
    long end = System.nanoTime();
    long elapsedMillis = (end - start) / 1_000_000;
    System.out.println("Creating and priming " + numberOfAccounts + " accounts took " +
        elapsedMillis / 1000.0 + " seconds");


    AccountID payingAccount = accountsList.get(0); // Arbitrary account to pay fees

    // Do random transfers
    start = System.nanoTime();
    int size = accountsList.size();
    for (int i = 0; i < numberOfTransfers; i++) {
      int from = ThreadLocalRandom.current().nextInt(size);
      // Be sure to pick a different index
      int to = (from + 1 + ThreadLocalRandom.current().nextInt(size - 1)) % size;

      AccountID fromId = accountsList.get(from);
      AccountID toId = accountsList.get(to);
      String toEthAddress = tokenOwners.get(toId);
      System.out.println("Transfer " + i + " from " + fromId.getAccountNum() + " to "
          + toId.getAccountNum());
      transfer(ocTokenContract, fromId, toEthAddress, transferSize);
    }
    end = System.nanoTime();
    elapsedMillis = (end - start) / 1_000_000;
    System.out.println("Making " + numberOfTransfers + " transfers took " +
        elapsedMillis / 1000.0 + " seconds");
    double tps = (numberOfTransfers * 100000 / elapsedMillis) / 100.0;
    System.out.println ("About " + tps + " TPS.");

    channelShared.shutdown();
  }

  private static void createStubs() {
    if (channelShared != null) {
      channelShared.shutdown();
    }
    channelShared = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
        .usePlaintext(true)
        .build();
    cryptoStub = CryptoServiceGrpc.newBlockingStub(channelShared);
    sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channelShared);
  }

  private static Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer,
        accountKeyPairs.get(payer), nodeAccount, transferAmt);
    return transferTx;
  }

  private static AccountID createAccount(AccountID payerAccount, long initialBalance)
      throws Exception {
    KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
    return createAccount(keyGenerated, payerAccount, initialBalance);
  }

  private static AccountID createAccount(KeyPair keyPair, AccountID payerAccount,
      long initialBalance) throws Exception {

    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
            accountKeyPairs.get(payerAccount));
    TransactionResponse response = retryLoopTransaction(transaction, "createAccount");
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    System.out.println(
        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), cryptoStub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    return newlyCreateAccountId;
  }

  private static TransactionGetReceiptResponse getReceipt(TransactionID transactionId)
      throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();

    Response transactionReceipts = cryptoStub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES &&
        (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN) ||
         transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.BUSY))) {
      Thread.sleep(500);
      transactionReceipts = cryptoStub.getTransactionReceipts(query);
//      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
//          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());

    return transactionReceipts.getTransactionGetReceipt();

  }

  private static ContractID createContract(AccountID payerAccount, FileID contractFile,
      byte[] constructorData) throws Exception {
    ContractID createdContract = null;
    ByteString dataToPass = ByteString.EMPTY;
    if (constructorData != null) {
      dataToPass = ByteString.copyFrom(constructorData);
    }

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();;
    Duration transactionDuration = RequestBuilder.getDuration(180);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "", 1250000, contractFile, dataToPass, 0,
            Duration.newBuilder().setSeconds(contractDuration).build(), accountKeyPairs.get(payerAccount), "",
            null);

    TransactionResponse response = retryLoopTransaction(createContractRequest, "createContract");
    Assert.assertNotNull(response);
    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
    		createContractBody.getTransactionID());
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
    }

    return createdContract;
  }


  private static byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    ContractID createdContract = null;

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();;
    Duration transactionDuration = RequestBuilder.getDuration(180);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccountNum, 0l, 0l,
            TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));


    TransactionResponse response = retryLoopTransaction(callContractRequest, "contractCallMethod");
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK,response.getNodeTransactionPrecheckCode());

    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
    		callContractBody.getTransactionID());
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      //Thread.sleep(6000);
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
          System.out.println("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
    return dataToReturn;
  }

  private static TransactionRecord getTransactionRecord(AccountID payer,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    long fee = FeeClient.getCostForGettingTxRecord();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc
        .newBlockingStub(channelShared);
    Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.COST_ANSWER);
    Response recordResp = retryLoopQuery(getRecordQuery, "getTxRecordByTxID");
    Assert.assertNotNull(recordResp);

    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
    recordResp = retryLoopQuery(getRecordQuery, "getTxRecordByTxID");
    Assert.assertNotNull(recordResp);

    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
//    System.out.println("tx record = " + txRecord);

    return txRecord;
  }

  private static CallTransaction.Function getConstructorFunction() {
    String funcJson = TOKEN_ERC20_CONSTRUCTOR_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] getEncodedConstructor(long initialSupply, String tokenName,
      String tokenSymbol) {
    String retStr = "";
    CallTransaction.Function func = getConstructorFunction();
    byte[] encodedFunc = func.encodeArguments(initialSupply, tokenName, tokenSymbol);

    return encodedFunc;
  }

  private static ContractID createTokenContract(AccountID payerAccount, FileID contractFile,
      long initialTokensSupply, String tokenName, String tokenSymbol) throws Exception {
    byte[] constructorData = getEncodedConstructor(initialTokensSupply, tokenName, tokenSymbol);
    ContractID createdContract = null;
    createdContract = createContract(payerAccount, contractFile, constructorData);

    return createdContract;
  }

  private static AccountInfo getCryptoGetAccountInfo(
      AccountID accountID) throws Exception {

    long fee = FeeClient.getCostForGettingAccountInfo();
    Transaction paymentTx = createQueryHeaderTransfer(accountID, fee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, ResponseType.COST_ANSWER);

    Response respToReturn = cryptoStub.getAccountInfo(cryptoGetInfoQuery);

    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(accountID, fee);
    cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, ResponseType.ANSWER_ONLY);
    respToReturn = cryptoStub.getAccountInfo(cryptoGetInfoQuery);

    AccountInfo accInfToReturn = null;
    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();

    return accInfToReturn;
  }

  private static CallTransaction.Function getTransferFunction() {
    String funcJson = TRANSFER_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeTransfer(String toAccountAddress, long valueToTransfer) {
    String retVal = "";
    CallTransaction.Function function = getTransferFunction();
    byte[] encodedFunc = function.encode(toAccountAddress, valueToTransfer);

    return encodedFunc;
  }

  private static void transfer(ContractID contractId, AccountID payerAccount,
      String toAccountAddress, long valueToTransfer) throws Exception {
    byte[] dataToSet = encodeTransfer(toAccountAddress, valueToTransfer);
    //set value to simple storage smart contract
    byte[] retData = callContract(payerAccount, contractId, dataToSet);
  }


  private static CallTransaction.Function getApproveFunction() {
    String funcJson = APPROVE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeApprove(String spenderAccountAddress, long valueToApprove) {
    String retVal = "";
    CallTransaction.Function function = getApproveFunction();
    byte[] encodedFunc = function.encode(spenderAccountAddress, valueToApprove);

    return encodedFunc;
  }

  private static void approve(ContractID contractId, AccountID payerAccount,
      String spenderAccountAddress, long valueToApprove) throws Exception {
    byte[] dataToSet = encodeApprove(spenderAccountAddress, valueToApprove);
    //set value to simple storage smart contract
    byte[] retData = callContract(payerAccount, contractId, dataToSet);
  }


  private static CallTransaction.Function getTransferFromFunction() {
    String funcJson = TRANSFER_FROM_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeTransferFrom(String fromAddress, String toAddress, long value) {
    String retVal = "";
    CallTransaction.Function function = getTransferFromFunction();
    byte[] encodedFunc = function.encode(fromAddress, toAddress, value);
    return encodedFunc;
  }

  private static void transferFrom(ContractID contractId, AccountID payerAccount,
      String fromAccountAddress, String toAccountAddress, long valueToTransfer) throws Exception {
    byte[] dataToSet = encodeTransferFrom(fromAccountAddress, toAccountAddress, valueToTransfer);
    //set value to simple storage smart contract
    byte[] retData = callContract(payerAccount, contractId, dataToSet);
  }

  private static Properties getApplicationProperties() {
    Properties prop = new Properties();
    InputStream input = null;
    try {
      String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
      input = new FileInputStream(rootPath + "application.properties");
      prop.load(input);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return prop;
  }
  private static TransactionResponse retryLoopTransaction(Transaction transaction, String apiName) {
    TransactionResponse response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {
      try {
        switch (apiName) {
          case "createAccount":
            response = cryptoStub.createAccount(transaction);
            break;
          case "createContract":
            response = sCServiceStub.createContract(transaction);
            break;
          case "contractCallMethod":
            response = sCServiceStub.contractCallMethod(transaction);
            break;
          default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        System.out.println("Platform exception ..." + ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          createStubs();
        }
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      try {
        Thread.sleep(BUSY_RETRY_MS);
      } catch (InterruptedException e) {
        ;
      }
    }
    return response;
  }

  private static Response retryLoopQuery(Query query, String apiName) {
    Response response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {
      ResponseCodeEnum precheckCode;
      try {
        switch (apiName) {
          case "getTxRecordByTxID":
            response = cryptoStub.getTxRecordByTxID(query);
            precheckCode = response.getTransactionGetRecord()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        System.out.println("Platform exception ..." + ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          createStubs();
        }
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(precheckCode)) {
        break;
      }
      try {
        Thread.sleep(BUSY_RETRY_MS);
      } catch (InterruptedException e) {
        ;
      }
    }
    return response;
  }}
