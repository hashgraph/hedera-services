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
import com.hedera.services.legacy.regression.LegacySmartContractTest;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Test an ERC20 contract, transfers and authorization
 *
 * @author Constantin
 */
public class OCTokenIT extends LegacySmartContractTest {

  private static long TX_DURATION_SEC = 3 * 60; // 3 minutes for tx dedup
  private static long DAY_SEC = 24 * 60 * 60; // secs in a day


  private static final Logger log = LogManager.getLogger(OCTokenIT.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
  private static final String BALANCE_OF_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String DECIMALS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String APPROVE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String TRANSFER_FROM_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"},{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SYMBOL_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";


  private static AccountID nodeAccount = AccountID.newBuilder()
      .setAccountNum(Utilities.getDefaultNodeAccount()).setRealmNum(0l).setShardNum(0l).build();
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static AccountID genesisAccount;
  private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String grpcHost;
  private static int grpcPort;
  private static long localCallGas;
  private static long contractDuration;

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
    OCTokenIT scOCT = new OCTokenIT();
    Properties properties = getApplicationProperties();
    OCTokenIT.grpcHost = properties.getProperty("host");
    scOCT.demo(grpcHost, nodeAccount);
  }

  public void demo(String grpcHost, AccountID nodeAccount) throws Exception{

    OCTokenIT.grpcHost = grpcHost;
    OCTokenIT.nodeAccount = nodeAccount;
    Properties properties = getApplicationProperties();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    grpcPort = Integer.parseInt(properties.getProperty("port"));
    localCallGas = Long.parseLong(properties.getProperty("LOCAL_CALL_GAS"));
    channelShared = ManagedChannelBuilder.forAddress(OCTokenIT.grpcHost, grpcPort)
            .usePlaintext()
            .build();
    cryptoStub = CryptoServiceGrpc.newBlockingStub(channelShared);
    sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channelShared);
    loadGenesisAndNodeAcccounts();

    TestHelper.initializeFeeClient(channelShared, genesisAccount, accountKeyPairs.get(genesisAccount),
            OCTokenIT.nodeAccount);

    Map<String, String> tokenOwners = new HashMap<String, String>();

    KeyPair tokenIssureKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID tokenIssuer = createAccount(tokenIssureKeyPair, genesisAccount, 10000000000000L);
    System.out.println("Token Isssuer account created");
    AccountInfo crAccInfo = getCryptoGetAccountInfo(tokenIssuer);

    String tokenIssuerEthAddress = crAccInfo.getContractAccountID();

    AccountID alice = createAccount(tokenIssuer, 10000000000L);
    System.out.println("Alice  account created");
    AccountInfo accInfoAlice = getCryptoGetAccountInfo(alice);
    String aliceEthAddress = accInfoAlice.getContractAccountID();

    AccountID bob = createAccount(tokenIssuer, 10000000000L);
    System.out.println("Bob  account created");
    AccountInfo accInfoBob = getCryptoGetAccountInfo(bob);
    String bobEthAddress = accInfoBob.getContractAccountID();

    tokenOwners.put("Issuer", tokenIssuerEthAddress);
    tokenOwners.put("Alice", aliceEthAddress);
    tokenOwners.put("Bob", bobEthAddress);

    String fileName = "testfiles/octoken.bin";

