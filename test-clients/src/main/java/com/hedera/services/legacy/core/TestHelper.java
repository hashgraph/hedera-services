package com.hedera.services.legacy.core;

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
import com.google.protobuf.TextFormat;
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hedera.services.legacy.regression.FeeUtility;
import com.hedera.services.legacy.regression.Utilities;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * @author Akshay
 * @Date : 8/14/2018
 */
public class TestHelper {

  private static final org.apache.log4j.Logger log = LogManager.getLogger(TestHelper.class);

  public static long DEFAULT_SEND_RECV_RECORD_THRESHOLD = 5000000000000000000L;
  public static long DEFAULT_WIND_SEC = -30; // seconds to wind back the UTC clock
  public static long TX_DURATION = 180;
  private static volatile long lastNano = 0;
  protected static Map<AccountID, Key> acc2ComplexKeyMap = new LinkedHashMap<>();
  protected static Map<ContractID, Key> contract2ComplexKeyMap = new LinkedHashMap<>();
  protected static Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
  protected static int MAX_RECEIPT_RETRIES = 108000;
  protected static long RETRY_FREQ_MILLIS = 50;
  protected static long MAX_RETRY_FREQ_MILLIS = 200;
  protected static boolean isExponentialBackoff = false;
  private static  Properties properties = TestHelper.getApplicationProperties();
  public static String fileName = TestHelper.getStartUpFile();
  public static Map<String, List<AccountKeyListObj>> getKeyFromFile(String strPath) throws IOException {
    log.info("Startup File Path: "+strPath);
    File tempFile =null;
    try { new File(strPath); }catch (Throwable t) {}
    Path path;
    if(tempFile==null || !tempFile.exists()) {
      try {
        int index = strPath.lastIndexOf("\\");
        if (index <= 0) index =  strPath.lastIndexOf("/");
        String localStartUpFile = strPath.substring(index+1);
        path = Paths.get(TestHelper.class.getClassLoader().getResource(localStartUpFile).toURI());
      }catch (Exception e) {
        log.info("Error while loading startup file "+e.getMessage());
        path = Paths.get(strPath);
      }
    }else {
      path = Paths.get(strPath);
    }
    log.info("Loading Startup File: "+path.toString());
    StringBuilder data = new StringBuilder();
    Stream<String> lines = Files.lines(path);
    lines.forEach(data::append);
    lines.close();
    byte[] accountKeyPairHolderBytes = CommonUtils.base64decode(String.valueOf(data));
    return (Map<String, List<AccountKeyListObj>>) CommonUtils
        .convertFromBytes(accountKeyPairHolderBytes);
  }

