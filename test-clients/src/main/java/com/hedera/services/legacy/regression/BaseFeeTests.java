package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.legacy.core.*;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.regression.umbrella.SmartContractServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hedera.services.legacy.regression.umbrella.UmbrellaServiceRunnable;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

public class BaseFeeTests extends BaseClient {

  private static final Logger log = LogManager.getLogger(BaseFeeTests.class);

  public BaseFeeTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  protected static List<String> testResults = new ArrayList<>();
  public static Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<>();
  protected static Properties appProperties = getApplicationProperties();
  public SmartContractServiceTest fit;
  protected Random random = new Random();
  public static SmartContractServiceGrpc.SmartContractServiceBlockingStub sstub;
  public static SmartContractServiceGrpc.SmartContractServiceBlockingStub[] contractStubs =null;
  public static PrivateKey queryPayerPrivateKey;
  public static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  public static KeyPair queryPayerKeyPair;
  public static AccountID queryPayerId;
  protected AccountID[] commonAccounts = null; // payer accounts, size determined by numCryptoAccounts
  public static AccountID payerID;
  public static AccountID nodeID;
  public static AccountID account_1;
  public static AccountID account_2;
  public static AccountID account_3;
  public static long NAP = 300;
  public static int FEE_VARIANCE_PERCENT;
  public static int CRYPTO_CREATE_MEMO_1_KEY_1_DUR_30;
  public static int CRYPTO_CREATE_MEMO_10_KEY_10_DUR_90;
  public static int CRYPTO_UPDATE_MEMO_1_KEY_1_DUR_30;
  public static int CRYPTO_UPDATE_MEMO_10_KEY_10_DUR_90;
  public static int CRYPTO_TRANSFER_MEMO_1_KEY_1_DUR_30;
  public static int CRYPTO_TRANSFER_MEMO_10_KEY_10_DUR_90;
  public static int CRYPTO_DELETE_MEMO_13_KEY_1;
  public static int CRYPTO_DELETE_MEMO_13_KEY_10;

  public static int FILE_CREATE_SIZE_1_KEY_1_DUR_30;
  public static int FILE_CREATE_SIZE_10_KEY_10_DUR_90;
  public static int FILE_UPDATE_SIZE_1_KEY_1_DUR_30;
  public static int FILE_UPDATE_SIZE_10_KEY_10_DUR_90;
  public static int FILE_DELETE_KEY_1;
  public static int FILE_DELETE_KEY_10;
  public static int FILE_APPEND_SIZE_4_KEY_1_DUR_30;
  public static int FILE_APPEND_SIZE_4_KEY_10_DUR_30;
  public static int SMARTCONTRACT_CREATE_STORAGE_10_RECSIZE_10_DUR_30;
  public static int SMARTCONTRACT_CREATE_STORAGE_1000_RECSIZE_1000_DUR_90;
  public static int CONTRACT_CALL_FUNC_10_RECSIZE_10;
  public static int CONTRACT_CALL_FUNC_1000_RECSIZE_1000;
  public static int SMARTCONTRACT_GET_INFO_SIG_1;
  public static int SMARTCONTRACT_GET_INFO_SIG_10;
  public static int SMARTCONTRACT_GET_RECORD_SIG_1;
  public static int SMARTCONTRACT_GET_RECORD_SIG_10;
  public static int SMARTCONTRACT_GET_BYTECODE_SIG_1;
  public static int SMARTCONTRACT_GET_BYTECODE_SIG_10;

  public static int QUERY_GET_ACCOUNT_BALANCE_SIG_1;
  public static int QUERY_GET_ACCOUNT_BALANCE_SIG_10;
  public static int QUERY_GET_ACCOUNT_INFO_SIG_1;
  public static int QUERY_GET_ACCOUNT_INFO_SIG_10;
  public static int QUERY_GET_FILE_INFO_SIG_1;
  public static int QUERY_GET_FILE_INFO_SIG_10;

  public static int QUERY_GET_FILE_CONTENT_SIG_1;
  public static int QUERY_GET_FILE_CONTENT_SIG_10;
  public static int QUERY_GET_TX_RECORD_BY_TX_ID_SIG_1;
  public static int QUERY_GET_TX_RECORD_BY_TX_ID_SIG_10;
  public static int QUERY_GET_ACCOUNT_RECORD_BY_ACCTID_ID_2;
  public static int QUERY_GET_ACCOUNT_RECORD_BY_ACCTID_ID_10;