    if (tokenIssuer != null) {

      FileID ocTokenCode = LargeFileUploadIT.uploadFile(tokenIssuer, fileName, tokenIssureKeyPair);
      if (ocTokenCode != null) {

        long initialSupply = 1000_000L;
        ContractID ocTokenContract = createTokenContract(tokenIssuer, ocTokenCode, 1000_000L,
                "OpenCrowd Token", "OCT");
        crAccInfo = getCryptoGetAccountInfo(tokenIssuer);
        log.info("crAccInfo========>>>>>   " + crAccInfo);
        Assert.assertNotNull(ocTokenContract);
        System.out.println("@@@ Contract Adress is  " + ocTokenContract.toString());
        long tokenDecimals = decimals(ocTokenContract, tokenIssuer);
        String symbol = symbol(ocTokenContract, tokenIssuer);
        System.out.println("decimals = " + tokenDecimals);
        long tokenMultiplier = (long) Math.pow(10, tokenDecimals);
        long balanceOfTokenIssuer = balanceOf(ocTokenContract, tokenIssuerEthAddress, tokenIssuer);

        System.out.println(
                "@@@ Balance of token issuer  " + balanceOfTokenIssuer / tokenMultiplier + " " + symbol
                        + "  decimals = " + tokenDecimals);
        assert (initialSupply * tokenMultiplier) == balanceOfTokenIssuer;

        System.out.println("token owner transfers 1000 tokens to Alice ");

        transfer(ocTokenContract, tokenIssuer, aliceEthAddress, 1000 * tokenMultiplier);

        System.out.println("token owner transfers 2000 tokens to Bob");
        transfer(ocTokenContract, tokenIssuer, bobEthAddress, 2000 * tokenMultiplier);

        balanceOfTokenIssuer = balanceOf(ocTokenContract, tokenIssuerEthAddress, tokenIssuer);
        long balanceOfAlice = balanceOf(ocTokenContract, aliceEthAddress, alice);
        long balanceOfBob = balanceOf(ocTokenContract, bobEthAddress, bob);

        assert (initialSupply * tokenMultiplier) - (3000 * tokenMultiplier) == balanceOfTokenIssuer;
        assert (1000 * tokenMultiplier) == balanceOfAlice;
        assert (2000 * tokenMultiplier) == balanceOfBob;

        printBalances(ocTokenContract, tokenOwners, tokenMultiplier, symbol, tokenIssuer);

        //adding new token owner Carol
        AccountID carol = createAccount(tokenIssuer, 100000000000L);
        System.out.println("Carol account created");
        AccountInfo accInfoCarol = getCryptoGetAccountInfo(carol);
        String carolEthAddress = accInfoCarol.getContractAccountID();
        tokenOwners.put("Carol", carolEthAddress);

        System.out.println("Bob transfers 500 tokens to Carol");
        transfer(ocTokenContract, bob, carolEthAddress, 500 * tokenMultiplier);
        balanceOfBob = balanceOf(ocTokenContract, bobEthAddress, bob);
        long balanceOfCarol = balanceOf(ocTokenContract, carolEthAddress, carol);

        assert (1500 * tokenMultiplier) == balanceOfBob;
        assert (500 * tokenMultiplier) == balanceOfCarol;
        printBalances(ocTokenContract, tokenOwners, tokenMultiplier, symbol, tokenIssuer);

        //Create new account Dave
        AccountID dave = createAccount(tokenIssuer, 100000000000L);
        System.out.println("Dave account created");
        AccountInfo accInfoDave = getCryptoGetAccountInfo(dave);
        String daveEthAddress = accInfoDave.getContractAccountID();
        tokenOwners.put("Dave", daveEthAddress);

        System.out.println("Alice Allows to Dave to spend up to 200 Alice's tokens");
        approve(ocTokenContract, alice, daveEthAddress, 200 * tokenMultiplier);

        System.out.println("Dave transfers 100 token from Alice account into Bob's");
        transferFrom(ocTokenContract, dave, aliceEthAddress, bobEthAddress, 100 * tokenMultiplier);
        balanceOfBob = balanceOf(ocTokenContract, bobEthAddress, bob);
        balanceOfAlice = balanceOf(ocTokenContract, aliceEthAddress, alice);
        assert (1600 * tokenMultiplier) == balanceOfBob;
        assert (900 * tokenMultiplier) == balanceOfAlice;

        printBalances(ocTokenContract, tokenOwners, tokenMultiplier, symbol, tokenIssuer);
        System.out.println();

        log.info("Get Tx records by account Id...");
        long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetRecords);
        Query query = TestHelper.getTxRecordByContractId(ocTokenContract, tokenIssuer,
                tokenIssureKeyPair, OCTokenIT.nodeAccount, fee, ResponseType.COST_ANSWER);
        Response transactionRecord = sCServiceStub.getTxRecordByContractID(query);
        Assert.assertNotNull(transactionRecord);

        fee = transactionRecord.getContractGetRecordsResponse().getHeader().getCost();
        query = TestHelper.getTxRecordByContractId(ocTokenContract, tokenIssuer,
                tokenIssureKeyPair, OCTokenIT.nodeAccount, fee, ResponseType.ANSWER_ONLY);
        transactionRecord = sCServiceStub.getTxRecordByContractID(query);
        Assert.assertNotNull(transactionRecord.getContractGetRecordsResponse());
        List<TransactionRecord> recordList = transactionRecord.getContractGetRecordsResponse()
                .getRecordsList();
        log.info("Tx Records List for contract ID " + ocTokenContract.getContractNum() + " :: "
                + recordList.size());
        channelShared.shutdown();
        log.info("--------------------¯\\_(ツ)_/¯----------------------");

        // Marker message for regression report
        log.info("Regression summary: This run is successful.");
      }
    }
  }

  private static Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer, accountKeyPairs.get(payer), nodeAccount, transferAmt);
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
    while (attempts <= MAX_RECEIPT_RETRIES && !transactionReceipts.getTransactionGetReceipt()
        .getReceipt()
        .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      Thread.sleep(2000);
      transactionReceipts = cryptoStub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as Success..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }
    if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
        .equals(ResponseCodeEnum.SUCCESS)) {
      receiptToReturn = transactionReceipts.getTransactionGetReceipt();
    }

    return transactionReceipts.getTransactionGetReceipt();

  }

  private static ContractID createContract(AccountID payerAccount, FileID contractFile,
      byte[] constructorData) throws Exception {
    ContractID createdContract = null;
    ByteString dataToPass = ByteString.EMPTY;
    if (constructorData != null) {
      dataToPass = ByteString.copyFrom(constructorData);
    }

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "", 4_000, contractFile, dataToPass, 0,
            Duration.newBuilder().setSeconds(contractDuration).build(), accountKeyPairs.get(payerAccount), "",
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
    TransactionRecord trRecord = getTransactionRecord(payerAccount,
        createContractBody.getTransactionID());
    Assert.assertNotNull(trRecord);
    Assert.assertTrue(trRecord.hasContractCreateResult());
    Assert.assertEquals(trRecord.getContractCreateResult().getContractID(),
        contractCreateReceipt.getReceipt().getContractID());

    return createdContract;
  }


  private static byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    ContractID createdContract = null;

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), Utilities.getDefaultNodeAccount(), 0l, 0l,
            TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = sCServiceStub.contractCallMethod(callContractRequest);
    System.out.println(
        " call contract  Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
    		callContractBody.getTransactionID());
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
    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);

    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    paymentTx = createQueryHeaderTransfer(payer, fee);
    getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
    recordResp = stub.getTxRecordByTxID(getRecordQuery);

    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    System.out.println("tx record = " + txRecord);

    return txRecord;
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

    Response callResp = sCServiceStub.contractCallLocalMethod(contractCallLocal);

    fee = callResp.getContractCallLocal().getHeader().getCost() + localCallGas;
    paymentTx = createQueryHeaderTransfer(payer, fee);
    contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            ResponseType.ANSWER_ONLY);

    callResp = sCServiceStub.contractCallLocalMethod(contractCallLocal);
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

  private static void printBalances(ContractID contractAddress, Map<String, String> tokenOwners,
      long multiplier, String symbol, AccountID payer) throws Exception {
    Map<String, Long> balancePerOwner = new HashMap<String, Long>(tokenOwners.size());
    for (String tokenOwnerName : tokenOwners.keySet()) {
      String tokenOwnerAccountEthFormat = tokenOwners.get(tokenOwnerName);
      long balance = balanceOf(contractAddress, tokenOwnerAccountEthFormat, payer);
      balancePerOwner.put(tokenOwnerName, balance);

    }
    System.out.println(
        "---------------------------------------------balances-----------------------------------------------------------");
    for (String currentTokenOwnerName : balancePerOwner.keySet()) {
      long currBalance = balancePerOwner.get(currentTokenOwnerName);
      System.out.println(
          currentTokenOwnerName + " has a balance of " + currBalance / multiplier + " " + symbol);
    }
    System.out.println(
        "----------------------------------------------------------------------------------------------------------------");
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
}
