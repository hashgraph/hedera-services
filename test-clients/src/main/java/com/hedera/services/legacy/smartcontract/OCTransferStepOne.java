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
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Function test for ERC20 transfers, step 1: Create accounts and contract
 *
 * @author Peter
 */
public class OCTransferStepOne {

  private static long TX_DURATION_SEC = 3 * 60; // 3 minutes for tx dedup
  private static long DAY_SEC = 24 * 60 * 60; // secs in a day


  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final int MAX_BUSY_RETRIES = 25;
  private static final int BUSY_RETRY_MS = 200;
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
  private static ManagedChannel channelShared;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;
  private static SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;
  private static long contractDuration;


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

  static KeyPair getMasterKeyPair() {
    String pubKeyStr = "302a300506032b657003210077e22838c072db186720e20171ac2c065f5492b75f3b2641f691b7d7c3d94a4b";
    String privKeyStr = "302e020100300506032b6570042204203f1c71a139dc67f6a5a0c090b92fb4edcf12f72fa4f868468de112adf4679f7b";

    EdDSAPublicKey pubKey = null;
    EdDSAPrivateKey privKey = null;
    try {
      byte[] pubKeybytes = HexUtils.hexToBytes(pubKeyStr);
      X509EncodedKeySpec pubEncoded = new X509EncodedKeySpec(pubKeybytes);
      pubKey = new EdDSAPublicKey(pubEncoded);

      byte[] privArray = Hex.decodeHex(privKeyStr);
      PKCS8EncodedKeySpec privEncoded = new PKCS8EncodedKeySpec(privArray);
      privKey = new EdDSAPrivateKey(privEncoded);
    } catch (Exception e) {
      System.out.println(e);
      Assert.fail("Could not decode key pair");
    }

    return new KeyPair(pubKey, privKey);
  }

  public static void main(String args[]) throws Exception {
    long initialTokenSupply = 10_000_000_000L;
    long initialTokens = 200_000;

    String contractFileName = "octoken.bin";
    String transferFileName = "transfer.txt";

    Properties properties = getApplicationProperties();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    grpcHost = properties.getProperty("host");
    grpcPort = Integer.parseInt(properties.getProperty("port"));

    int numberOfAccounts = 500;
    if ((args.length) > 0) {
      numberOfAccounts = Integer.parseInt(args[0]);
    }

    System.out.println(grpcHost + ":: is the grpc host");

    channelShared = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
        .usePlaintext(true)
        .build();
    cryptoStub = CryptoServiceGrpc.newBlockingStub(channelShared);
    sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channelShared);
    loadGenesisAndNodeAcccounts();

    TestHelper.initializeFeeClient(channelShared, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);

    Map<AccountID, String> tokenOwners = new HashMap<>();

    KeyPair masterKeyPair = getMasterKeyPair();
    long initialBalance = TestHelper.getCryptoMaxFee() * numberOfAccounts;

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
      transfer(ocTokenContract, tokenIssuer, newEthAddress, initialTokens);
      System.out.println("---------- Account " + accounts + " initialized: " +
          newAccount.getAccountNum());
    }
    long end = System.nanoTime();
    long elapsedMillis = (end - start) / 1_000_000;
    System.out.println("Creating and priming " + numberOfAccounts + " accounts took " +
        elapsedMillis / 1000.0 + " seconds");

    ArrayList<AccountID> accounts = new ArrayList<>(tokenOwners.keySet());

    // Write the contract number and account numbers to the transfer file so that subsequent
    // processes can construct the same Contract and Account IDs.
    Path transferPath = FileSystems.getDefault().getPath(transferFileName);
    try (BufferedWriter writer = Files.newBufferedWriter(transferPath)) {
      writer.write(ocTokenContract.getContractNum() + "\n");
      writer.write("- - - - -");  // Arbitrary separator after contract
      for (AccountID id : accounts) {
        writer.write("\n" + id.getAccountNum());
      }
    } catch (IOException e) {
      System.out.println(e);
      Assert.fail("Exception writing account list");
    }

    System.out.println("Account transfer file written to " +
        transferPath.toAbsolutePath().toString());
    channelShared.shutdown();
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

    TransactionResponse response = null;
    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      response = cryptoStub.createAccount(transaction);
      System.out.println("Pre Check Response of Create  account :: " +
          response.getNodeTransactionPrecheckCode().name());
      if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,response.getNodeTransactionPrecheckCode());
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK,response.getNodeTransactionPrecheckCode());

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
      Thread.sleep(2000);
      transactionReceipts = cryptoStub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }    Assert.assertEquals(ResponseCodeEnum.SUCCESS, transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());

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
            Duration.newBuilder().setSeconds(contractDuration).build(), accountKeyPairs.get(payerAccount), "",
            null);

    TransactionResponse response = null;
    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      response = sCServiceStub.createContract(createContractRequest);
      System.out.println(" createContract Pre Check Response :: " +
          response.getNodeTransactionPrecheckCode().name());
      if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,response.getNodeTransactionPrecheckCode());
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK,response.getNodeTransactionPrecheckCode());

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

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();;
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
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