  public void setup(String[] args) throws Throwable {
    init(args);
    CustomProperties testProps = new CustomProperties("config/estimatedFee.properties", false);
    CRYPTO_CREATE_MEMO_1_KEY_1_DUR_30 = testProps.getInt("cryptoCreate_memo_1_key_1_dur_30", 8333);
    CRYPTO_CREATE_MEMO_10_KEY_10_DUR_90 = testProps
        .getInt("cryptoCreate_memo_10_key_10_dur_90", 8333);
    CRYPTO_UPDATE_MEMO_1_KEY_1_DUR_30 = testProps.getInt("cryptoUpdate_memo_1_key_1_dur_30", 8333);
    CRYPTO_UPDATE_MEMO_10_KEY_10_DUR_90 = testProps
        .getInt("cryptoUpdate_memo_10_key_10_dur_90", 8333);
    CRYPTO_TRANSFER_MEMO_1_KEY_1_DUR_30 = testProps
        .getInt("cryptoTransfer_memo_1_key_1_dur_30", 8333);
    CRYPTO_TRANSFER_MEMO_10_KEY_10_DUR_90 = testProps
        .getInt("cryptoTransfer_memo_10_key_10_dur_90", 8333);
    
    CRYPTO_DELETE_MEMO_13_KEY_1 = testProps
        .getInt("cryptoDelete_memo_13_key_1", 6816667);
    CRYPTO_DELETE_MEMO_13_KEY_10 = testProps
        .getInt("cryptoDelete_memo_13_key_10", 30708334);

    FILE_CREATE_SIZE_1_KEY_1_DUR_30 = testProps.getInt("fileCreate_size_1_key_1_dur_30", 8333);
    FILE_CREATE_SIZE_10_KEY_10_DUR_90 = testProps.getInt("fileCreate_size_10_key_10_dur_90", 8333);
    FILE_UPDATE_SIZE_1_KEY_1_DUR_30 = testProps.getInt("fileUpdate_size_1_key_1_dur_30", 8333);
    FILE_UPDATE_SIZE_10_KEY_10_DUR_90 = testProps.getInt("fileUpdate_size_10_key_10_dur_90", 8333);
    FILE_DELETE_KEY_1 = testProps.getInt("fileDelete_key_1", 9583334);
    FILE_DELETE_KEY_10 = testProps.getInt("fileDelete_key_10", 43233334);
    FILE_APPEND_SIZE_4_KEY_1_DUR_30 = testProps.getInt("fileAppend_size_4_key_1_dur_30", 53958334);
    FILE_APPEND_SIZE_4_KEY_10_DUR_30 = testProps.getInt("fileAppend_size_4_key_10_dur_30", 137583334);
    
    SMARTCONTRACT_CREATE_STORAGE_10_RECSIZE_10_DUR_30 = testProps
        .getInt("smartContractCreate_storage_10_recSize_10_dur_30", 8333);
    SMARTCONTRACT_CREATE_STORAGE_1000_RECSIZE_1000_DUR_90 = testProps
        .getInt("smartContractCreate_storage_1000_recSize_1000_dur_90", 8333);
    CONTRACT_CALL_FUNC_10_RECSIZE_10 = testProps
        .getInt("smartContractCall_func_10_recSize_10", 8333);
    CONTRACT_CALL_FUNC_1000_RECSIZE_1000 = testProps
        .getInt("smartContractCall_func_1000_recSize_1000", 8333);

    SMARTCONTRACT_GET_INFO_SIG_1 = testProps
        .getInt("smartContract_getInfo_Sig_1", 8333);
    SMARTCONTRACT_GET_INFO_SIG_10 = testProps
        .getInt("smartContract_getInfo_Sig_10", 8333);
    SMARTCONTRACT_GET_RECORD_SIG_1 = testProps
        .getInt("smartContract_getRecords_Sig_1", 8333);
    SMARTCONTRACT_GET_RECORD_SIG_10 = testProps
        .getInt("smartContract_getRecords_Sig_10", 8333);
    SMARTCONTRACT_GET_BYTECODE_SIG_1 = testProps
        .getInt("smartContract_getByteCode_Sig_1", 8333);
    SMARTCONTRACT_GET_BYTECODE_SIG_10 = testProps
        .getInt("smartContract_getByteCode_Sig_10", 8333);

    QUERY_GET_ACCOUNT_BALANCE_SIG_1 = testProps
        .getInt("queryGetAccountBalanceSig_1", 8333);
    QUERY_GET_ACCOUNT_BALANCE_SIG_10 = testProps
        .getInt("queryGetAccountBalanceSig_10", 8333);
    QUERY_GET_ACCOUNT_INFO_SIG_1 = testProps
        .getInt("queryGetAccountInfoSig_1", 8333);
    QUERY_GET_ACCOUNT_INFO_SIG_10 = testProps
        .getInt("queryGetAccountInfoSig_10", 8333);
    QUERY_GET_FILE_INFO_SIG_1 = testProps
        .getInt("queryGetFileInfoSig_1", 8333);
    QUERY_GET_FILE_INFO_SIG_10 = testProps
        .getInt("queryGetFileInfoSig_10", 8333);

    QUERY_GET_FILE_CONTENT_SIG_1 = testProps
        .getInt("queryGetFileContent_Sig_1", 8333);
    QUERY_GET_FILE_CONTENT_SIG_10 = testProps
        .getInt("queryGetFileContent_Sig_10", 8333);
    QUERY_GET_TX_RECORD_BY_TX_ID_SIG_1 = testProps
        .getInt("queryTxRecordByTxID_Sig_1", 8333);
    QUERY_GET_TX_RECORD_BY_TX_ID_SIG_10 = testProps
        .getInt("queryTxRecordByTxID_Sig_10", 8333);
    
    QUERY_GET_ACCOUNT_RECORD_BY_ACCTID_ID_2 = testProps
            .getInt("queryAcctRecordByActID_2", 4);
    QUERY_GET_ACCOUNT_RECORD_BY_ACCTID_ID_10 = testProps
            .getInt("queryAcctRecordByActID_10", 12);

    FEE_VARIANCE_PERCENT = testProps.getInt("feeVariancePercentage", 5);

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);
    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    queryPayerPrivateKey = genKeyPairObj.getPrivateKey();
    queryPayerKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    queryPayerId = genesisAccount.get(0).getAccountId();
    accountKeyPairs.put(queryPayerId, queryPayerKeyPair);

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext().build();
    channelList.add(channel);
    sstub = SmartContractServiceGrpc.newBlockingStub(channel);

