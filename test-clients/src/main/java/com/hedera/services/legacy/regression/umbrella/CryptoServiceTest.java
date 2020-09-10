package com.hedera.services.legacy.regression.umbrella;

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
import com.google.protobuf.UInt64Value;
import com.hedera.services.legacy.client.util.Common;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse.FileContents;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.KeyList.Builder;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.client.test.ClientBaseThread;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds crypto related functions for regression tests.
 *
 * @author hua Created on 2018-10-19
 */
public class CryptoServiceTest extends TestHelperComplex {

  private static final Logger log = LogManager.getLogger(CryptoServiceTest.class);
  protected static String testConfigFilePath = "config/umbrellaTest.properties";
  protected static String[] fileTypes = {"txt", "jpg", "pdf", "bin"};
  protected static int[] fileSizesK = {1, 2, 3, 4, 5};
  public static double MAX_REQUESTS_IN_K = 1;
  protected static boolean isRandomSubmission = false;
  protected static boolean isRandomPayer = true;
  protected static int numPayerAccounts = 2;
  protected static AccountID[] payerAccounts = null; // payer accounts, size determined by numCryptoAccounts
  protected static boolean isRandomTransferAccount = true;
  protected static int numTransferAccounts = 2;
  protected static AccountID[] transferAccounts = null;// accounts for transfer's from and to parties, size determined by numTransferAccounts
  public static int K = 1000;
  protected static AccountID[] nodeAccounts = null;
  protected static Set<AccountID> accountsBeingUpdated = new HashSet<>();
  protected static Random rand = new Random();
  public static final long ADDRESS_FILE_ACCOUNT_NUM = 101;
  protected static Map<AccountID, NodeAddress> nodeID2Ip = new HashMap<>();
  protected static Map<AccountID, FileServiceBlockingStub> nodeID2Stub = new HashMap<>();
  protected static Map<AccountID, Integer> nodeID2Port = new HashMap<>();
  protected static int MAX_TRANSFER_AMOUNT = 100;
  protected int COMPLEX_KEY_SIZE = 3;
  protected int COMPLEX_KEY_THRESHOLD = 2;
  protected static String[] accountKeyTypes = null;
  protected static String[] signatureFormat = null;
  public static String CONFIG_LIST_SEPARATOR = ",\\s*";
  protected static boolean receiverSigRequired = true;
  protected static Set<AccountID> recvSigRequiredAccounts = new HashSet<>();
  protected static boolean getReceipt = true; //flag whether or not to get receipts after a transfer transaction
  protected static boolean useSystemAccountAsPayer = false; //flag whether or not to use system accounts (account number under 100 excluding genesis and node accounts).
  protected static int MAX_BUSY_RETRIES = 10;
  protected static int BUSY_RETRY_MS = 200;

  protected static enum SUPPORTE_KEY_TYPES {
    single, keylist, thresholdKey
  }

//protected static long DEFAULT_INITIAL_ACCOUNT_BALANCE = 2000_00_000_000L; //working
  protected static long DEFAULT_INITIAL_ACCOUNT_BALANCE = getUmbrellaProperties().getLong("initialAccountBalance", 2000_00_000_000L);
  protected static long SMALL_ACCOUNT_BALANCE_FACTOR = getUmbrellaProperties().getLong("smallAccountBalanceFactor", 2000L);
  public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
  public static long DAY_SEC = 24 * 60 * 60; // secs in a day
  protected static String[] files = {"1K.txt", "overview-frame.html"};
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  protected String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";

  protected static ManagedChannel channel = null;
  protected static int transactionMaxBytes = Integer
      .parseInt(getApplicationProperties().getProperty("transactionMaxBytes", "8192"));//8192;
  protected static long specialAccountNum = Long
      .parseLong(getApplicationProperties().getProperty("specialAccountNum", "50"));
  protected static long genesisAccountNum = Long
      .parseLong(getApplicationProperties().getProperty("genesisAccountNum", "2"));
  protected long accountDuration;
  protected long fileDuration;
  protected long contractDuration;
  protected PrivateKey genesisPrivateKey;
  protected KeyPair genKeyPair;
  /**
   * // default file service stub that connects to the default listening node
   */
  protected static FileServiceBlockingStub stub = null;

  public static Map<AccountID, Long> localLedgerBalance = new ConcurrentHashMap<>();

  /**
   * default crypto service stub that connects to the default listening node
   */
  protected static CryptoServiceGrpc.CryptoServiceBlockingStub cstub = null;

  protected static Duration transactionDuration = Duration.newBuilder().setSeconds(TX_DURATION_SEC)
      .build();
  public static Map<String, List<AccountKeyListObj>> hederaAccounts = null;
  protected static List<AccountKeyListObj> genesisAccountList;
  protected static AccountID genesisAccountID;
  public static int NUM_WACL_KEYS = 1;
  protected static int WAIT_IN_SEC = 1;
  protected static String host = "localhost";
  protected static int port = 50211;

  /**
   * The account ID of the default listening node
   */
  public static AccountID defaultListeningNodeAccountID = null;

  /**
   * By default, all nodes are listening on the same port, i.e. 50211
   */
  protected static int uniqueListeningPortFlag = 0;

  /**
   * A value of 1 is for production, where all nodes listen on same default port A value of 0 is for
   * development, i.e. running locally
   */
  protected static int productionFlag = 0;

  /**
   * remember the start up genesis key in the case the genesis key is updated and want to revert
   * back
   */
  protected static Key startUpKey = null;

  /**
   * flag whether change the genesis key
   */
  protected boolean changeGenesisKey = false;

  /**
   * maintains the live transaction ids, which are removed when they expire
   */
  protected TransactionIDCache cache = null;

  protected static long QUERY_PAYMENT_AMOUNT = TestHelper.getCryptoMaxFee();

  /**
   * Flag for creating a new channel and stub for single use. Destroyed after the use.
   */
  protected boolean isOneUseChannel = true;

  public CryptoServiceTest(String testConfigFilePath) {
    CryptoServiceTest.testConfigFilePath = testConfigFilePath;
  }

  public CryptoServiceTest() {
  }

  public void setUp() throws Throwable {
    setUp(null, null);
  }

  public void setUp(String host, int port, long nodeAccount, long retryFreq,
      boolean isExponentialBackoff) throws Throwable {
    CryptoServiceTest.host = host;
    CryptoServiceTest.port = port;
    defaultListeningNodeAccountID = AccountID.newBuilder().setAccountNum(nodeAccount).build();
    RETRY_FREQ_MILLIS = retryFreq;
    MAX_RECEIPT_RETRIES = (int) (180000 / RETRY_FREQ_MILLIS) + 1; // total receipt time is 3 minutes
    TestHelper.isExponentialBackoff = isExponentialBackoff;

    getTestConfig();
    readGenesisInfo();
    createStubs();
    cache = TransactionIDCache
        .getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
    nodeID2Stub.put(defaultListeningNodeAccountID, stub);
    nodeAccounts = new AccountID[1];
    nodeAccounts[0] = defaultListeningNodeAccountID;
  }

  public void setUp(String host, Long nodeAccount) throws Throwable {
    readAppConfig(host, nodeAccount);
    getTestConfig();
    readGenesisInfo();
    createStubs();
    cache = TransactionIDCache
        .getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
    nodeID2Stub.put(defaultListeningNodeAccountID, stub);
    nodeAccounts = new AccountID[1];
    nodeAccounts[0] = defaultListeningNodeAccountID;
//    getNodeAccountsFromLedger();
  }

