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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
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
 * Function test for ERC20 transfers, step 2: execute the transfers
 *
 * @author Peter
 */
public class OCTransferStepTwo {
  private static final long TX_DURATION_SEC = 3 * 60; // 3 minutes for tx dedup
  private static final int MAX_RECEIPT_RETRIES = 120;
  private static final int MAX_BUSY_RETRIES = 25;
  private static final int BUSY_RETRY_MS = 200;
  private static final int BATCH_SIZE = 20;
  private static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
  private static final String BALANCE_OF_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String DECIMALS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String APPROVE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String TRANSFER_FROM_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"},{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SYMBOL_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";


  private static long nodeAccountNum;
  private static AccountID nodeAccount;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static AccountID genesisAccount;
  private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String grpcHost;
  private static int grpcPort;
  private static ManagedChannel channelShared;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;
  private static SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;


  private static void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> hederaAccounts = null;
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");
    ;

    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

    // get the Account Object
    genesisAccount = genesisAccountList.get(0).getAccountId();
    accountKeyPairs.put(genesisAccount, genesisKeyPair);
  }

  public static void main(String args[]) throws Exception {
    long transferSize = 100;

    KeyPair masterKeyPair = OCTransferStepOne.getMasterKeyPair();
    String transferFileName = "transfer.txt";

    Properties properties = getApplicationProperties();
    grpcPort = Integer.parseInt(properties.getProperty("port"));

    if (args.length < 6) {
      System.out.println("Must provide all six arguments to this application.");
      System.out.println("0: host");
      System.out.println("1: node number");
      System.out.println("2: number of transfers");
      System.out.println("3: Smart Contract number");
      System.out.println("4: low Account number");
      System.out.println("5: high Account number");
      return;
     }

    System.out.println("args[0], host, is " + args[0]);
    System.out.println("args[1], node account, is " + args[1]);
    System.out.println("args[2], number of transfers, is " + args[2]);
    System.out.println("args[3], contract number, is " + args[3]);
    System.out.println("args[4], low account, is " + args[4]);
    System.out.println("args[5], high account, is " + args[5]);


    grpcHost = args[0];
    System.out.println("Got Grpc host as " + grpcHost);

    nodeAccountNum = Long.parseLong(args[1]);
    System.out.println("Got Node Account number as " + nodeAccountNum);
    nodeAccount = RequestBuilder
        .getAccountIdBuild(nodeAccountNum, 0l, 0l);


    int numberOfTransfers;
    numberOfTransfers = Integer.parseInt(args[2]);
    System.out.println("Got number of transfers as " + numberOfTransfers);

    long contractNumber = Long.parseLong(args[3]);
    ContractID ocTokenContract = RequestBuilder.getContractIdBuild(contractNumber, 0L, 0L);
    System.out.println("Got contract ID as " + ocTokenContract);

    long accountNumberLow = Long.parseLong(args[4]);
    System.out.println("Got low account number as " + accountNumberLow);

    long accountNumberHigh = Long.parseLong(args[5]);
    System.out.println("Got high account number as " + accountNumberHigh);

    channelShared = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
        .usePlaintext(true)
        .build();
    cryptoStub = CryptoServiceGrpc.newBlockingStub(channelShared);
    sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channelShared);
    loadGenesisAndNodeAcccounts();

    TestHelper.initializeFeeClient(channelShared, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);

    // Build the list of account IDs from the low and high account numbers
    ArrayList<AccountID> accounts = new ArrayList<>();
    for (long accountNum = accountNumberLow; accountNum <= accountNumberHigh; accountNum += 1L) {
      AccountID reconstructedAccountId = AccountID.newBuilder()
          .setShardNum(0L).setRealmNum(0L).setAccountNum(accountNum).build();
      accounts.add(reconstructedAccountId);
      System.out.println("Recovered account ID is " + reconstructedAccountId);
    }

    // Build the two maps, of private keys and ether addresses
    Map<AccountID, String> tokenOwners = new HashMap<>();

    for (AccountID id : accounts) {
      String newEthAddress =
          CommonUtils.calculateSolidityAddress(0, 0L, id.getAccountNum());
      tokenOwners.put(id, newEthAddress);
      accountKeyPairs.put(id, masterKeyPair);
    }

    AccountID payingAccount = accounts.get(0); // Arbitrary account to pay fees

    // Do random transfers
    long start = System.nanoTime();
    int size = accounts.size();
    List<TransactionID> transactions = new ArrayList<>();
    for (int i = 0; i < numberOfTransfers; i++) {
      int from = ThreadLocalRandom.current().nextInt(size);
      // Be sure to pick a different index
      int to = (from + 1 + ThreadLocalRandom.current().nextInt(size - 1)) % size;

      AccountID fromId = accounts.get(from);
      AccountID toId = accounts.get(to);
      String toEthAddress = tokenOwners.get(toId);
      System.out.println("Transfer " + i + " from " + fromId.getAccountNum() + " to "
          + toId.getAccountNum());
      TransactionID txId = transfer(ocTokenContract, fromId, toEthAddress, transferSize);
      transactions.add(txId);
      if (transactions.size() >= BATCH_SIZE) {
        clearTransactions(payingAccount, transactions);
      }
    }
    if (transactions.size() > 0) {
      clearTransactions(payingAccount, transactions);
    }

    long end = System.nanoTime();
    long elapsedMillis = (end - start) / 1_000_000;
    System.out.println("Making " + numberOfTransfers + " transfers took " +
        elapsedMillis / 1000.0 + " seconds");
    double tps = (numberOfTransfers * 100000 / elapsedMillis) / 100.0;
    System.out.println("About " + tps + " TPS");

    channelShared.shutdown();
  }

  private static void clearTransactions(AccountID payingAccount, List<TransactionID> transactions)
      throws Exception {
    for (TransactionID id : transactions) {
      readReceiptAndRecord(payingAccount, id);
    }
    transactions.clear();
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
    TransactionResponse response = cryptoStub.createAccount(transaction);
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
    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
        .getReceipt()
        .getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
      Thread.sleep(1000);
      transactionReceipts = cryptoStub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
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
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "", 1250000, contractFile, dataToPass, 0,
            Duration.newBuilder().setSeconds(300).build(), accountKeyPairs.get(payerAccount), "",
            null);

    TransactionResponse response = sCServiceStub.createContract(createContractRequest);
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


  private static TransactionID callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    ContractID createdContract = null;

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();;
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
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

    TransactionResponse response = null;
    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      response = sCServiceStub.contractCallMethod(callContractRequest);
      System.out.println(" call contract  Pre Check Response :: " +
          response.getNodeTransactionPrecheckCode().name());
      if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,response.getNodeTransactionPrecheckCode());
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK,response.getNodeTransactionPrecheckCode());

    TransactionID txId = TransactionBody.parseFrom(callContractRequest.getBodyBytes())
        .getTransactionID();
    return txId;
  }

  private static void readReceiptAndRecord(AccountID payerAccount, TransactionID txId)
      throws Exception {
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(txId);
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (!StringUtils.isEmpty(errMsg)) {
           System.out.println("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
  }

  private static TransactionRecord getTransactionRecord(AccountID payer,
      TransactionID transactionId) throws Exception {
    long fee = FeeClient.getCostForGettingTxRecord();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc
        .newBlockingStub(channelShared);
    Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.COST_ANSWER);
    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);

    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
    recordResp = stub.getTxRecordByTxID(getRecordQuery);

    return recordResp.getTransactionGetRecord().getTransactionRecord();
  }


  private static byte[] callContractLocal(ContractID contractToCall, byte[] data, AccountID payer)
      throws Exception {
    byte[] dataToReturn = null;
    AccountID createdAccount = null;
    long fee = FeeClient.getCostContractCallLocalFee(data.length);
    Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
    ByteString callData = ByteString.EMPTY;
    if (data != null) {
      callData = ByteString.copyFrom(data);
    }
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.COST_ANSWER);

    Response callResp = null;
    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      callResp = sCServiceStub.contractCallLocalMethod(contractCallLocal);
      ResponseCodeEnum preCheck = callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode();
      System.out.println(" call local Cost Answer Pre Check Response :: " + preCheck.name());
      if (ResponseCodeEnum.OK.equals(preCheck)) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,preCheck);
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK,
        callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());

   fee = callResp.getContractCallLocal().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.ANSWER_ONLY);

    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      callResp = sCServiceStub.contractCallLocalMethod(contractCallLocal);
      ResponseCodeEnum preCheck = callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode();
      System.out.println(" call local Pre Check Response :: " + preCheck.name());
      if (ResponseCodeEnum.OK.equals(preCheck)) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,preCheck);
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK,
        callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());

    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    System.out.println("callContractLocal response = " + callResp);

    return functionResults.toByteArray();
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

  private static CallTransaction.Function getDecimalsFunction() {
    String funcJson = DECIMALS_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeDecimals() {
    String retVal = "";
    CallTransaction.Function function = getDecimalsFunction();
    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private static long decodeDecimalsResult(byte[] value) {
    long decodedReturnedValue = 0;
    CallTransaction.Function function = getDecimalsFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.longValue();
    }
    return decodedReturnedValue;
  }

  public static long decimals(ContractID contractAddress, AccountID payer) throws Exception {
    long decimalsToReturn = 0;

    byte[] dataEncodeDecimals = encodeDecimals();

    byte[] valueOfDecimals = callContractLocal(contractAddress, dataEncodeDecimals, payer);
    //decode value from results
    decimalsToReturn = decodeDecimalsResult(valueOfDecimals);
    return decimalsToReturn;

  }

  private static CallTransaction.Function getSymbolFunction() {
    String funcJson = SYMBOL_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeSymbol() {
    String retVal = "";
    CallTransaction.Function function = getSymbolFunction();

    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private static String decodeSymbolResult(byte[] value) {
    String decodedReturnedValue = "";
    CallTransaction.Function function = getSymbolFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      decodedReturnedValue = (String) retResults[0];
    }
    return decodedReturnedValue;
  }

  public static String symbol(ContractID contractAddress, AccountID payer) throws Exception {
    String symbolToReturn;
    byte[] dataEncodeSymbol = encodeSymbol();

    byte[] valueOfSymbol = callContractLocal(contractAddress, dataEncodeSymbol, payer);
    //decode value from results
    symbolToReturn = decodeSymbolResult(valueOfSymbol);
    return symbolToReturn;

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

  private static CallTransaction.Function getBalanceOfFunction() {
    String funcJson = BALANCE_OF_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeBalanceOf(String address) {
    String retVal = "";
    CallTransaction.Function function = getBalanceOfFunction();

    byte[] encodedFunc = function.encode(address);

    return encodedFunc;
  }

  private static long decodeBalanceOfResult(byte[] value) {
    long decodedReturnedValue = 0;
    CallTransaction.Function function = getBalanceOfFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.longValue();
    }
    return decodedReturnedValue;
  }

  public static long balanceOf(ContractID contractAddress, String accountAdddressEthFormat,
      AccountID payer) throws Exception {
    long balance = 0;
    byte[] dataEncodeBalanceOF = encodeBalanceOf(accountAdddressEthFormat);

    byte[] valueOfBalance = callContractLocal(contractAddress, dataEncodeBalanceOF, payer);
    //decode value from results
    balance = decodeBalanceOfResult(valueOfBalance);
    return balance;

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

  private static TransactionID transfer(ContractID contractId, AccountID payerAccount,
      String toAccountAddress, long valueToTransfer) throws Exception {
    byte[] dataToSet = encodeTransfer(toAccountAddress, valueToTransfer);
    //set value to simple storage smart contract
    return callContract(payerAccount, contractId, dataToSet);
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
    callContract(payerAccount, contractId, dataToSet);
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

  private static void printBalances(ContractID contractAddress, Map<AccountID, String> tokenOwners,
      String symbol, AccountID payer) throws Exception {
    Map<AccountID, Long> balancePerOwner = new HashMap<>(tokenOwners.size());
    for (AccountID tokenOwner : tokenOwners.keySet()) {
      String tokenOwnerAccountEthFormat = tokenOwners.get(tokenOwner);
      long balance = balanceOf(contractAddress, tokenOwnerAccountEthFormat, payer);
      balancePerOwner.put(tokenOwner, balance);

    }
    System.out.println(
        "---------------------------------------------balances-----------------------------------------------------------");
    for (AccountID currentTokenOwner : balancePerOwner.keySet()) {
      long currBalance = balancePerOwner.get(currentTokenOwner);
      System.out.println(
          "accountNum " + currentTokenOwner.getAccountNum() + " has a balance of " +
              currBalance + " " + symbol);
    }
    System.out.println(
        "----------------------------------------------------------------------------------------------------------------");
  }


  private static void transferFrom(ContractID contractId, AccountID payerAccount,
      String fromAccountAddress, String toAccountAddress, long valueToTransfer) throws Exception {
    byte[] dataToSet = encodeTransferFrom(fromAccountAddress, toAccountAddress, valueToTransfer);
    //set value to simple storage smart contract
    callContract(payerAccount, contractId, dataToSet);
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
}