  // Read exchange rate from the application.properties file
  public static void initializeFeeClient(Channel channel, AccountID payerAccount,
      KeyPair payerKeyPair, AccountID nodeAccount) {
    int centEquivalent;
    int hbarEquivalent;
    Properties properties = TestHelper.getApplicationProperties();
    try {
      centEquivalent = Integer.parseInt(properties.getProperty("currentCentEquivalent"));
    } catch (NumberFormatException nfe) {
      centEquivalent = 12;
    }
    try {
      hbarEquivalent = Integer.parseInt(properties.getProperty("currentHbarEquivalent"));
    } catch (NumberFormatException nfe) {
      hbarEquivalent = 1;
    }

    FileServiceGrpc.FileServiceBlockingStub fStub = FileServiceGrpc.newBlockingStub(channel);
    FileID feeFileId = FileID.newBuilder()
        .setFileNum(FeeUtility.FEE_FILE_ACCOUNT_NUM)
        .setRealmNum(0L).setShardNum(0L).build();
    byte[] feeScheduleBytes = new byte[0];
    try {
    Response response = getFileContentInitial(fStub, feeFileId, payerAccount,
        payerKeyPair, nodeAccount);
      feeScheduleBytes = response.getFileGetContents().getFileContents().getContents()
          .toByteArray();
      if (feeScheduleBytes.length < 1) {
        log.error("Empty data found while reading Fee file from fcfs, status is "
            + response.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
      }
    } catch (Exception e) {
      log.error("Exception while reading Fee file from fcfs :: " + e);
    }

    FeeClient.initialize(hbarEquivalent, centEquivalent, feeScheduleBytes);
  }

  public static AccountID createFirstAccountFromGenesis(KeyPair firstPair,
      CryptoServiceBlockingStub stub) throws Exception {
    List<AccountKeyListObj> genesisAccount = getGenAccountKey();
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genKeyPairObj.getPrivateKey());
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = getDefaultNodeAccount();

    // create 1st account by payer as genesis
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 1000000l,
            genesisKeyPair);
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = getTxReceipt(body.getTransactionID(), stub);
    AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
    return newlyCreateAccountId1;
  }

  public static List<AccountKeyListObj> getGenAccountKey() throws URISyntaxException, IOException {
//    Path path = Paths
//        .get(Thread.currentThread().getContextClassLoader().getResource(fileName).toURI());
    Map<String, List<AccountKeyListObj>> keyFromFile = getKeyFromFile(fileName);

    return keyFromFile.get("START_ACCOUNT");
  }

  public static AccountID getDefaultNodeAccount() {
    return RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);
  }

  public static Transaction createAccountWithFee(AccountID payerAccount, AccountID nodeAccount,
      KeyPair pair, long initialBalance, List<PrivateKey> privKey) throws Exception {

    Transaction transaction = TestHelper
        .createAccount(payerAccount, nodeAccount, pair, initialBalance, 0,
            DEFAULT_SEND_RECV_RECORD_THRESHOLD, DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    Transaction signTransaction = TransactionSigner.signTransaction(transaction, privKey);
//    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction, privKey.size());
//    System.out.println("createAccountFee ===> " + createAccountFee);
    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount, pair, initialBalance, TestHelper.getCryptoMaxFee(),
            DEFAULT_SEND_RECV_RECORD_THRESHOLD, DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    signTransaction = TransactionSigner.signTransaction(transaction, privKey);
    return signTransaction;
  }
  public static Transaction createAccountWithSigMap(AccountID payerAccount, AccountID nodeAccount,
      KeyPair pair, long initialBalance, KeyPair payerKeyPair) throws Exception {
    Transaction transaction = TestHelper
        .createAccount(payerAccount, nodeAccount, pair, initialBalance, TestHelper.getCryptoMaxFee(),
            DEFAULT_SEND_RECV_RECORD_THRESHOLD, DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    List<Key> keyList = Collections.singletonList(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    Common.addKeyMap(pair, pubKey2privKeyMap);
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    Transaction signTransaction = TransactionSigner
        .signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);
    //  Transaction signTransaction = TransactionSigner.signTransaction(transaction, privKey);
    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction, 1);
    System.out.println("createAccountFee ===> " + createAccountFee);
    return signTransaction;
  }

  public static Transaction createAccountWithFeeAndThresholds(AccountID payerAccount,
      AccountID nodeAccount, KeyPair pair, long initialBalance, KeyPair payerKeyPair,
      long sendRecordThreshold, long receiveRecordThreshold) throws Exception {

    Transaction transaction = TestHelper
        .createAccount(payerAccount, nodeAccount, pair, initialBalance, 0,
            sendRecordThreshold, receiveRecordThreshold);
    List<Key> keyList = Collections.singletonList(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    Common.addKeyMap(pair, pubKey2privKeyMap);
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    Transaction signTransaction = TransactionSigner
        .signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);
    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction, 1);
    System.out.println("createAccountFee ===> " + createAccountFee);
    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount, pair, initialBalance,
            createAccountFee, sendRecordThreshold, receiveRecordThreshold);
    signTransaction =  TransactionSigner
        .signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);
    return signTransaction;
  }

  public static Transaction createAccountWithFeeAutoRenewCheck(AccountID payerAccount,
      AccountID nodeAccount,
      KeyPair pair, long initialBalance, List<PrivateKey> privKey) throws Exception {

    Transaction transaction = TestHelper
        .createAccountAutoRenewCheck(payerAccount, nodeAccount, pair, initialBalance, 0);
    Transaction signTransaction = TransactionSigner.signTransaction(transaction, privKey);
    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction, privKey.size());
    System.out.println("createAccountFee ===> " + createAccountFee);
    transaction = TestHelper
        .createAccountAutoRenewCheck(payerAccount, nodeAccount, pair, initialBalance,
            createAccountFee);
    signTransaction = TransactionSigner.signTransaction(transaction, privKey);
    return signTransaction;


  }

  public static Transaction createAccountWithFeeZeroThreshold(AccountID payerAccount,
      AccountID nodeAccount, KeyPair pair, long initialBalance, KeyPair privKey) throws Exception {
    List<Key> keyList = Collections.singletonList(Common.PrivateKeyToKey(privKey.getPrivate()));
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    Common.addKeyMap(privKey, pubKey2privKeyMap);

    Transaction transaction = TestHelper
        .createAccountZeroThreshold(payerAccount, nodeAccount, pair, initialBalance, 0);
    Transaction signTransaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);
    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction, 1) * 2;
    System.out.println("createAccountFee ===> " + createAccountFee);
    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount, pair, initialBalance, createAccountFee,
            0, 0);
    signTransaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);
    return signTransaction;


  }

  public static Transaction createAccount(AccountID payerAccount, AccountID nodeAccount,
      KeyPair pair, long initialBalance, long transactionFee, long defaultSendRecordThreshold,
      long defaultRecvRecordThreshold) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    List<Key> keyList = Collections.singletonList(key);

    boolean generateRecord = true;
    String memo = "Create Account Test";
    boolean receiverSigRequired = false;
    CustomPropertiesSingleton propertyWrapper = CustomPropertiesSingleton.getInstance();
    Duration autoRenewPeriod = RequestBuilder.getDuration(propertyWrapper.getAccountDuration());

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, defaultSendRecordThreshold,
            defaultRecvRecordThreshold, receiverSigRequired, autoRenewPeriod);


  }

  public static Transaction createAccountAutoRenewCheck(AccountID payerAccount,
      AccountID nodeAccount,
      KeyPair pair, long initialBalance, long transactionFee) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
//		  String pubKeyStr = Hex.encodeHexString(pubKey);
//		  Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    List<Key> keyList = Collections.singletonList(key);

    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    long receiveRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    boolean receiverSigRequired = false;
    Duration autoRenewPeriod = RequestBuilder.getDuration(1000000002);

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);


  }

  public static Transaction createAccountZeroThreshold(AccountID payerAccount,
      AccountID nodeAccount,
      KeyPair pair, long initialBalance, long transactionFee) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