  public static CustomProperties getUmbrellaProperties() {
    File checkFile = new File(testConfigFilePath);
    if(checkFile.exists()) {
      return new CustomProperties(testConfigFilePath, false);
    } else {
      String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
      return new CustomProperties(rootPath+"umbrellaTest.properties", false);
    }
  }
  /**
   * Gets the test config for the tests
   */
  protected void getTestConfig() {
    CustomProperties testProps = getUmbrellaProperties();
    isRandomPayer = Boolean.parseBoolean(testProps.getString("isRandomPayer", "false"));
    isRandomTransferAccount = Boolean
        .parseBoolean(testProps.getString("isRandomTransferAccount", "false"));
    isRandomSubmission = Boolean.parseBoolean(testProps.getString("isRandomSubmission", "false"));
    fileTypes = testProps.getString("fileTypes", "txt,jpg,pdf,bin").split(CONFIG_LIST_SEPARATOR);
    String[] fileSizesKStr = testProps.getString("fileSizesK", "1,10,100,1000")
        .split(CONFIG_LIST_SEPARATOR);
    fileSizesK = parseIntArray(fileSizesKStr);
    MAX_REQUESTS_IN_K = Double.parseDouble(testProps.getString("maxRequestsInK", "1"));
    MAX_TRANSFER_AMOUNT = testProps.getInt("maxTransferAmount", 100);
    numPayerAccounts = testProps.getInt("numCryptoAccounts", 10);
    numTransferAccounts = testProps.getInt("numTransferAccounts", 2);
    accountKeyTypes = testProps.getString("accountKeyType", "single")
        .split(CONFIG_LIST_SEPARATOR);
    changeGenesisKey = Boolean.parseBoolean(testProps.getString("changeGenesisKey", "false"));
    NUM_WACL_KEYS = testProps.getInt("numWaclKeys", 1);

    getReceipt = Boolean.parseBoolean(testProps.getString("getReceipt", "true"));
    SMALL_ACCOUNT_BALANCE_FACTOR = testProps.getLong("smallAccountBalanceFactor", 100000L);
    useSystemAccountAsPayer = Boolean
        .parseBoolean(testProps.getString("useSystemAccountAsPayer", "false"));
    port = testProps.getInt("port", 50211);
    System.out.println("port:" + port);
    this.MAX_BUSY_RETRIES = testProps.getInt("maxBusyRetry", 10);
    this.BUSY_RETRY_MS = testProps.getInt("busyRetrySleep", 50211);
    Properties applicationProperties = getApplicationProperties();
    specialAccountNum = Long
        .parseLong(applicationProperties.getProperty("specialAccountNum", "55"));
    genesisAccountNum = Long
        .parseLong(applicationProperties.getProperty("genesisAccountNum", "2"));
    accountDuration = Long
        .parseLong(applicationProperties.getProperty("ACCOUNT_DURATION", "7890000"));
    fileDuration = Long
        .parseLong(applicationProperties.getProperty("FILE_DURATION", "7890000"));
    contractDuration = Long
        .parseLong(applicationProperties.getProperty("CONTRACT_DURATION", "7890000"));
  }

  /**
   * Gets a non-negative random amount.
   */
  public static long getRandomTransferAmount() {
    long rv = rand.nextInt(MAX_TRANSFER_AMOUNT - 1) + 1;
    return rv;
  }

  /**
   * Gets the genesis account ID.
   */
  public AccountID getGenesisAccountID() {
    return genesisAccountID;
  }

  /**
   * Gets transaction records by account ID.
   *
   * @return list of transaction records
   */
  public List<TransactionRecord> getTransactionRecordsByAccountId(AccountID accountID,
      AccountID payerAccount, AccountID nodeAccountID) throws Throwable {
    log.info("Get Tx records by account Id...");
    long fee = getAccountRecordsCostFee(accountID, payerAccount.getAccountNum(), nodeAccountID.getAccountNum());
    Query query = TestHelperComplex
        .getTxRecordByAccountIdComplex(accountID, payerAccount, nodeAccountID, fee,
            ResponseType.ANSWER_ONLY);
    Response transactionRecord = retryLoopQuery(query, "getAccountRecords");
    Assert.assertEquals(ResponseCodeEnum.OK,
        transactionRecord.getCryptoGetAccountRecords().getHeader()
            .getNodeTransactionPrecheckCode());
    Assert.assertNotNull(transactionRecord.getCryptoGetAccountRecords());
    Assert.assertEquals(accountID, transactionRecord.getCryptoGetAccountRecords().getAccountID());
    List<TransactionRecord> recordList = transactionRecord.getCryptoGetAccountRecords()
        .getRecordsList();
    log.info(
        "Tx Records List for account ID " + accountID.getAccountNum() + " :: " + recordList.size());
    log.info("--------------------------------------");
    return recordList;
  }

  /**
   * Updates an account autoRenew.
   *
   * @param accountID account to be updated
   * @return updated account info.
   */
  public AccountInfo updateAccount(AccountID accountID, AccountID payerAccountID,
      AccountID nodeAccountID)
      throws Throwable {
    Duration autoRenew = RequestBuilder
        .getDuration(CustomPropertiesSingleton.getInstance().getUpdateDurationValue());

    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key accKey = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (accountID.getAccountNum() != 55) {
      keys.add(accKey);
    }
    Transaction updateTx = TestHelperComplex.updateAccount(accountID, payerAccountID,
        nodeAccountID, autoRenew);
    Transaction signUpdate = TransactionSigner
        .signTransactionComplexWithSigMap(updateTx, keys, pubKey2privKeyMap);

    log.info("\n-----------------------------------\nupdateAccount: request = " + signUpdate);
    TransactionResponse response = retryLoopTransaction(signUpdate, "updateAccount");
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
    TransactionID transactionID = body.getTransactionID();
    cache.addTransactionID(transactionID);

    AccountInfo accInfo = null;
    if (getReceipt) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);