    TestHelper.initializeFeeClient(channel, queryPayerId, queryPayerKeyPair,
        AccountID.newBuilder().setAccountNum(nodeAccount).build());

    receiverSigRequired = false;
    // create accounts
    UmbrellaServiceRunnable.isSmallAccount = false;
    DEFAULT_INITIAL_ACCOUNT_BALANCE = 10000000000000L;
    commonAccounts = null;
    commonAccounts = accountCreatBatch(4, "single");
    payerID = commonAccounts[0];
    //if we create new Payer account please add keys to acc2ComplexKeyMap.get(payerAccount);
    if (nodeAccounts.length == 0) {
      nodeID = getDefaultNodeAccount();
    } else {
      nodeID = nodeAccounts[0];
    }
    account_1 = commonAccounts[1];
    Assert.assertNotNull(account_1);
    account_2 = commonAccounts[2];
    Assert.assertNotNull(account_2);
    account_3 = commonAccounts[3];
    Assert.assertNotNull(account_3);
    fit = new SmartContractServiceTest(testConfigFilePath);
//    nodeAccounts[0] = getDefaultNodeAccount();
    //Enable below only for local testing and update with ports
//    nodeID2Port.put(nodeAccounts[0],50211);
//    nodeID2Port.put(nodeAccounts[1],50416);
//    nodeID2Port.put(nodeAccounts[2],50417);