//		  String pubKeyStr = Hex.encodeHexString(pubKey);
//		  Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    List<Key> keyList = Collections.singletonList(key);

    boolean generateRecord = false;
    String memo = "Create Account Test";
    long sendRecordThreshold = 0l;
    long receiveRecordThreshold = 0l;
    boolean receiverSigRequired = false;
    Duration autoRenewPeriod = RequestBuilder.getDuration(TX_DURATION);

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);


  }

  public static Transaction createAccountWallet(AccountID payerAccount, AccountID nodeAccount,
      String pubKeyStr, long initialBalance) throws DecoderException {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    //		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
//		String pubKeyStr = Hex.encodeHexString(pubKey);
//		  Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    byte[] bytes = HexUtils.hexToBytes(pubKeyStr);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = TestHelper.getCryptoMaxFee();
    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = 999;
    long receiveRecordThreshold = 999;
    boolean receiverSigRequired = false;
    Duration autoRenewPeriod = RequestBuilder.getDuration(
        CustomPropertiesSingleton.getInstance().getAccountDuration());

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
  }

  /**
   * Gets the transaction receipt.
   *
   * @return the transaction receipt
   */
  public static TransactionReceipt getTxReceipt(TransactionID transactionID,
      CryptoServiceBlockingStub stub, Logger log, String host)
      throws InvalidNodeTransactionPrecheckCode {
    Query query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
        .build();

    TransactionReceipt rv;
    Response transactionReceipts = fetchReceipts(query, stub, log, host);
    rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
    return rv;
  }

  /**
   * Gets the transaction receipt.
   *
   * @return the transaction receipt
   */
  public static TransactionReceipt getTxReceipt(TransactionID transactionID,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub)
      throws InvalidNodeTransactionPrecheckCode {
    log.info("getTxReceipt--transactionID : " + TextFormat.shortDebugString(transactionID));
    return getTxReceipt(transactionID, stub, null, "localhost");
  }

  public static TransactionRecord getFastTxRecord(TransactionID transactionID,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub) {
    Query query = Query.newBuilder().setTransactionGetFastRecord(
        RequestBuilder.getFastTransactionRecordQuery(transactionID, ResponseType.ANSWER_ONLY))
        .build();

    return stub.getFastTransactionRecord(query).getTransactionGetFastRecord()
        .getTransactionRecord();
  }

  public static Response getCryptoGetAccountInfo(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount,
      KeyPair payerKeyPair, AccountID nodeAccount) throws Exception {

    // first get the fee for getting the account info

    long costForQuery = FeeClient.getCostForGettingAccountInfo();
    System.out.println(costForQuery + " :: is the cost for query");
    Response response = executeAccountInfoQuery(stub, accountID, payerAccount, payerKeyPair,
        nodeAccount, costForQuery, ResponseType.COST_ANSWER);

    if (response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode()
        == ResponseCodeEnum.OK) {
      long getAcctFee = response.getCryptoGetInfo().getHeader().getCost();
      response = executeAccountInfoQuery(stub, accountID, payerAccount, payerKeyPair,
          nodeAccount,
          getAcctFee, ResponseType.ANSWER_ONLY);
    }
    return response;
  }

  public static Response executeAccountInfoQuery(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount, KeyPair payerKeyPair,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerKeyPair, nodeAccount,
        payerAccount, payerKeyPair, nodeAccount, costForQuery);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, transferTransaction, responseType);
    return stub.getAccountInfo(cryptoGetInfoQuery);
  }

  public static Response getCryptoGetBalance(CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount,
      KeyPair payerAccountKey, AccountID nodeAccount) throws Exception {

    long costForQuery = FeeClient.getFeeByID(HederaFunctionality.CryptoGetAccountBalance) + 50000;
    System.out.println(costForQuery + " :: is the cost for query");
    Response response = executeGetBalanceQuery(stub, accountID, payerAccount, payerAccountKey,
        nodeAccount, costForQuery, ResponseType.COST_ANSWER);

    if (response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode()
        == ResponseCodeEnum.OK) {
      long getAcctFee = response.getCryptogetAccountBalance().getHeader().getCost();
      response = executeGetBalanceQuery(stub, accountID, payerAccount, payerAccountKey,
          nodeAccount, getAcctFee, ResponseType.ANSWER_ONLY);
    }
    return response;
  }

  private static Response executeGetBalanceQuery(CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, costForQuery);
    Query getBalanceQuery = RequestBuilder
        .getCryptoGetBalanceQuery(accountID, transferTransaction, responseType);
    return stub.cryptoGetBalance(getBalanceQuery);
  }

  public static Response getTxRecordByTxID(CryptoServiceBlockingStub stub,
      TransactionID transactionID, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount) throws Exception {

    long costForQuery = FeeClient.getCostForGettingTxRecord();
    System.out.println(costForQuery + " :: is the cost for query");
    Response response = executeTxRecordByTxID(stub, transactionID, payerAccount, payerAccountKey,
        nodeAccount, costForQuery, ResponseType.COST_ANSWER);

    if (response.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode()
        == ResponseCodeEnum.OK) {
      long getAcctFee = response.getTransactionGetRecord().getHeader().getCost();
      response = executeTxRecordByTxID(stub, transactionID, payerAccount, payerAccountKey,
          nodeAccount, getAcctFee, ResponseType.ANSWER_ONLY);
    }
    return response;
  }

  private static Response executeTxRecordByTxID(CryptoServiceBlockingStub stub,
      TransactionID transactionID, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, costForQuery);
    Query query = RequestBuilder.getTransactionGetRecordQuery(transactionID, transferTransaction,
        responseType);
    return stub.getTxRecordByTxID(query);
  }

  public static Response getAccountRecords(CryptoServiceBlockingStub stub,
      AccountID accountId, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount) throws Exception {

//    long costForQuery = FeeClient.getFeeByID(HederaFunctionality.CryptoGetAccountRecords);
    long costForQuery = TestHelper.getCryptoMaxFee();
    System.out.println(costForQuery + " :: is the cost for query");
    Response response = executeGetAccountRecords(stub, accountId, payerAccount, payerAccountKey,
        nodeAccount, costForQuery, ResponseType.COST_ANSWER);

    if (response.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode()
        == ResponseCodeEnum.OK) {
      long getAcctFee = response.getCryptoGetAccountRecords().getHeader().getCost();
      response = executeGetAccountRecords(stub, accountId, payerAccount, payerAccountKey,
          nodeAccount, getAcctFee, ResponseType.ANSWER_ONLY);
    }
    return response;
  }

  private static Response executeGetAccountRecords(CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, costForQuery);
    Query query = RequestBuilder.getAccountRecordsQuery(accountID, transferTransaction,
        responseType);
    return stub.getAccountRecords(query);
  }

  public static Response getAccountLiveHash(CryptoServiceBlockingStub stub,
      AccountID accountId, byte[] hash, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount) throws Exception {

    long costForQuery = FeeClient.getFeeByID(HederaFunctionality.CryptoGetLiveHash);
    System.out.println(costForQuery + " :: is the cost for query");
    Response response = executeGetAccountLiveHash(stub, accountId, hash, payerAccount, payerAccountKey,
        nodeAccount, costForQuery, ResponseType.COST_ANSWER);

    if (response.getCryptoGetLiveHash().getHeader().getNodeTransactionPrecheckCode()
        == ResponseCodeEnum.OK) {
      long getAcctFee = response.getCryptoGetLiveHash().getHeader().getCost();
      response = executeGetAccountLiveHash(stub, accountId, hash, payerAccount, payerAccountKey,
          nodeAccount, getAcctFee, ResponseType.ANSWER_ONLY);
    }
    return response;
  }

  private static Response executeGetAccountLiveHash(CryptoServiceBlockingStub stub,
      AccountID accountID, byte[] hash, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey, nodeAccount,
        payerAccount, payerAccountKey, nodeAccount, costForQuery);
    Query query = RequestBuilder.getAccountLiveHashQuery(accountID, hash, transferTransaction,
        responseType);
    return stub.getLiveHash(query);
  }

  public static Transaction updateAccount(AccountID accountID, AccountID payerAccount,
      PrivateKey payerAccountKey, AccountID nodeAccount, Duration autoRenew) {

    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    long nodeAccountNum = nodeAccount.getAccountNum();
    long payerAccountNum = payerAccount.getAccountNum();
    return RequestBuilder.getAccountUpdateRequest(accountID, payerAccountNum, 0l,
        0l, nodeAccountNum, 0l,
        0l, TestHelper.getCryptoMaxFee(), startTime,
        transactionDuration, true, "Update Account",
        autoRenew);


  }

  public static Transaction deleteAccount(AccountID accountID, AccountID trasferAccountID,
      AccountID payerAccount,
      PrivateKey payerAccountKey, AccountID nodeAccount) {
    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    long nodeAccountNum = nodeAccount.getAccountNum();
    long payerAccountNum = payerAccount.getAccountNum();
    return RequestBuilder.getAccountDeleteRequest(accountID, trasferAccountID, payerAccountNum, 0l,
        0l, nodeAccountNum, 0l,
        0l, TestHelper.getCryptoMaxFee(), startTime,
        transactionDuration, true, "Delete Account");
  }

  public static Transaction createFile(long payerAccountNum,
      Long nodeAccountNum, long transactionFee,
      boolean generateRecord, String memo,
      ByteString fileData, Timestamp fileExpirationTime) {

    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    List<Key> waclKeyList = new ArrayList<>();
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    Key waclKey = Key.newBuilder()
        .setEd25519(ByteString.copyFrom(pair.getPublic().toString().getBytes())).build();
    waclKeyList.add(waclKey);

    return RequestBuilder.getFileCreateBuilder(payerAccountNum, 0l, 0l, nodeAccountNum, 0l, 0l,
        transactionFee, startTime, transactionDuration, generateRecord, memo,
        fileData,
        fileExpirationTime, waclKeyList);
  }


  public static Transaction createAccountmultiSig(AccountID payerAccount, AccountID nodeAccount,
      List<KeyPair> listKeyPairs, long initialBalance, PrivateKey genesisPrivateKey,
      long renewalPeriod)
      throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    List<Key> keyList = new ArrayList<>();
    int i = listKeyPairs.size();
    for (int j = 0; j < i; j++) {
      byte[] pubKey = ((EdDSAPublicKey) listKeyPairs.get(j).getPublic()).getAbyte();
//			String pubKeyStr = Hex.encodeHexString(pubKey);
//			Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
      Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
      keyList.add(j, key);
    }
    long transactionFee = 10l;
    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = 1000000l;
    long receiveRecordThreshold = 1000000l;
    boolean receiverSigRequired = false;
    Duration autoRenewPeriod = RequestBuilder.getDuration(renewalPeriod);

    Transaction transaction = RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
    transaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