      accInfo = getAccountInfo(accountID, payerAccountID, nodeAccountID);
      Assert.assertNotNull(accInfo);
      log.info(accInfo);
      Assert.assertEquals(autoRenew, accInfo.getAutoRenewPeriod());
      log.info("updating successful" + "\n");
    }

    return accInfo;
  }

  /**
   * Creates a large number of accounts
   *
   * @param maxAccounts number of rounds to create accounts
   * @return an array holding the created accounts
   */
  public AccountID[] accountCreatBatch(int maxAccounts, String accountKeyType) throws Throwable {
    AccountID[] rv = new AccountID[maxAccounts];
    for (int i = 0; i < maxAccounts; i++) {
      AccountID payerAccount = genesisAccountID;
      AccountID nodeAccountID = getRandomNodeAccount();
      if (accountKeyType == null || "".equals(accountKeyType)) {
        accountKeyType = getRandomAccountKeyType();
      }
      AccountID accountId = createAccountComplex(payerAccount, nodeAccountID,
          accountKeyType, true, false);
      rv[i] = accountId;
      log.info("\n@@@@ account creation round #" + (i + 1) + ": account = " + accountId);
    }

    return rv;
  }

  public AccountID[] accountCreatBatch(int maxAccounts) throws Throwable {
    return accountCreatBatch(maxAccounts, null);
  }


  /**
   * Create payer accounts.
   */
  public AccountID[] accountCreatBatch4Payer(int maxAccounts) throws Throwable {
    if (useSystemAccountAsPayer) {
      return getSystemAccounts(maxAccounts);
    } else {
      return accountCreatBatch(maxAccounts);
    }
  }

  /**
   * Gets funded system accounts, i.e. those with account number under 100 excluding genesis and
   * node accounts
   *
   * @param maxAccounts number of system accounts to use. the value should be set such that when
   * combined with the genesis and node accounts, the total should not exceed 100. If the limit is
   * exceeded, only accounts under the limit will be used.
   */
  public AccountID[] getSystemAccounts(int maxAccounts) throws Throwable {
    int addressListSize = nodeAccounts.length;
    int existingNumSystemAccounts = 100 - addressListSize - 2;
    int numAccounts = maxAccounts;
    if (maxAccounts > existingNumSystemAccounts) {
      numAccounts = existingNumSystemAccounts;
      log.warn("Number of system accounts (" + maxAccounts + "), excceeded limit of "
          + existingNumSystemAccounts + "! Reset to " + numAccounts + ".");
    }
    AccountID[] rv = new AccountID[numAccounts];
    long realm = defaultListeningNodeAccountID.getRealmNum();
    long shard = defaultListeningNodeAccountID.getShardNum();

    int startCount = 2 + addressListSize + 1;
    for (int i = 0; i < numAccounts; i++) {
      long accNum = startCount + i;
      AccountID sysAccountID = RequestBuilder.getAccountIdBuild(accNum, realm, shard);
      Key key = acc2ComplexKeyMap
          .get(genesisAccountID); // note system accounts use same keypair as genesis

      //transfer funds to the account from genesis
      TransactionReceipt receipt = transfer(genesisAccountID, defaultListeningNodeAccountID,
          genesisAccountID, sysAccountID, DEFAULT_INITIAL_ACCOUNT_BALANCE, false, true);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

      acc2ComplexKeyMap.put(sysAccountID, key);
      rv[i] = sysAccountID;
    }

    return rv;
  }

  /**
   * Creates an account.
   */
  public AccountID createAccount(AccountID payerAccount, AccountID nodeAccountID, boolean cacheTxID)
      throws Throwable {
    String accountKeyType = getRandomAccountKeyType();
    return createAccountComplex(cstub, payerAccount, nodeAccountID, accountKeyType, cacheTxID);
  }

  /* Creates a crypto account.
   *
   * @param stub
   * @param account2keyMap maps from account id to corresponding key pair
   * @param payerAccount
   * @param nodeAccountID
   * @return the account ID of the created account
   * @throws Exception
   */
  public AccountID createAccount(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      Map<AccountID, List<KeyPair>> account2keyMap, AccountID payerAccount, AccountID nodeAccountID)
      throws Throwable {
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    List<PrivateKey> payerPrivKeys = null;
    payerPrivKeys = getAccountPrivateKeys(payerAccount);
    Transaction createAccountRequest = TestHelperComplex
        .createAccountWithFee(payerAccount, nodeAccountID, pair, DEFAULT_INITIAL_ACCOUNT_BALANCE,
            payerPrivKeys);

    sendCreateAccountRequest(createAccountRequest);

    // get transaction receipt
    log.info("preparing to getTransactionReceipts....");
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(createAccountRequest);
    TransactionID transactionID = body.getTransactionID();
    cache.addTransactionID(transactionID);
    Query query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
        .build();
    Response transactionReceipts = fetchReceipts(query, cstub);
    AccountID accountID = transactionReceipts.getTransactionGetReceipt().getReceipt()
        .getAccountID();
    List<KeyPair> keys = new ArrayList<>();
    keys.add(pair);
    account2keyMap.put(accountID, keys);
    log.info("Account created: account num :: " + accountID.getAccountNum());

    // get account info
    //		CommonUtils.nap(WAIT_IN_SEC);
    AccountInfo accInfo = getAccountInfo(accountID);
    log.info("Created account info = " + accInfo);
    Assert.assertEquals(body.getCryptoCreateAccount().getInitialBalance(),
        accInfo.getBalance());

    return accountID;
  }

  private void sendCreateAccountRequest(Transaction createAccountRequest) throws Exception {
    log.info("\n-----------------------------------");
    log.info("createAccount: request = " + createAccountRequest);
    TransactionResponse response = retryLoopTransaction(createAccountRequest, "createAccount");
    log.info("createAccount Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
  }

  /**
   * Creates a crypto account with complex types, i.e. keylist or threshold keys.
   *
   * @param accountKeyType type of complex key
   * @return the account ID of the created account
   */
  public AccountID createAccountComplex(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID payerAccount, AccountID nodeAccountID, String accountKeyType,
      boolean cacheTxID) throws Throwable {
    return createAccountComplex(payerAccount, nodeAccountID, accountKeyType, getReceipt,
        cacheTxID);
  }

  /**
   * Creates a crypto account with complex types, i.e. keylist or threshold keys.
   *
   * @param accountKeyType type of complex key
   * @return the account ID of the created account
   */
  public AccountID createAccountComplex(AccountID payerAccount, AccountID nodeAccountID,
      String accountKeyType, boolean needReceipt, boolean cacheTxID) throws Throwable {
    long balance = DEFAULT_INITIAL_ACCOUNT_BALANCE;
    if (UmbrellaServiceRunnable.isSmallAccount) {
      balance = balance / SMALL_ACCOUNT_BALANCE_FACTOR;
    }

    return createAccountComplex(payerAccount, nodeAccountID, accountKeyType, balance,
        needReceipt, cacheTxID);
  }

  /**
   * Creates a crypto account with complex types, i.e. keylist or threshold keys.
   *
   * @param accountKeyType type of complex key
   * @param initBalance initial balance for the new account
   * @return the account ID of the created account
   */
  public AccountID createAccountComplex(AccountID payerAccount, AccountID nodeAccountID,
      String accountKeyType, long initBalance, boolean needReceipt, boolean cacheTxID)
      throws Throwable {
    Key key = genComplexKey(accountKeyType);
    Transaction createAccountRequest = TestHelperComplex.createAccountComplex(payerAccount,
        nodeAccountID, key, initBalance, receiverSigRequired, accountDuration);

    sendCreateAccountRequest(createAccountRequest);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(createAccountRequest);
    TransactionID transactionID = body.getTransactionID();
    if (cacheTxID) {
      cache.addTransactionID(transactionID);
    }

    // get transaction receipt
    log.info("preparing to getTransactionReceipts....");

    AccountID accountID = null;
    if (needReceipt) {
      Query query = Query.newBuilder().setTransactionGetReceipt(
          RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
          .build();
      Response transactionReceipts = fetchReceipts(query, cstub);
      if (!ResponseCodeEnum.SUCCESS.name()
          .equals(transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name())) {
        throw new Exception(
            "Create account failed! The receipt retrieved receipt=" + transactionReceipts);
      }
      accountID = transactionReceipts.getTransactionGetReceipt().getReceipt()
          .getAccountID();
      acc2ComplexKeyMap.put(accountID, key);
      if (receiverSigRequired) {
        recvSigRequiredAccounts.add(accountID);
      }
      log.info("Account created: account num :: " + accountID.getAccountNum());

      // get account info
      CommonUtils.nap(WAIT_IN_SEC);
      AccountInfo accInfo = getAccountInfo(accountID);
      log.info("Created account info = " + accInfo);
      Assert.assertEquals(body.getCryptoCreateAccount().getInitialBalance(),
          accInfo.getBalance());
    }

    return accountID;
  }

  /**
   * Update complex key registry.
   */
  protected void readGenesisInfo() throws Exception {
    KeyPairObj genesisKeyPair;
    String payerAccountNumStr = getApplicationProperties().getProperty("payerAccountForTests");
    long payerAccountNum;
    try {
      payerAccountNum = Long.parseLong(payerAccountNumStr);
    }catch (Exception e) { payerAccountNum = 2L; }
//    if (payerAccountNum > 2 ) {
//      String pemFilePath = getApplicationProperties().getProperty("payerAccountPEMKeyFile");
//      if(pemFilePath.isEmpty() || payerAccountNumStr.isEmpty()) {
//        throw new Exception("payerAccountForTests is missing in application.properties ");
//      }
//      // Get Payer Account Key Pair
//      AccountKeyListObj genKeyListObj = SetupAccount.getAccountKeyListObj(pemFilePath,payerAccountNum);
//      genesisKeyPair = genKeyListObj.getKeyPairList().get(0);
//    } else {
      // Get Genesis Account key Pair
      Path path = Paths.get(INITIAL_ACCOUNTS_FILE);
      Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);
      genesisAccountList = keyFromFile.get("START_ACCOUNT");
      genesisAccountID = genesisAccountList.get(0).getAccountId();
      genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
//  }
    String pubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();
    Key akey;
    if (KeyExpansion.USE_HEX_ENCODED_KEY) {
      akey = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyHex)).build();
    } else {
      akey = Key.newBuilder().setEd25519(ByteString.copyFrom(ClientBaseThread.hexToBytes(pubKeyHex)))
          .build();
    }
    genesisPrivateKey = genesisKeyPair.getPrivateKey();
    pubKey2privKeyMap.put(pubKeyHex, genesisPrivateKey);
    startUpKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(akey)).build();
    acc2ComplexKeyMap.put(genesisAccountID,startUpKey );
  }

  /**
   * Reads default application properties.
   */
  protected void readAppConfig() {
    readAppConfig(null, null);
  }

  /**
   * Reads default application properties.
   *
   * @param hostOverWrite used to overwrite default
   * @param nodeAccountOverWrite used to overwrite default
   */
  protected void readAppConfig(String hostOverWrite, Long nodeAccountOverWrite) {
    CustomProperties properties = TestHelperComplex.getApplicationPropertiesNew();
    if (hostOverWrite == null) {
      host = properties.getString("host", "localhost");
    } else {
      host = hostOverWrite;
    }

    port = properties.getInt("port", 50211);
    if (nodeAccountOverWrite == null) {
      String nodeAccIDStr = properties
          .getString("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
      defaultListeningNodeAccountID = extractAccountID(nodeAccIDStr);
    } else {
      defaultListeningNodeAccountID = AccountID.newBuilder().setAccountNum(nodeAccountOverWrite)
          .build();
    }

    uniqueListeningPortFlag = properties.getInt("uniqueListeningPortFlag", 0);
    productionFlag = properties.getInt("productionFlag", 0);
  }

  protected void createStubs() throws URISyntaxException, IOException {
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
    stub = FileServiceGrpc.newBlockingStub(channel);
    cstub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  /**
   * Parse a string array to an int array.
   *
   * @return an array of int
   */
  private int[] parseIntArray(String[] arrayOfStr) {
    int[] rv = new int[arrayOfStr.length];
    for (int i = 0; i < rv.length; i++) {
      rv[i] = Integer.parseInt(arrayOfStr[i]);
    }

    return rv;
  }

  /**
   * Gets the node account IDs from the config file.
   */
  public void getNodeAccountsFromLedger() throws Throwable {
    NodeAddressBook addrBook = getAddressBook();
    List<NodeAddress> addrList = addrBook.getNodeAddressList();
    int numNodes = addrList.size();
    nodeAccounts = new AccountID[numNodes];
    for (int i = 0; i < numNodes; i++) {
      NodeAddress addr = addrList.get(i);
      String memo = new String(addr.getMemo().toByteArray(), "UTF-8");
      AccountID nodeAccount = extractAccountID(memo);
      nodeAccounts[i] = nodeAccount;
      nodeID2Ip.put(nodeAccount, addr);
      nodeID2Port.put(nodeAccount, 50211);
    }
  }

  /**
   * Gets the node addressbook from the ledger.
   */
  public NodeAddressBook getAddressBook() throws Throwable {
    AccountID payerID = createAccountComplex(genesisAccountID, defaultListeningNodeAccountID,
        SUPPORTE_KEY_TYPES.single.name(), true, false);
    CommonUtils.nap(2);
    FileID fid = FileID.newBuilder().setFileNum(ADDRESS_FILE_ACCOUNT_NUM).setRealmNum(0)
        .setShardNum(0).build();
    ByteString content = getFileContent(fid, payerID, defaultListeningNodeAccountID);
    NodeAddressBook book = NodeAddressBook.newBuilder().mergeFrom(content.toByteArray()).build();
    return book;
  }

  /**
   * Get the transaction receipt.
   *
   * @param txId ID of the tx
   * @return the transaction receipt
   */
  public TransactionReceipt getTxReceipt(TransactionID txId) throws Throwable {
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(
            RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
        .build();
    Response transactionReceipts = fetchReceipts(query, cstub);
    TransactionReceipt rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
    return rv;
  }

  /**
   * Gets Tx fast record, retry if necessary.
   */
  public TransactionReceipt getTxFastRecord(TransactionID txId) throws Throwable {
    return TestHelperComplex.getTxReceipt(txId, cstub, log, host);
  }


  /**
   * Gets the random account key type.
   */
  protected String getRandomAccountKeyType() {
    int index = rand.nextInt(accountKeyTypes.length);
    return accountKeyTypes[index];
  }

  /**
   * Gets the random Signature Format.
   */
  protected String getRandomSignatureFormat() {
    int index = rand.nextInt(signatureFormat.length);
    return signatureFormat[index];
  }

  /**
   * Gets account info using genesis as payer and default listening node as receiving node.
   *
   * @param accountID the account to get info for
   */
  public AccountInfo getAccountInfo(AccountID accountID) throws Throwable {
    return getAccountInfo(accountID, genesisAccountID.getAccountNum(),
        defaultListeningNodeAccountID.getAccountNum());
  }

  /**
   * Gets the account info for a given account ID.
   */
  public AccountInfo getAccountInfo(AccountID accountID, long fromAccountNum, long toAccountNum)
      throws Throwable {

    long getAccountInfoFee = getGetAccountInfoFee(accountID, fromAccountNum, toAccountNum);

    AccountID payerAccountID = AccountID.newBuilder().setAccountNum(fromAccountNum).setRealmNum(0)
            .setShardNum(0).build();
    AccountID toID = AccountID.newBuilder().setAccountNum(toAccountNum).setRealmNum(0).setShardNum(0)
            .build();
    Transaction paymentTxSigned = getQueryPaymentSignedWithFee(payerAccountID, toID,
            "getCryptoGetAccountInfo", getAccountInfoFee);

    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
    log.info(
        "\n-----------------------------------\ngetAccountInfo: request = " + cryptoGetInfoQuery);
    Response getInfoResponse = retryLoopQuery(cryptoGetInfoQuery, "getAccountInfo");
    log.info("Pre Check Response of getAccountInfo:: "
        + getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(getInfoResponse);
    Assert.assertNotNull(getInfoResponse.getCryptoGetInfo());
    log.info("getInfoResponse :: " + getInfoResponse.getCryptoGetInfo());

    AccountInfo accInfo = getInfoResponse.getCryptoGetInfo().getAccountInfo();
    return accInfo;
  }

  private long getGetAccountInfoFee(AccountID accountID, long fromAccountNum, long toAccountNum) throws Exception {
    Transaction paymentTxSignedCost = getPaymentSigned(fromAccountNum, toAccountNum,
        "getCryptoGetAccountInfoCost");
    Query cryptoGetInfoQueryCost = RequestBuilder
            .getCryptoGetInfoQuery(accountID, paymentTxSignedCost, ResponseType.COST_ANSWER);
    Response getInfoResponseCost = retryLoopQuery(cryptoGetInfoQueryCost, "getAccountInfo");
    return getInfoResponseCost.getCryptoGetInfo().getHeader().getCost();
  }

  private long getAccountRecordsCostFee(AccountID accountID, long fromAccountNum, long toAccountNum) throws Exception {
    Transaction paymentTxSignedCost = getPaymentSigned(fromAccountNum, toAccountNum,
            "getAccountRecordsCost");
    Query cryptoGetInfoQueryCost = RequestBuilder.getAccountRecordsQuery(accountID, paymentTxSignedCost, ResponseType.COST_ANSWER);
    Response getAccountRecordsCost = retryLoopQuery(cryptoGetInfoQueryCost, "getAccountRecords");
    return getAccountRecordsCost.getCryptoGetAccountRecords().getHeader().getCost();
  }
  /**
   * Generates a complex key up to 2 levels.
   *
   * @param accountKeyType complex key type
   */
  protected Key genComplexKey(String accountKeyType) {
    Key key = null;
    if (accountKeyType.equals(SUPPORTE_KEY_TYPES.thresholdKey.name())) {
      key = KeyExpansion
          .genThresholdKeyInstance(COMPLEX_KEY_SIZE, COMPLEX_KEY_THRESHOLD, pubKey2privKeyMap);
    } else if (accountKeyType.equals(SUPPORTE_KEY_TYPES.keylist.name())) {
      key = KeyExpansion.genKeyListInstance(COMPLEX_KEY_SIZE, pubKey2privKeyMap);
    } else {
      key = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(pubKey2privKeyMap);
    }

    return key;
  }

  /**
   * Generates a listKey
   */
  protected Key genListKey(int size) {
    Key key = KeyExpansion.genKeyListInstance(size, pubKey2privKeyMap);
    return key;
  }

  /**
   * Extract account ID from memo string, e.g. "0.0.5".
   */
  protected AccountID extractAccountID(String memo) {
    AccountID rv = null;
    String[] parts = memo.split("\\.");
    rv = AccountID.newBuilder().setShardNum(Long.parseLong(parts[0]))
        .setRealmNum(Long.parseLong(parts[1]))
        .setAccountNum(Long.parseLong(parts[2])).build();
    return rv;
  }

  /**
   *
   */
  public ByteString getFileContent(FileID fid, AccountID payerID, AccountID nodeID)
      throws Throwable {
    FileServiceBlockingStub stub = null;
    ManagedChannel[] createdChannels = new ManagedChannel[1];
    long fileGetContentCost = getFileGetContentCost(fid, payerID, nodeID, stub);
    Transaction paymentTxSigned = getQueryPaymentSignedWithFee(payerID, nodeID, "FileGetContent", fileGetContentCost);
    Query fileGetContentQuery = RequestBuilder.getFileGetContentBuilder(paymentTxSigned, fid,
        ResponseType.ANSWER_ONLY);
    log.info("\n-----------------------------------");
    log.info("FileGetContent: query = " + fileGetContentQuery);

    Response fileContentResp = retryLoopQuery(fileGetContentQuery, "getFileContent");

    FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
    ByteString actualFileData = fileContent.getContents();
    log.info("FileGetContent: content = " + fileContent);
    log.info("FileGetContent: file size = " + actualFileData.size());
    return actualFileData;
  }

  private long getFileGetContentCost(FileID fid, AccountID payerID, AccountID nodeID, FileServiceBlockingStub stub) throws Exception {
    Transaction paymentTxSignedCost = getQueryPaymentSigned(payerID, nodeID, "FileGetContent");
    Query fileGetContentQueryCost = RequestBuilder.getFileGetContentBuilder(paymentTxSignedCost, fid,
            ResponseType.COST_ANSWER);
    Response getFileContentResponseCost = retryLoopQuery(fileGetContentQueryCost, "getFileContent");
    return getFileContentResponseCost.getFileGetContents().getHeader().getCost();
  }


  /**
   * Gets a FileServiceBlockingStub instance for connecting to a given node. Creates it if none
   * exists.
   *
   * @param nodeID the node to connect to
   */
  public synchronized FileServiceBlockingStub getStub(AccountID nodeID)
      throws Exception {
    FileServiceBlockingStub rv = null;
    if (nodeID2Stub.containsKey(nodeID)) {
      rv = nodeID2Stub.get(nodeID);
    } else {
      rv = newStub(nodeID);
    }

    return rv;
  }

  protected FileServiceBlockingStub newStub(AccountID nodeID) throws Exception {
    FileServiceBlockingStub rv;
    int p = -1;
    NodeAddress nodeAddr = nodeID2Ip.get(nodeID);
    String host = new String(nodeAddr.getIpAddress().toByteArray(), "UTF-8");
    if (productionFlag != 0) { // for production, all nodes listen on same default port
      // gen new stub with default port
      p = port;
    } else { // for development
      if (uniqueListeningPortFlag != 1) { // single default stub
        reConnectChannel(); // Does nothing if the channel is READY.
        return stub;
      } else {
        // gen new stub with diff port
        int pDelta = nodeAddr.getPortno() % 1000;
        p = port + pDelta;
      }
    }

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, p).usePlaintext(true).build();
    rv = FileServiceGrpc.newBlockingStub(channel);
    nodeID2Stub.put(nodeID, rv);

    log.info("created stub for nodeID=" + nodeID);
    return rv;
  }

  /**
   * Gets balance of an account.
   *
   * @param accountID the account to get balance for
   * @return the balance
   */
  public long getAccountBalance(AccountID accountID, AccountID payerID, AccountID nodeID)
      throws Throwable {
    return getAccountBalance(cstub, accountID, payerID, nodeID);
  }

  /**
   * Gets account info.
   *
   * @param accountID the account to get info for
   */
  public AccountInfo getAccountInfo(AccountID accountID, AccountID payerID, AccountID nodeID)
      throws Throwable {
    return getAccountInfo(accountID, payerID.getAccountNum(), nodeID.getAccountNum());
  }

  /**
   * Makes a transfer.
   */
  public TransactionReceipt transfer(AccountID payerAccountID, AccountID nodeAccountID,
      AccountID fromAccountID,
      AccountID toAccountID, long amount) throws Throwable {
    Transaction transferTxSigned = getSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
        toAccountID,
        amount, "Transfer");

    return transfer(transferTxSigned);
  }

  /**
   * Makes a transfer.
   */
  public TransactionReceipt cryptoDelete(AccountID accountToBeDeleted, AccountID payerID, AccountID nodeID, long transactionFee) throws Throwable {

    Key payerKey = acc2ComplexKeyMap.get(payerID);
    Key accKey = acc2ComplexKeyMap.get(accountToBeDeleted);
    List<Key> keys = new ArrayList<>();
    keys.add(payerKey);
    keys.add(accKey);

    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
            .newBuilder().setDeleteAccountID(accountToBeDeleted).setTransferAccountID(payerID)
            .build();
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    TransactionID  transactionID = TransactionID.newBuilder().setAccountID(payerID)
            .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
            .setTransactionID(transactionID)
            .setNodeAccountID(nodeID)
            .setTransactionFee(transactionFee)
            .setTransactionValidDuration(transactionValidDuration)
            .setGenerateRecord(false)
            .setMemo("Crypto Delete")
            .setCryptoDelete(cryptoDeleteTransactionBody)
            .build();

    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction deletetx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();

    Transaction signDelete = TransactionSigner
            .signTransactionComplexWithSigMap(deletetx, keys, pubKey2privKeyMap);
    log.info("\n-----------------------------------\ncryptoDelete: request = "
            + com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(signDelete));
    TransactionResponse response = retryLoopTransaction(signDelete, "cryptoDelete");
    log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
            .extractTransactionBody(signDelete);
    TransactionID txId = body.getTransactionID();
    cache.addTransactionID(txId);
    TransactionReceipt receipt = null;
    if (CryptoServiceTest.getReceipt) {
      receipt = getTxReceipt(txId);
    }
    return receipt;
  }

  /**
   * Updates an account.
   *
   * @param accountID account to be updated
   * @param newKey the new key to replace the existing key
   * @return updated account info
   */
  public AccountInfo updateAccountKey(AccountID accountID, AccountID payerAccountID,
      AccountID nodeAccountID,
      Key newKey) throws Throwable {
    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key accKey = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (accountID.getAccountNum() != specialAccountNum) {
      keys.add(accKey);
    }
    keys.add(newKey);

    CryptoUpdateTransactionBody cryptoUpdate = CryptoUpdateTransactionBody.newBuilder()
        .setAccountIDToUpdate(accountID).setKey(newKey).build();

    Transaction updateTx = TestHelperComplex
        .updateAccount(accountID, payerAccountID, nodeAccountID, cryptoUpdate);
    Transaction signUpdate = TransactionSigner
        .signTransactionComplexWithSigMap(updateTx, keys, pubKey2privKeyMap);

    log.info("\n-----------------------------------\nupdateAccount: request = " + signUpdate);
    Key oldGenesisKey = acc2ComplexKeyMap.remove(accountID);

    TransactionResponse response = cstub.updateAccount(signUpdate);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
    TransactionID transactionID = body.getTransactionID();
    cache.addTransactionID(transactionID);
    TransactionReceipt txReceipt = getTxFastRecord(transactionID);
    Assert.assertNotNull(txReceipt);
    String status = txReceipt.getStatus().name();

    if (status.equals(ResponseCodeEnum.SUCCESS.name())) {
      acc2ComplexKeyMap.put(accountID, newKey);
    } else {
      acc2ComplexKeyMap.put(accountID, oldGenesisKey);
    }

    synchronized (accountsBeingUpdated) {
      if (accountsBeingUpdated.contains(accountID)) {
        accountsBeingUpdated.remove(accountID);
      }
    }

    AccountInfo accInfo = getAccountInfo(accountID, payerAccountID, nodeAccountID);
    Assert.assertNotNull(accInfo);
    log.info(accInfo);
    Assert.assertEquals(newKey, accInfo.getKey());

    log.info("updating successful" + "\n");
    return accInfo;
  }

  /**
   * Revert the genesis account key to the original key.
   */
  public void revertGenesisKey() throws Throwable {
    AccountID payerID = genesisAccountID;
    AccountID nodeID = defaultListeningNodeAccountID;

    try {
      if (changeGenesisKey) {
        AccountInfo accInfo = updateAccountKey(genesisAccountID, payerID, nodeID, startUpKey);
        log.info("revert genesis key success :) updated account info = " + accInfo);
      }
    } catch (Exception e) {
      log.warn("cryptoUpdate revert genesis key error!", e);
    }
  }

  /**
   * Changes the genesis account key.
   */
  public void changeGenesisKey() throws Throwable {
    AccountID payerID = genesisAccountID;
    AccountID nodeID = defaultListeningNodeAccountID;
    Key newKey = genComplexKey("thresholdKey");

    try {
      AccountInfo accInfo = updateAccountKey(genesisAccountID, payerID, nodeID, newKey);
      log.info("change genesis key success :) updated account info = " + accInfo);
    } catch (Exception e) {
      log.warn("cryptoUpdate genesis key error!", e);
    }
  }

  /**
   * Gets an account that is not used as payer. This account is used as a candidate for key update.
   *
   * @return an random account ID, or null if none available
   */
  public AccountID getRandomNonPayerAccount() {
    Set<AccountID> originalAccounts = acc2ComplexKeyMap.keySet();
    Set<AccountID> accounts = new HashSet<>();
    accounts.addAll(originalAccounts);
    for (AccountID acc : payerAccounts) {
      accounts.remove(acc);
    }

    // we don't consider genesis key here
    if (accounts.contains(genesisAccountID)) {
      accounts.remove(genesisAccountID);
    }

    if (accounts.size() == 0) {
      return null;
    }

    AccountID[] candidates = accounts.toArray(new AccountID[0]);
    AccountID rv = null;
    for (int i = 0; i < candidates.length; i++) {
      rv = candidates[rand.nextInt(candidates.length)];
      synchronized (accountsBeingUpdated) {
        if (!accountsBeingUpdated.contains(rv)) {
          accountsBeingUpdated.add(rv);
          break;
        }
      }
    }
    return rv;
  }

  /**
   * Generates a complex key up to defined depth.
   *
   * @param accountKeyType complex key type
   * @param currentLevel the current depth of the key tree
   * @param targetLevel the target depth of the key tree
   * @return the key constructed
   */
  protected Key genComplexKeyRecursive(String accountKeyType, int currentLevel, int targetLevel)
      throws Exception {
    Key key = null;
    if (accountKeyType.equals(SUPPORTE_KEY_TYPES.thresholdKey.name())
        || (accountKeyType.equals(SUPPORTE_KEY_TYPES.keylist.name()))) {
      if (targetLevel < 2) {
        throw new Exception(
            "Error: targetLevel should be at least 2 for keyList and thresholdKey! A base key has a depth of 1 whereas keyList and thresholdKey starts at depth of 2.");
      }
      Builder keyList = KeyList.newBuilder();
      for (int i = 0; i < COMPLEX_KEY_SIZE; i++) {
        Key item = null;
        // check if level reached, if not call parent at the next level
        if (currentLevel >= (targetLevel - 1)) {
          item = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(pubKey2privKeyMap);
        } else {
          String keyType = getRandomAccountKeyType();
          item = genComplexKeyRecursive(keyType, (currentLevel + 1), targetLevel);
        }
        keyList.addKeys(item);
      }
      if (accountKeyType.equals(SUPPORTE_KEY_TYPES.thresholdKey.name())) {
        key = Key.newBuilder()
            .setThresholdKey(
                ThresholdKey.newBuilder().setKeys(keyList).setThreshold(COMPLEX_KEY_THRESHOLD))
            .build();
      } else {
        key = Key.newBuilder().setKeyList(keyList).build();
      }
    } else {
      key = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(pubKey2privKeyMap);
    }

    return key;
  }

  public static void main(String[] args) throws Throwable {
    testGenComplexKeyRecursive("keylist", 5);
    testGenComplexKeyRecursive("thresholdKey", 5);
  }

  public static void testGenComplexKeyRecursive(String type, int depth) throws Exception {
    CryptoServiceTest tester = new CryptoServiceTest();
    tester.getTestConfig();
    Key key = tester.genComplexKeyRecursive(type, 1, depth);
    byte[] message = "testGenComplexKeyRecursive".getBytes();
    Signature sig = KeyExpansion.sign(key, message, pubKey2privKeyMap, depth);
    log.info(
        "\n\n******======> type=" + type + ", depth=" + depth + "\nkey=" + key + "\n\nsig=" + sig);
  }

  /**
   * Creates a signed Query payment tx using default listening node as the processing node and payer
   * as the from account.
   *
   * @param payerSeq payer account number, as the payer and the from account
   * @param toSeq node account number, as the node account and the to account
   * @return the signed transaction
   */
  protected static Transaction getPaymentSigned(long payerSeq, long toSeq, String memo)
      throws Exception {
    AccountID payerAccountID = AccountID.newBuilder().setAccountNum(payerSeq).setRealmNum(0)
        .setShardNum(0).build();
    AccountID toID = AccountID.newBuilder().setAccountNum(toSeq).setRealmNum(0).setShardNum(0)
        .build();
    return getQueryPaymentSigned(payerAccountID, toID, memo);
  }

  /**
   * Fetches the receipts, wait if necessary.
   */
  protected static Response fetchReceipts(Query query, CryptoServiceBlockingStub cstub2)
      throws Exception {
    return TestHelperComplex.fetchReceipts(query, cstub2, log, host);
  }

  /**
   * Creats a signed payment tx.
   */
  public static Transaction getQueryPaymentSigned(AccountID payerID, AccountID nodeID, String memo)
      throws Exception {
    Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, payerID, nodeID,
        TestHelper.getCryptoMaxFee(), memo);
    return paymentTxSigned;
  }

  /**
   * Creats a signed payment tx.
   */
  public static Transaction getQueryPaymentSignedWithFee(AccountID payerID, AccountID nodeID,
      String memo, long queryFee)
      throws Exception {
    Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, payerID, nodeID,
        queryFee, memo);
    return paymentTxSigned;
  }

  /**
   * Creates a signed transfer tx.
   */
  protected static Transaction getSignedTransferTx(AccountID payerAccountID,
      AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, String memo) throws Exception {

    Transaction paymentTx = getUnSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
        toAccountID, amount, memo);
    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key fromKey = acc2ComplexKeyMap.get(fromAccountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(fromKey);
    if (recvSigRequiredAccounts.contains(toAccountID)) {
      Key toKey = acc2ComplexKeyMap.get(toAccountID);
      keys.add(toKey);
    }
    Transaction paymentTxSigned = TransactionSigner
        .signTransactionComplexWithSigMap(paymentTx, keys, pubKey2privKeyMap);
    return paymentTxSigned;
  }

  /**
   * Creates a signed transfer tx.
   */
  protected static Transaction getSignedTransferTx(AccountID payerAccountID,
      AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, String memo, long transactionFee)
      throws Exception {

    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Transaction paymentTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
        payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
        nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), transactionFee, timestamp,
        transactionDuration, true,
        memo, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
        amount);

    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key fromKey = acc2ComplexKeyMap.get(fromAccountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(fromKey);
    if (recvSigRequiredAccounts.contains(toAccountID)) {
      Key toKey = acc2ComplexKeyMap.get(toAccountID);
      keys.add(toKey);
    }
    Transaction paymentTxSigned = TransactionSigner
        .signTransactionComplexWithSigMap(paymentTx, keys, pubKey2privKeyMap);
    return paymentTxSigned;
  }

  /**
   * Gets a random node account if isRandomSubmission is true, otherwise gets the default receiving
   * node account specified in test config.
   *
   * @return a a random node account ID
   */
  public static AccountID getRandomNodeAccount() {
    AccountID rv = defaultListeningNodeAccountID;
    if (isRandomSubmission && nodeAccounts != null && nodeAccounts.length > 0) {
      int index = rand.nextInt(nodeAccounts.length);
      rv = nodeAccounts[index];
    }

    return rv;
  }

  /**
   * Gets a random payer account.
   *
   * @return the selected account ID or null if none available
   */
  public static AccountID getRandomPayerAccount() {
    if (payerAccounts == null || payerAccounts.length == 0) {
      return null;
    }
    int index = 0;
    if (isRandomPayer) {
      index = rand.nextInt(payerAccounts.length);
    }
    return payerAccounts[index];
  }

  /**
   * Gets the payer private keys for signing.
   */
  protected static List<PrivateKey> getPayerPrivateKey(long payerSeqNum)
      throws InvalidKeySpecException, DecoderException {
    AccountID accountID = RequestBuilder.getAccountIdBuild(payerSeqNum, 0l, 0l);
    List<PrivateKey> privKey = getAccountPrivateKeys(accountID);

    return privKey;
  }

  /**
   * Gets the account private keys.
   *
   * @return private key of the given account
   */
  protected static List<PrivateKey> getAccountPrivateKeys(AccountID accountID) {
    List<PrivateKey> rv = new ArrayList<>();
    Key key = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = null;
    if (key.hasKeyList()) {
      keys = key.getKeyList().getKeysList();

    } else if (key.hasThresholdKey()) {
      keys = key.getThresholdKey().getKeys().getKeysList();
    } else {
      keys = java.util.Collections.singletonList(key);
    }

    for (Key aKey : keys) {
      if (!aKey.getEd25519().isEmpty()) {
        String pubKey = Common.bytes2Hex(aKey.getEd25519().toByteArray());
        PrivateKey privKey = pubKey2privKeyMap.get(pubKey);
        rv.add(privKey);
      } else if (!aKey.getECDSA384().isEmpty()) {
        String pubKey = Common.bytes2Hex(aKey.getECDSA384().toByteArray());
        PrivateKey privKey = pubKey2privKeyMap.get(pubKey);
        rv.add(privKey);
      } else {
        log.warn("Key type not supported: key=" + aKey);
      }
    }

    return rv;
  }

  /**
   * Checks transaction size.
   *
   * @throws Exception if the max size is exceeded.
   */
  protected void checkTxSize(Transaction txSigned) throws Exception {
    int requestSize = txSigned.getSerializedSize();
    if (requestSize > transactionMaxBytes) {
      String msg =
          "transactionMaxBytes (" + transactionMaxBytes + ") exceeded! requestSize=" + requestSize
              + ", txShortInfo=" + com.hedera.services.legacy.proto.utils.CommonUtils
              .toReadableStringShort(txSigned);
      log.warn(msg);
      throw new Exception(msg);
    } else {
      log.debug("request serialized size=" + requestSize);
    }
  }

  /**
   * Updates an account with all possible fields.
   *
   * @param accountID account to be updated
   * @param newKey the new key to replace the existing key
   * @return the account info of the updated account
   */
  public AccountInfo updateAccount(AccountID accountID, AccountID payerAccountID,
      AccountID nodeAccountID, Key newKey, AccountID proxyAccountID, Long sendRecordThreshold,
      Long recvRecordThreshold,
      Duration autoRenewPeriod, Timestamp expirationTime, Boolean receiverSigRequired)
      throws Throwable {

    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key accKey = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (accountID.getAccountNum() != specialAccountNum) {
      keys.add(accKey);
    }
    com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody.Builder cryptoUpdate = CryptoUpdateTransactionBody
        .newBuilder()
        .setAccountIDToUpdate(accountID);

    if (newKey != null) {
      cryptoUpdate.setKey(newKey);
      keys.add(newKey);
    }
    if (proxyAccountID != null) {
      cryptoUpdate.setProxyAccountID(proxyAccountID);
    }
    if (sendRecordThreshold != null) {
      cryptoUpdate.setSendRecordThresholdWrapper(UInt64Value.of(sendRecordThreshold));
    }
    if (recvRecordThreshold != null) {
      cryptoUpdate.setReceiveRecordThresholdWrapper(UInt64Value.of(recvRecordThreshold));
    }
    if (autoRenewPeriod != null) {
      cryptoUpdate.setAutoRenewPeriod(autoRenewPeriod);
    }
    if (expirationTime != null) {
      cryptoUpdate.setExpirationTime(expirationTime);
    }
    if (receiverSigRequired != null) {
      cryptoUpdate.setReceiverSigRequired(receiverSigRequired);
    }
    Transaction updateTx = TestHelperComplex
        .updateAccount(accountID, payerAccountID, nodeAccountID, cryptoUpdate.build());

    Transaction signUpdate = TransactionSigner
        .signTransactionComplexWithSigMap(updateTx, keys, pubKey2privKeyMap);

    log.info("\n-----------------------------------\nupdateAccount: request = " + signUpdate);
    Key oldGenesisKey = acc2ComplexKeyMap.remove(accountID);

    TransactionResponse response = cstub.updateAccount(signUpdate);
    Assert.assertNotNull(response);
    if (accountID.getAccountNum() != specialAccountNum
        || (accountID.getAccountNum() == specialAccountNum &&
        payerAccountID.getAccountNum() == genesisAccountNum)) {
      Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    }
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    String status="";
    if(ResponseCodeEnum.OK==response.getNodeTransactionPrecheckCode()){
      TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
      TransactionID transactionID = body.getTransactionID();
      cache.addTransactionID(transactionID);
      TransactionReceipt txReceipt = getTxFastRecord(transactionID);
      Assert.assertNotNull(txReceipt);
      status = txReceipt.getStatus().name();
    }
    if (newKey != null && status.equals(ResponseCodeEnum.SUCCESS.name())) {
      acc2ComplexKeyMap.put(accountID, newKey);
    } else {
      acc2ComplexKeyMap.put(accountID, oldGenesisKey);
    }

    synchronized (accountsBeingUpdated) {
      if (accountsBeingUpdated.contains(accountID)) {
        accountsBeingUpdated.remove(accountID);
      }
    }

    AccountInfo accInfo = getAccountInfo(accountID, payerAccountID, nodeAccountID);
    Assert.assertNotNull(accInfo);
    log.info(accInfo);
    if (newKey != null &&
        accountID.getAccountNum() == specialAccountNum &&
        payerAccountID.getAccountNum() == genesisAccountNum) {
      Assert.assertEquals(newKey, accInfo.getKey());
    } else if (newKey != null &&
        accountID.getAccountNum() != specialAccountNum) {
      Assert.assertEquals(newKey, accInfo.getKey());
    } else if (oldGenesisKey != null) {
      Assert.assertEquals(oldGenesisKey, accInfo.getKey());
    }

    log.info("updating successful" + "\n");
    return accInfo;
  }

  /**
   * Updates an account with all possible fields.
   *
   * @param accountID account to be updated
   * @param newKey the new key to replace the existing key
   * @return transaction response of the updated account
   */
  public TransactionResponse updateSpecialAccount(AccountID accountID, AccountID payerAccountID,
      AccountID nodeAccountID, Key newKey, AccountID proxyAccountID, Long sendRecordThreshold,
      Long recvRecordThreshold,
      Duration autoRenewPeriod, Timestamp expirationTime, Boolean receiverSigRequired)
      throws Throwable {

    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key accKey = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (accountID.getAccountNum() != specialAccountNum) {
      keys.add(accKey);
    }
    com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody.Builder cryptoUpdate = CryptoUpdateTransactionBody
        .newBuilder()
        .setAccountIDToUpdate(accountID);

    if (newKey != null) {
      cryptoUpdate.setKey(newKey);
      keys.add(newKey);
    }
    if (proxyAccountID != null) {
      cryptoUpdate.setProxyAccountID(proxyAccountID);
    }
    if (sendRecordThreshold != null) {
      cryptoUpdate.setSendRecordThreshold(sendRecordThreshold);
    }
    if (recvRecordThreshold != null) {
      cryptoUpdate.setReceiveRecordThreshold(recvRecordThreshold);
    }
    if (autoRenewPeriod != null) {
      cryptoUpdate.setAutoRenewPeriod(autoRenewPeriod);
    }
    if (expirationTime != null) {
      cryptoUpdate.setExpirationTime(expirationTime);
    }
    if (receiverSigRequired != null) {
      cryptoUpdate.setReceiverSigRequired(receiverSigRequired);
    }

    Transaction updateTx = TestHelperComplex
        .updateAccount(accountID, payerAccountID, nodeAccountID, cryptoUpdate.build());

    Transaction signUpdate = TransactionSigner
        .signTransactionComplexWithSigMap(updateTx, keys, pubKey2privKeyMap);

    log.info("\n-----------------------------------\nupdateAccount: request = " + signUpdate);
    Key oldGenesisKey = acc2ComplexKeyMap.remove(accountID);

    TransactionResponse response = cstub.updateAccount(signUpdate);
    Assert.assertNotNull(response);

    log.info("updating successful" + "\n");
    return response;
  }

  /**
   * Makes a transfer.
   */
  public TransactionReceipt transfer(Transaction transferTxSigned) throws Throwable {
    log.info("\n-----------------------------------\ntransfer: request = "
        + com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(transferTxSigned));
    TransactionResponse response = retryLoopTransaction(transferTxSigned, "transfer");
    log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(transferTxSigned);
    TransactionID txId = body.getTransactionID();
    cache.addTransactionID(txId);
    TransactionReceipt receipt = null;
    if (CryptoServiceTest.getReceipt) {
      receipt = getTxReceipt(txId);
    }
    return receipt;
  }

  /**
   * Makes a transfer, but does not try to get receipts.
   *
   * @return transaction response
   */
  public TransactionResponse transferOnly(Transaction transferTxSigned) throws Throwable {
    log.info("\n-----------------------------------\ntransfer: request = "
        + com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(transferTxSigned));
    TransactionResponse response = retryLoopTransaction(transferTxSigned, "transfer");
    log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
    return response;
  }

  /**
   * Gets a random account for the from or to account of a transfer.
   *
   * @return the selected account ID or null if none available
   */
  public static AccountID getRandomTransferAccount() {
    if (transferAccounts == null || transferAccounts.length == 0) {
      return null;
    }

    int index = 0;
    if (isRandomTransferAccount) {
      index = rand.nextInt(transferAccounts.length);
    }
    return transferAccounts[index];
  }

  /**
   * Creates a signed transfer tx.
   */
  protected static Transaction getSignedTransferTx(Transaction unSignedTransferTx)
      throws Exception {
    TransactionBody txBody = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(unSignedTransferTx);
    com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.Builder bodyBuilder = txBody
        .getCryptoTransfer().toBuilder();
    AccountID payerAccountID = txBody.getTransactionID().getAccountID();
    List<Key> keys = new ArrayList<Key>();
    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    keys.add(payerKey);
    TransferList transferList = bodyBuilder.getTransfers();
    List<AccountAmount> accountAmounts = transferList.getAccountAmountsList();
    for (AccountAmount aa : accountAmounts) {
      AccountID accountID = aa.getAccountID();
      long amount = aa.getAmount();
//      if (amount <= 0) { // from account
      if (amount < 0) { // from account
        Key fromKey = acc2ComplexKeyMap.get(accountID);
        keys.add(fromKey);
      } else { // to account
        if (recvSigRequiredAccounts.contains(accountID)) {
          Key toKey = acc2ComplexKeyMap.get(accountID);
          keys.add(toKey);
        }
      }
    }

    Transaction paymentTxSigned = TransactionSigner
        .signTransactionComplexWithSigMap(unSignedTransferTx, keys, pubKey2privKeyMap);
    return paymentTxSigned;
  }

  /**
   * Creates a transfer tx with signatures.
   */
  public static Transaction getUnSignedTransferTx(AccountID payerAccountID,
      AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, String memo) throws Exception {

    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
        payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
        nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), TestHelper.getCryptoMaxFee(), timestamp,
        transactionDuration, true,
        memo, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
        amount);
    return transferTx;
  }

  /**
   * Gets balances of provided accounts using genesis account as the payer.
   *
   * @return map of account number to balance, the entry with key "total" contains the total balance
   * of all provided accounts
   */
  public Map<String, Long> getBalances(AccountID[] payerAccounts) throws Throwable {
    long total = 0;
    Map<String, Long> rv = new LinkedHashMap<>();
    AccountID payerID = genesisAccountID;
    AccountID nodeID = defaultListeningNodeAccountID;

    for (AccountID accountID : payerAccounts) {
      long balance = getAccountBalance(accountID, payerID, nodeID);
      total += balance;
      rv.put(String.valueOf(accountID.getAccountNum()), balance);
    }

    rv.put("total", total);
    return rv;
  }

  /**
   * Makes a transfer.
   *
   * @param cacheTxID flag for caching transaction ID
   * @param getReceiptInLine flag for getting receipt right after the transfer
   * @return receipt if getReceiptInLine is true, null otherwise
   */
  public TransactionReceipt transfer(AccountID payerAccountID, AccountID nodeAccountID,
      AccountID fromAccountID,
      AccountID toAccountID, long amount, boolean cacheTxID, boolean getReceiptInLine)
      throws Throwable {
    Transaction transferTxSigned = getSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
        toAccountID,
        amount, "Transfer");

    log.info("\n-----------------------------------\ntransfer: request = "
        + com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(transferTxSigned));
    TransactionResponse response = retryLoopTransaction(transferTxSigned, "transfer");
    log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(transferTxSigned);
    TransactionID txId = body.getTransactionID();
    if (cacheTxID) {
      cache.addTransactionID(txId);
    }
    TransactionReceipt receipt = null;
    if (getReceiptInLine) {
      receipt = getTxReceipt(txId);
    }
    return receipt;
  }

  /**
   * Makes a transfer.
   *
   * @param txHolder single element array for keeping the created tx so that the caller can access
   * it
   */
  public TransactionReceipt transfer(AccountID payerAccountID, AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, Transaction[] txHolder)
      throws Throwable {
    Transaction transferTxSigned = getSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
        toAccountID,
        amount, "Transfer");

    if (txHolder != null) {
      txHolder[0] = transferTxSigned;
    }

    return transfer(transferTxSigned);
  }

  /**
   * Creates a new channel and stub for file service.
   *
   * @param createdChannels to store generated channel
   */
  public static FileServiceBlockingStub createFileServiceStub(ManagedChannel[] createdChannels) {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true)
        .build();
    FileServiceBlockingStub rv = FileServiceGrpc.newBlockingStub(channel);
    createdChannels[0] = channel;

    return rv;
  }

  /**
   * Method to compare balances before Transfer and after Transfer
   */
  public static void tallyBalances() {
    for (int i = 0; i < CryptoServiceTest.transferAccounts.length; i++) {
      try {
        long tempBalance = CryptoServiceTest
            .getAccountBalance(cstub, CryptoServiceTest.transferAccounts[i], genesisAccountID,
                nodeAccounts[0]);
        long localAccountBalance = localLedgerBalance.get(CryptoServiceTest.transferAccounts[i]);
        log.info(
            "Tally AccountID: " + TextFormat.shortDebugString(CryptoServiceTest.transferAccounts[i])
                + ", with Ledger Balance="
                + tempBalance + ", Local Ledger Balance=" + localAccountBalance);
        Assert.assertEquals(tempBalance, localAccountBalance);
      } catch (Throwable t) {
        log.error("Error: getting account balance for : " + CryptoServiceTest.transferAccounts[i],
            t);
      }
    }
  }

  /**
   * Fet all account balances before starting Transfer Test and update local ledger to Tally the
   * balance after completion of tests
   */
  public static void initLocalLedgerWithAccountBalances() {
    for (int i = 0; i < CryptoServiceTest.transferAccounts.length; i++) {
      try {
        long tempBalance = CryptoServiceTest
            .getAccountBalance(cstub, CryptoServiceTest.transferAccounts[i], genesisAccountID,
                nodeAccounts[0]);
        localLedgerBalance.put(CryptoServiceTest.transferAccounts[i], tempBalance);
        log.info("Initializing Local ledger AccountID: " + TextFormat
            .shortDebugString(CryptoServiceTest.transferAccounts[i])
            + ", with Balance=" + tempBalance);
      } catch (Throwable t) {
        log.error("Error: getting account balance for : " + TextFormat
            .shortDebugString(CryptoServiceTest.transferAccounts[i]), t);
      }
    }
  }

  /**
   * Update local ledger balance after a transaction to keep track of transactions for Ledger
   * Balance Tally
   */
  public static boolean uplodateLocalLedger(AccountID accountID, long transactionAmount) {
    boolean successFlag = true;
    try {
      long localAccountBalance;
      synchronized (CryptoServiceTest.class) {
        localAccountBalance = localLedgerBalance.get(accountID) + transactionAmount;
        if (localAccountBalance < 0) {
          throw new Exception(
              "Account balance should not be negative while Updating Local ledger AccountID: "
                  + accountID + ", with Balance=" + localAccountBalance
                  + ", Transaction Amount=" + transactionAmount);
        }
        localLedgerBalance.put(accountID, localAccountBalance);
      }
      log.info(
          "Updating Local ledger AccountID: " + accountID + ", with Balance=" + localAccountBalance
              + ", Transaction Amount=" + transactionAmount);

    } catch (Throwable t) {
      log.error("Error: while updating account balance for : " + accountID, t);
      successFlag = false;
    }
    return successFlag;
  }

  /**
   * Get balance for provided Account with stub
   *
   * @return accountBalance
   */
  public static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerID, AccountID nodeID)
      throws Throwable {
    Transaction paymentTxSigned = getPaymentSigned(payerID.getAccountNum(), nodeID.getAccountNum(),
        "getAccountBalance");
    Query cryptoGetBalanceQuery = RequestBuilder
        .getCryptoGetBalanceQuery(accountID, paymentTxSigned,
            ResponseType.ANSWER_ONLY);
    log.info("\n-----------------------------------\ngetAccountBalance: request = "
        + cryptoGetBalanceQuery);
    CryptoServiceTest cryptoServiceTest = new CryptoServiceTest();
    CryptoServiceTest.cstub = stub;
    Response getBalanceResponse = cryptoServiceTest
        .retryLoopQuery(cryptoGetBalanceQuery, "cryptoGetBalance");
    log.info("Pre Check Response of getAccountBalance:: "
        + getBalanceResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode()
        .name());
    log.info("Get Balance Fee: " + getBalanceResponse.getCryptogetAccountBalance().getHeader()
        .getCost());
    Assert.assertNotNull(getBalanceResponse);
    Assert.assertNotNull(getBalanceResponse.getCryptoGetInfo());
    Assert.assertEquals(ResponseCodeEnum.OK,
        getBalanceResponse.getCryptogetAccountBalance().getHeader()
            .getNodeTransactionPrecheckCode());

    long balance = getBalanceResponse.getCryptogetAccountBalance().getBalance();
    log.info("getAccountBalance :: account=" + accountID.getAccountNum() + ", balance=" + balance);
    return balance;
  }

  protected void reConnectChannel() throws Exception {
    long stTime = System.nanoTime();
    if (channel == null || channel.getState(false) != ConnectivityState.READY) {
      createStubs();
     /* log.info("Connectivity is not READY reConnectChannel " + channelConnects);
      channelConnects++;*/
      long endTime = System.nanoTime() - stTime;
      log.error("Reconnect took NS " + endTime);
    }
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
          case "createAccount":
            response = cstub.createAccount(transaction);
            log.info(response);
            break;
          case "updateAccount":
            response = cstub.updateAccount(transaction);
            break;
          case "transfer":
            response = cstub.cryptoTransfer(transaction);
            break;
          case "cryptoDelete":
            response = cstub.cryptoDelete(transaction);
            break;
          case "addClaim":
            response = cstub.addLiveHash(transaction);
            break;
          case "deleteClaim":
            response = cstub.deleteLiveHash(transaction);
            break;
          default:
            throw new IllegalArgumentException();
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          try {
            reConnectChannel();
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

  private Response retryLoopQuery(Query query, String apiName)
      throws InterruptedException {
    Response response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

      if (i > 0) {
        log.info("retrying api call " + apiName);
      }
      ResponseCodeEnum precheckCode;
      try {
        switch (apiName) {
          case "getReceipt":
            response = cstub.getTransactionReceipts(query);
            precheckCode = response.getTransactionGetReceipt()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getAccountInfo":
            response = cstub.getAccountInfo(query);
            precheckCode = response.getCryptoGetInfo()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getAccountRecords":
            response = cstub.getAccountRecords(query);
            precheckCode = response.getCryptoGetAccountRecords()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getTxRecordByTxID":
            response = cstub.getTxRecordByTxID(query);
            precheckCode = response.getTransactionGetRecord()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "cryptoGetBalance":
            response = cstub.cryptoGetBalance(query);
            precheckCode = response.getCryptogetAccountBalance()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getFastTransactionRecord":
            response = cstub.getFastTransactionRecord(query);
            precheckCode = response.getTransactionGetFastRecord()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getClaim":
            response = cstub.getLiveHash(query);
            precheckCode = response.getCryptoGetLiveHash()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getFileContent":
            response = stub.getFileContent(query);
            precheckCode = response.getFileGetContents()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          default:
            throw new IllegalArgumentException();
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          try {
            reConnectChannel();
          } catch (Exception e) {
            log.error("Reconnect channel failed..");
            break;
          }
        }
        Thread.sleep(BUSY_RETRY_MS);
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(precheckCode)) {
        break;
      }
      Thread.sleep(BUSY_RETRY_MS);
    }
    return response;
  }
}