    contractStubs = new SmartContractServiceGrpc.SmartContractServiceBlockingStub[nodeAccounts.length];
    for(int i=0;i<nodeAccounts.length;i++) {
      contractStubs[i] = createSmartContractStub(nodeID2Ip.get(nodeAccounts[i]).getIpAddress().toStringUtf8(), nodeID2Port.get(nodeAccounts[i]));
    }
  }

  /**
   * create list of KeyPair
   */
  public static List<KeyPair> getKeyPairList(int count) {
    List<KeyPair> multiSig = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      multiSig.add(i, new KeyPairGenerator().generateKeyPair());
    }
    return multiSig;
  }

  public AccountID getMultiSigAccount(int keyCount, int memoSize, long durationSeconds)
      throws Exception {
    Key payerKey = acc2ComplexKeyMap.get(queryPayerId);
    accountKeyTypes = new String[]{"keylist"};
    COMPLEX_KEY_SIZE = keyCount;
    Key key = genComplexKey("keylist");
    nodeID = getDefaultNodeAccount();
    Transaction createAccountRequest = TestHelperComplex
        .createAccount(queryPayerId, payerKey, nodeID, key,
                (DEFAULT_INITIAL_ACCOUNT_BALANCE/SMALL_ACCOUNT_BALANCE_FACTOR), TestHelper.getCryptoMaxFee(),
            false, memoSize, durationSeconds);
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      return getMultiSigAccount(keyCount, memoSize, durationSeconds);
    }
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    Thread.sleep(NAP);
    log.info("response = " + response);
    AccountID newAccountID = getAccountID(createAccountRequest);
    log.info("New Account ID: " + newAccountID);
    acc2ComplexKeyMap.put(newAccountID, key);
    if (newAccountID == null) {
      return getMultiSigAccount(keyCount, memoSize, durationSeconds);
    } else {
      return newAccountID;
    }
  }

  public AccountID getMultiSigAccount(int keyCount, int memoSize, long durationSeconds, long initialBalance)
          throws Exception {
    Key payerKey = acc2ComplexKeyMap.get(queryPayerId);
    accountKeyTypes = new String[]{"keylist"};
    COMPLEX_KEY_SIZE = keyCount;
    Key key = genComplexKey("keylist");
    nodeID = getDefaultNodeAccount();
    Transaction createAccountRequest = TestHelperComplex
            .createAccount(queryPayerId, payerKey, nodeID, key,
                    initialBalance, TestHelper.getCryptoMaxFee(),
                    false, memoSize, durationSeconds);
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      return getMultiSigAccount(keyCount, memoSize, durationSeconds);
    }
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    Thread.sleep(NAP);
    log.info("response = " + response);
    AccountID newAccountID = getAccountID(createAccountRequest);
    log.info("New Account ID: " + newAccountID);
    acc2ComplexKeyMap.put(newAccountID, key);
    if (newAccountID == null) {
      return getMultiSigAccount(keyCount, memoSize, durationSeconds);
    } else {
      return newAccountID;
    }
  }

  public Transaction getMultiSigAccountTransaction(int keyCount, int memoSize, long durationSeconds)
      throws Exception {
    Key payerKey = acc2ComplexKeyMap.get(queryPayerId);
    accountKeyTypes = new String[]{"keylist"};
    COMPLEX_KEY_SIZE = keyCount;
    Key key = genComplexKey("keylist");
    Transaction createAccountRequest = TestHelperComplex
        .createAccount(queryPayerId, payerKey, nodeID, key, (DEFAULT_INITIAL_ACCOUNT_BALANCE/SMALL_ACCOUNT_BALANCE_FACTOR), TestHelper.getCryptoMaxFee(),
            false, memoSize, durationSeconds);
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      log.info("Transaction is null");
      return getMultiSigAccountTransaction(keyCount, memoSize, durationSeconds);
    }
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    Thread.sleep(NAP);
    log.info("response = " + response);
    AccountID newAccountID = getAccountID(createAccountRequest);
    log.info("New Account ID: " + newAccountID);
    acc2ComplexKeyMap.put(newAccountID, key);
    return createAccountRequest;
  }

  public static long getTransactionFee(Transaction transaction) throws Exception {
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = null;
    try {
      txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), cstub);
    } catch (Exception e) {
      log.info(e.getMessage());
      return 0;
    }
    AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
    Query query = TestHelper.getTxRecordByTxId(body.getTransactionID(), queryPayerId,
        queryPayerKeyPair, nodeID, TestHelper.getCryptoMaxFee(),
        ResponseType.ANSWER_ONLY);
    Response transactionRecord = cstub.getTxRecordByTxID(query);
    long transactionFee = transactionRecord.getTransactionGetRecord().getTransactionRecord()
        .getTransactionFee();
    return transactionFee;
  }

  /**
   * Get TransactionFee from Record
   */
  public static long getTransactionFeeFromRecord(Transaction transaction, AccountID payerID,
      String msg)
      throws Exception {
    CommonUtils.nap(2);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(transaction);
    TransactionRecord record = getTransactionRecord(payerID, body.getTransactionID());
    return record.getTransactionFee();
  }

  public static TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
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

  public static TransactionRecord getTransactionRecord(TransactionID transactionId, int retryCount) throws Exception {
    Query query = TestHelper.getTxRecordByTxId(transactionId, queryPayerId,
            queryPayerKeyPair, nodeID, TestHelper.getCryptoMaxFee(),
            ResponseType.ANSWER_ONLY);
    Response transactionRecord = cstub.getTxRecordByTxID(query);
    if(retryCount<=0 || "SUCCESS".equalsIgnoreCase(transactionRecord.getTransactionGetRecord().getTransactionRecord().getReceipt().getStatus().name())) {
      return transactionRecord.getTransactionGetRecord().getTransactionRecord();
    } else {
      Thread.sleep(300);
      return getTransactionRecord(transactionId, --retryCount);
    }
  }

  public static Response executeQueryForTxRecord(AccountID payerAccount,
      TransactionID transactionId,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
    return recordResp;
  }


  public static Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = TestHelper.createTransfer(payer, accountKeys.get(payer).get(0),
        nodeID, payer,
        accountKeys.get(payer).get(0), nodeID, transferAmt);
    return transferTx;

  }

  /**
   * Get Account from Transaction
   *
   * @return accountId
   */
  public static AccountID getAccountID(Transaction createAccountRequest) throws Exception {
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), cstub);
    return txReceipt1.getAccountID();
  }

  public static Key getKeyFromKeyPair(KeyPair keyPair) {
    byte[] pubKey = ((EdDSAPublicKey) keyPair.getPublic()).getAbyte();
    Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    return akey;
  }

  /**
   * Creates a crypto account with  keylist.
   */
  public AccountID createAccountWithListKey(AccountID payerAccount, AccountID nodeAccountID,
      long initBalance, boolean needReceipt, boolean cacheTxID, int keyListSize) throws Throwable {
    Key key = genListKey(keyListSize);
    CustomPropertiesSingleton customPropertiesSingleton = CustomPropertiesSingleton.getInstance();
    Transaction createAccountRequest = TestHelperComplex.createAccountComplex(payerAccount,
        nodeAccountID, key, initBalance, receiverSigRequired,
        customPropertiesSingleton.getAccountDuration());

    log.info("\n-----------------------------------");
    log.info("createAccount: request = " + createAccountRequest);
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    log.info("createAccount Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);

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
      CommonUtils.nap(2);
      AccountInfo accInfo = getAccountInfo(accountID);
      log.info("Created account info = " + accInfo);
      Assert.assertEquals(body.getCryptoCreateAccount().getInitialBalance(),
          accInfo.getBalance());
    }
    return accountID;
  }

  public static AccountID createAccount(KeyPair keyPair, AccountID payerAccount,
                                         long initialBalance) throws Exception {

    Transaction transaction = TestHelper
            .createAccountWithSigMap(payerAccount, nodeID, keyPair, initialBalance,
                    accountKeyPairs.get(payerAccount));
    TransactionResponse response = cstub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    System.out.println(
            "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
                    .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
            .getTxReceipt(body.getTransactionID(), cstub).getAccountID();
    accountKeys.put(newlyCreateAccountId, Collections.singletonList(keyPair.getPrivate()));
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    return newlyCreateAccountId;
  }

  public TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
            .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
                    transactionId, ResponseType.ANSWER_ONLY)).build();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Response transactionReceipts = stub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
            .getReceipt()
            .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.UNKNOWN.name())) {
      Thread.sleep(1000);
      transactionReceipts = stub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
              transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name() +
              "  (" + attempts + ")");
      attempts++;
    }
    if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
            .equals(ResponseCodeEnum.SUCCESS)) {
      receiptToReturn = transactionReceipts.getTransactionGetReceipt();
    }
    channel.shutdown();
    return transactionReceipts.getTransactionGetReceipt();

  }

}