// Since Single Genesis Key is used
    transactionFee = FeeClient.getCreateAccountFee(transaction, 1);

    transaction = RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
    return TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));


  }

  public static Properties getApplicationProperties() {
    Properties prop = new Properties();
    InputStream input;
    try {
      String fileName = "src/main/resource/application.properties";
      File checkFile = new File(fileName);
      if(!checkFile.exists()) {
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        input = new FileInputStream(rootPath + "application.properties");
        log.info("In IF rootPath: "+rootPath);
      } else {
          log.info("In Else fileName: "+fileName);
        input = new FileInputStream(fileName);
      }
      prop.load(input);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return prop;
  }

  public static String getStartUpFile() {
    if(properties == null || properties.size() <=0) {
      properties = TestHelper.getApplicationProperties();
    }
    log.info("properties: "+properties);
    return properties.getProperty("startUpFile");
  }
  /**
   * Gets the application properties as a more friendly CustomProperties object.
   */
  public static CustomProperties getApplicationPropertiesNew() {
    String fileName = "src/main/resource/application.properties";
    File checkFile = new File(fileName);
    if(checkFile.exists()) {
      return new CustomProperties(fileName, false);
    } else {
      String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
      return new CustomProperties(rootPath+"application.properties", false);
    }
  }

  public static Response getFileContent(FileServiceBlockingStub fileStub,
      FileID fileID, AccountID payerAccount,
      KeyPair payerAccountKey, AccountID nodeAccount) throws Exception {
    long getContentFee = TestHelper.getCryptoMaxFee();
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, getContentFee);
    Query fileContentQuery = RequestBuilder
        .getFileContentQuery(fileID, transferTransaction, ResponseType.COST_ANSWER);
    Response response = fileStub.getFileContent(fileContentQuery);
    getContentFee = response.getFileGetContents().getHeader().getCost();
    transferTransaction = createTransferSigMap(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, getContentFee);
    fileContentQuery = RequestBuilder
        .getFileContentQuery(fileID, transferTransaction, ResponseType.ANSWER_ONLY);
    return fileStub.getFileContent(fileContentQuery);
  }

  // For getting the initial files, exchange rate and fee schedule.
  public static Response getFileContentInitial(FileServiceBlockingStub fileStub,
      FileID fileID, AccountID payerAccount,
      KeyPair payerAccountKey, AccountID nodeAccount) throws Exception {
    long getContentFee = TestHelper.getFileMaxFee();
    Transaction transferTransaction = createTransferSigMapInitial(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, getContentFee);
    Query fileContentQuery = RequestBuilder
        .getFileContentQuery(fileID, transferTransaction, ResponseType.COST_ANSWER);
    Response response = fileStub.getFileContent(fileContentQuery);
    getContentFee = response.getFileGetContents().getHeader().getCost();
    transferTransaction = createTransferSigMapInitial(payerAccount, payerAccountKey,
        nodeAccount, payerAccount, payerAccountKey, nodeAccount, getContentFee);
    fileContentQuery = RequestBuilder
        .getFileContentQuery(fileID, transferTransaction, ResponseType.ANSWER_ONLY);
    return fileStub.getFileContent(fileContentQuery);
  }

  public static Query getTxRecordByTxId(TransactionID transactionID, AccountID payerAccount,
      KeyPair payerAccountKey, AccountID nodeAccount, long getTxRecordFee,
      ResponseType reponseType) throws Exception {

    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey, nodeAccount,
        payerAccount, payerAccountKey, nodeAccount, getTxRecordFee);

    return RequestBuilder
        .getTransactionGetRecordQuery(transactionID, transferTransaction, reponseType);
  }

  public static Query getTxRecordByAccountId(AccountID accountID, AccountID payerAccount,
      KeyPair payerAccountKeyPair, AccountID nodeAccount, long getTxRecordFee,
      ResponseType responsetype) throws Exception {
    getTxRecordFee = TestHelper.getCryptoMaxFee();
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKeyPair,
        nodeAccount, payerAccount, payerAccountKeyPair, nodeAccount, getTxRecordFee);
    return RequestBuilder.getAccountRecordsQuery(accountID, transferTransaction, responsetype);
  }

  public static Query getTxRecordByContractId(ContractID contractID, AccountID payerAccount,
      KeyPair payerAccountKeyPair, AccountID nodeAccount, long getTxRecordFee,
      ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKeyPair, nodeAccount,
        payerAccount, payerAccountKeyPair, nodeAccount, getTxRecordFee);
    return RequestBuilder.getContractRecordsQuery(contractID, transferTransaction, responseType);
  }

  public static Transaction createTransfer(AccountID fromAccount, PrivateKey fromKey,
      AccountID toAccount, AccountID payerAccount, PrivateKey payerAccountKey,
      AccountID nodeAccount, long amount) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 0, timestamp, transactionDuration,
        false, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    List<PrivateKey> privKeysList = new ArrayList<>();
    privKeysList.add(payerAccountKey);
    privKeysList.add(fromKey);

    Transaction signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);

    long transferFee = 0;
    try {
      transferFee = FeeClient.getTransferFee(signedTx, privKeysList.size());
    } catch (Exception e) {
      e.printStackTrace();
    }
    transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
        transactionDuration, false,
        "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
        amount);

    signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);
    return signedTx;
  }

  public static Transaction createTransferSigMap(AccountID fromAccount, KeyPair fromKeyPair,
      AccountID toAccount, AccountID payerAccount, KeyPair payerKeyPair,
      AccountID nodeAccount, long amount) throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 0, timestamp, transactionDuration,
        false, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    keyList.add(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    if (!payerKeyPair.equals(fromKeyPair)) {
      keyList.add(Common.PrivateKeyToKey(fromKeyPair.getPrivate()));
      Common.addKeyMap(fromKeyPair, pubKey2privKeyMap);
    }

    Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(
        transferTx, keyList, pubKey2privKeyMap);

    long transferFee = TestHelper.getCryptoMaxFee();
    transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
        transactionDuration, false,
        "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
        amount);

    signedTx = TransactionSigner.signTransactionComplexWithSigMap(
        transferTx, keyList, pubKey2privKeyMap);
    return signedTx;
  }

  //  For getting the initial files, exchange rate and fee schedule. Needs to offer a large
  // transaction fee, because the exchange rate is not yet known.
  public static Transaction createTransferSigMapInitial(AccountID fromAccount, KeyPair fromKeyPair,
      AccountID toAccount, AccountID payerAccount, KeyPair payerKeyPair,
      AccountID nodeAccount, long amount) throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), FeeClient.getMaxFee(), timestamp,
        transactionDuration, false, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    keyList.add(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    if (!payerKeyPair.equals(fromKeyPair)) {
      keyList.add(Common.PrivateKeyToKey(fromKeyPair.getPrivate()));
      Common.addKeyMap(fromKeyPair, pubKey2privKeyMap);
    }

    Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(
        transferTx, keyList, pubKey2privKeyMap);
    return signedTx;
  }


  public static Transaction createTransferSigMapBeforeCurrentStartTime(AccountID fromAccount, KeyPair fromKeyPair,
      AccountID toAccount, AccountID payerAccount, KeyPair payerKeyPair,
      AccountID nodeAccount, long amount) throws Exception {
    Timestamp timestamp = ProtoCommonUtils.getCurrentTimestampUTC(-1200);
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 0, timestamp, transactionDuration,
        false, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    keyList.add(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    if (!payerKeyPair.equals(fromKeyPair)) {
      keyList.add(Common.PrivateKeyToKey(fromKeyPair.getPrivate()));
      Common.addKeyMap(fromKeyPair, pubKey2privKeyMap);
    }

    Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(
        transferTx, keyList, pubKey2privKeyMap);

    long transferFee = 0;
    try {
      transferFee = FeeClient.getTransferFee(signedTx, keyList.size());
    } catch (Exception e) {
      e.printStackTrace();
    }
    transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
        transactionDuration, false,
        "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
        amount);

    signedTx = TransactionSigner.signTransactionComplexWithSigMap(
        transferTx, keyList, pubKey2privKeyMap);
    return signedTx;
  }

  /**
   * Fetches the receipts, wait if necessary.
   *
   * @return the response
   */
  public static Response fetchReceipts(final Query query, CryptoServiceBlockingStub cstub,
      final Logger log, String host)
      throws InvalidNodeTransactionPrecheckCode {
    long start = System.currentTimeMillis();
    if (log != null) {
      log.debug("GetTxReceipt: query=" + query);
    }

    Response transactionReceipts = cstub.getTransactionReceipts(query);
    Assert.assertNotNull(transactionReceipts);
    ResponseCodeEnum precheckCode = transactionReceipts.getTransactionGetReceipt().getHeader()
        .getNodeTransactionPrecheckCode();
    if (!precheckCode.equals(ResponseCodeEnum.OK) && !precheckCode.equals(ResponseCodeEnum.BUSY)) {
      throw new InvalidNodeTransactionPrecheckCode("Invalid node transaction precheck code " +
          precheckCode.name() +
          " from getTransactionReceipts");
    }

    int cnt = 0;
    String status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
    while (cnt < MAX_RECEIPT_RETRIES &&
        (status.equals(ResponseCodeEnum.UNKNOWN.name()) || precheckCode == ResponseCodeEnum.BUSY)) {
      long napMillis = RETRY_FREQ_MILLIS;
      if (isExponentialBackoff) {
        napMillis = getExpWaitTimeMillis(cnt, MAX_RETRY_FREQ_MILLIS);
      }
      CommonUtils.napMillis(napMillis);
      cnt++;
      if (log != null) {
        log.info("Retrying getTransactionReceipts call");
      }
      try {
        transactionReceipts = cstub.getTransactionReceipts(query);
        status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
        precheckCode = transactionReceipts.getTransactionGetReceipt().getHeader()
            .getNodeTransactionPrecheckCode();
      } catch (StatusRuntimeException e) {
        if (log != null) {
          log.warn("getTransactionReceipts: RPC failed!", e);
        }
        String errorMsg = e.getStatus().getDescription();
        if (e.getStatus().equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg
            .contains("max_age")) {
          Channel channel = cstub.getChannel();
          if (channel == null) {
            Properties properties = TestHelper.getApplicationProperties();
            int port = Integer.parseInt(properties.getProperty("port"));
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
            cstub = CryptoServiceGrpc.newBlockingStub(channel);
          }
        }
        status = ResponseCodeEnum.UNKNOWN.name();
      }
    }

    long elapse = System.currentTimeMillis() - start;
    long secs = elapse / 1000;
    long milliSec = elapse % 1000;
    String msg =
        "GetTxReceipt: took = " + secs + " second " + milliSec + " millisec; retries = " + cnt
            + "; receipt=" + transactionReceipts;
    if (!status.equals(ResponseCodeEnum.SUCCESS.name())) {
      if (log != null) {
        log.warn(msg);
      }
    } else {
      if (log != null) {
        log.info(msg);
      }
    }
    return transactionReceipts;
  }

  /**
   * Exponential wait time in millis capped by a max wait time.
   *
   * @param retries num of retries
   * @param maxWaitMillis beyond which, the wait time will be this value
   * @return the wait time in millis
   */
  private static long getExpWaitTimeMillis(int retries, long maxWaitMillis) {
    double rv = 0;
    rv = (Math.pow(2, retries) * RETRY_FREQ_MILLIS);

    if (rv > maxWaitMillis) {
      rv = maxWaitMillis;
    }

    long waitMillis = (long) rv;

    return waitMillis;
  }

  /**
   * Gets the current UTC timestamp with default winding back seconds.
   */
  public synchronized static Timestamp getDefaultCurrentTimestampUTC() {
    Timestamp rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
    if (rv.getNanos() == lastNano) {
      try {
        Thread.sleep(0, 1);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
    }

    lastNano = rv.getNanos();

    return rv;
  }


  public static Transaction getContractCallRequest(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp,
      Duration txDuration, long gas, ContractID contractId,
      ByteString functionData, long value,
      List<PrivateKey> keys) throws Exception {

    Transaction transaction = RequestBuilder
        .getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, gas, contractId, functionData, value);

    transaction = TransactionSigner.signTransaction(transaction, keys);

    transactionFee = FeeClient.getCostContractCallFee(transaction, keys.size());

    transaction = RequestBuilder
        .getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, gas, contractId, functionData, value);

    return TransactionSigner.signTransaction(transaction, keys);

  }

  public static Transaction getContractCallRequestSigMap(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp,
      Duration txDuration, long gas, ContractID contractId,
      ByteString functionData, long value,
      KeyPair payerKeyPair) throws Exception {

    List<Key> keyList = Collections.singletonList(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);

    Transaction transaction = RequestBuilder
        .getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, gas, contractId, functionData, value);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);

    transactionFee = FeeClient.getCostContractCallFee(transaction, keyList.size());

    transaction = RequestBuilder
        .getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, gas, contractId, functionData, value);

    return TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);

  }

  public static Transaction getCreateContractRequest(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, long gas, FileID fileId,
      ByteString constructorParameters, long initialBalance,
      Duration autoRenewalPeriod, List<PrivateKey> keys, String contractMemo) throws Exception {
    return getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum,
        nodeAccountNum, nodeRealmNum, nodeShardNum,
        transactionFee, timestamp, txDuration,
        generateRecord, txMemo, gas, fileId,
        constructorParameters, initialBalance,
        autoRenewalPeriod, keys, contractMemo, null);
  }

  public static Transaction getCreateContractRequest(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, long gas, FileID fileId,
      ByteString constructorParameters, long initialBalance,
      Duration autoRenewalPeriod, List<PrivateKey> keys, String contractMemo,
      Key adminKey) throws Exception {

    Transaction transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminKey);

    transaction = TransactionSigner.signTransaction(transaction, keys);

    transactionFee = FeeClient.getContractCreateFee(transaction, keys.size());

    transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminKey);

    transaction = TransactionSigner.signTransaction(transaction, keys);
    return transaction;
  }

  public static Transaction getCreateContractRequestSigMap(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, long gas, FileID fileId,
      ByteString constructorParameters, long initialBalance,
      Duration autoRenewalPeriod, KeyPair payerKeyPair, String contractMemo,
      Key adminPubKey) throws Exception {

    List<Key> keyList = Collections.singletonList(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);

    Transaction transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminPubKey);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);

    transactionFee = FeeClient.getContractCreateFee(transaction, keyList.size());

    transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminPubKey);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);
    return transaction;
  }

  public static Transaction getCreateContractRequestSigMap(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, long gas, FileID fileId,
      ByteString constructorParameters, long initialBalance,
      Duration autoRenewalPeriod, List<KeyPair> keyPairs, String contractMemo,
      Key adminPubKey) throws Exception {

    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    for (KeyPair pair : keyPairs) {
      keyList.add(Common.PrivateKeyToKey(pair.getPrivate()));
      Common.addKeyMap(pair, pubKey2privKeyMap);
    }

    Transaction transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminPubKey);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);

    transactionFee = FeeClient.getContractCreateFee(transaction, keyList.size());

    transaction = RequestBuilder
        .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
            txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
            autoRenewalPeriod, contractMemo, adminPubKey);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(
        transaction, keyList, pubKey2privKeyMap);
    return transaction;
  }

  public static long getFileMaxFee() {
    return CryptoServiceTest.getUmbrellaProperties().getLong("fileMaxFee", 800_000_000L);
  //  return 800_000_000L;
  }
  public static long getContractMaxFee() {
    return CryptoServiceTest.getUmbrellaProperties().getLong("contractMaxFee", 60_00_000_000L);
 //   return 6000_000_000L;
  }
  public static long getCryptoMaxFee() {
    return CryptoServiceTest.getUmbrellaProperties().getLong("cryptoMaxFee", 5_00_000_000L);
//    return 500_000_000L;
  }

  public static int getErrorReturnCode() {
    return CryptoServiceTest.getUmbrellaProperties().getInt("errorReturnCode", -1);
  }


  public static Transaction getSystemDeleteTx(PrivateKey payerPrivateKey, AccountID payerAccount,
      AccountID defaultNodeAccount, FileID fileID, long expireTimeInSeconds) throws Exception {

    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    // prepare tx Body
    TransactionBody.Builder body = RequestBuilder
        .getTxBodyBuilder(30, startTime, transactionDuration,
            true, "System Delete", payerAccount, defaultNodeAccount);

    // prepare specif request body
    SystemDeleteTransactionBody systemDeleteTransactionBody =
        RequestBuilder.getSystemDeleteTransactionBody(fileID, expireTimeInSeconds);
    body.setSystemDelete(systemDeleteTransactionBody);
    ByteString bodyBytes = ByteString.copyFrom(body.build().toByteArray());

    // step 1 : create tx
    Transaction transaction = Transaction.newBuilder().setBodyBytes(bodyBytes)
        .setSigs(SignatureList.getDefaultInstance()).build();
    // step 2 : sign tx by payer account's private key
    Transaction signTransaction = TransactionSigner.signTransaction(transaction,
        Collections.singletonList(payerPrivateKey));
    // step 3 calculate fee after signing tx
    long systemDeleteFee = FeeClient.getSystemDeleteFee(signTransaction, 1);
    // step 4 setting fee to body
    body.setTransactionFee(systemDeleteFee);
    System.out.println("SystemDelete Fee ===> " + systemDeleteFee);
    // step 5  sign again after fee calculation
    transaction = Transaction.newBuilder()
        .setBodyBytes(ByteString.copyFrom(body.build().toByteArray()))
        .setSigs(SignatureList.getDefaultInstance()).build();
    signTransaction = TransactionSigner.signTransaction(transaction,
        Collections.singletonList(payerPrivateKey));
    return signTransaction;
  }

  public static Response getFileInfo(FileServiceBlockingStub stub,
      FileID fileID, AccountID payerAccount, KeyPair payerAccountKey, AccountID nodeAccount)
      throws Exception {

    // first get the fee for getting the file info
    long feeForFileInfoCost = FeeClient.getFeeByID(HederaFunctionality.FileGetInfo);
    Response response = executeFileInfoQuery(stub, fileID, payerAccount, payerAccountKey,
        nodeAccount, feeForFileInfoCost, ResponseType.COST_ANSWER);

    long getFileFee = response.getFileGetInfo().getHeader().getCost();
    response = executeFileInfoQuery(stub, fileID, payerAccount, payerAccountKey, nodeAccount,
        getFileFee, ResponseType.ANSWER_ONLY);
    return response;
  }

  private static Response executeFileInfoQuery(FileServiceBlockingStub stub,
      FileID fileId, AccountID payerAccount, KeyPair payerAccountKey,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey, nodeAccount,
        payerAccount, payerAccountKey, nodeAccount, costForQuery);
    Query fileGetInfoBuilder = RequestBuilder
        .getFileGetInfoBuilder(transferTransaction, fileId, responseType);
    return stub.getFileInfo(fileGetInfoBuilder);
  }

  public static Transaction getDeleteContractRequest(AccountID payer,
      AccountID node,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, ContractID contractId, AccountID transferAccount,
      ContractID transferContract, List<PrivateKey> keys) throws Exception {

    Transaction transaction = RequestBuilder
        .getDeleteContractRequest(payer, node, transactionFee, timestamp, txDuration, contractId,
            transferAccount, transferContract, generateRecord, txMemo);

    transaction = TransactionSigner.signTransaction(transaction, keys);
    return transaction;
  }

  public static Transaction getDeleteContractRequestSigMap(AccountID payer,
      AccountID node,
      long transactionFee, Timestamp timestamp, Duration txDuration,
      boolean generateRecord, String txMemo, ContractID contractId, AccountID transferAccount,
      ContractID transferContract, List<KeyPair> keyPairs) throws Exception {
    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    for (KeyPair pair : keyPairs) {
      keyList.add(Common.PrivateKeyToKey(pair.getPrivate()));
      Common.addKeyMap(pair, pubKey2privKeyMap);
    }

    Transaction transaction = RequestBuilder
        .getDeleteContractRequest(payer, node, transactionFee, timestamp, txDuration, contractId,
            transferAccount, transferContract, generateRecord, txMemo);

    transaction = TransactionSigner.signTransactionComplexWithSigMap(transaction, keyList,
        pubKey2privKeyMap);
    return transaction;
  }

  public static ExchangeRate getExchangeRate() {
    long expiryTime = Instant.now().getEpochSecond() + 10000000;
    ExchangeRateSet exchangeRateSet = RequestBuilder
        .getExchangeRateSetBuilder(1, 12, expiryTime, 1, 12, expiryTime);
    return exchangeRateSet.getCurrentRate();
  }

  public static TransactionResponse addLiveHash(Builder txBodyBuilder,
      List<PrivateKey> genesisPrivateKey, AccountID accountIdBuild, KeyList keyList, byte[] hash,
      CryptoServiceBlockingStub stub) {
    Duration claimDuration = RequestBuilder
        .getDuration(Instant.now(Clock.systemUTC()).getEpochSecond() + 1000000L);
    LiveHash claim = RequestBuilder.getLiveHash(accountIdBuild, claimDuration, keyList, hash);
    CryptoAddLiveHashTransactionBody addLiveHashTransactionBody = CryptoAddLiveHashTransactionBody
        .newBuilder().setLiveHash(claim).build();
    txBodyBuilder.setCryptoAddLiveHash(addLiveHashTransactionBody);
    ByteString bodyBytes = ByteString.copyFrom(txBodyBuilder.build().toByteArray());
    Transaction addLiveHashTx = Transaction.newBuilder().setBodyBytes(bodyBytes)
        .setSigs(SignatureList.newBuilder().getDefaultInstanceForType()).build();
    Transaction signedAddLiveHashTx = TransactionSigner
        .signTransaction(addLiveHashTx, genesisPrivateKey);
    return stub.addLiveHash(signedAddLiveHashTx);
  }

  public static void genWacl(int numKeys, List<Key> waclPubKeyList,
      List<PrivateKey> waclPrivKeyList) {
    for (int i = 0; i < numKeys; i++) {
      KeyPair pair = new KeyPairGenerator().generateKeyPair();
      byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
      Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
      waclPubKeyList.add(waclKey);
      waclPrivKeyList.add(pair.getPrivate());
    }
  }

}
