package com.hedera.services.legacy.unit.handler;

/*-
 * ‌
 * Hedera Services Node
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

import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.service.GlobalFlag;
import com.hedera.services.legacy.util.MockStorageWrapper;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.RecordCache;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.mocks.TestExchangeRates;
import com.hedera.test.mocks.TestFeesFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hedera.services.legacy.unit.PropertyLoaderTest;
import com.hedera.services.legacy.config.PropertiesLoader;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.swirlds.fcmap.FCMap;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.ethereum.db.DbFlushManager;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;


/**
 * @author oc
 * @version Junit5 Tests the CryptoHandler class features The unit test plan are as follows - 2
 * Tests per method {1 Full, 1 Canned Mock} per method in CryptoHandler 1) Add Account 2) Update
 * Account 3) Crypto Transfer 4) Get Account Details 5) LiveHash Related - Disabled 6) Get Repo & FS
 */


@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@DisplayName("CryptoHandler Test Suite")
public class CryptoHandlerTest {

  long nodeId = 123456l;
  FCMap<MapKey, HederaAccount> accountFCMap = null;
  FCMap<MapKey, Topic> topicFCMap = null;

  private FCMap<StorageKey, StorageValue> storageMap;
  FCMap<SolidityAddress, MapKey> solAddressMap = null;
  DbFlushManager dbFlushManager;
  TransactionBody utTxBody;
  AccountID accountID;
  AccountID proxyAccountID;
  MockStorageWrapper mockStorageWrapper;

  long seqNumber;
  //Initialize this
  Map<HederaFunctionality, FeeData> feeSchedule;
  String fsPath = "/dummy/fs";
  RecordCache recordCache;
  long payerAccount;
  long nodeAccount;
  boolean addAccount = false;
  boolean updateAccount = false;
  boolean cryptoTransfer = false;
  boolean addLiveHash = false;
  private CryptoHandlerTestHelper cryptoHandler;
  private TransactionHandler transactionHandler;
  private AccountID payerAccountId;
  private AccountID nodeAccountId;
  private AccountID testAccountId;
  //Crypto Transfer Case
  private AccountID senderAccountId;
  private AccountID receiverAccountId;
  //Two receiverAccountID for overflow testing
  private AccountID receiverAccountId_overflow_1;
  private AccountID receiverAccountId_overflow_2;

  private long selfID = 9870798L;

  public static void oneTimeSetup() {
    System.setProperty("log4j.defaultInitOverride", Boolean.toString(true));
    System.setProperty("log4j.ignoreTCL", Boolean.toString(true));
  }

  /**
   * TestInstance.Lifecycle.PER_CLASS is used to force non static implementation of BeforeAll When
   * using this mode, a new test instance will be created once per test class. Thus, if your test
   * methods rely on state stored in instance variables, you may need to reset that state in
   *
   * @BeforeEach or @AfterEach methods.
   */

  @BeforeAll
  public void setUp() throws Exception {

    payerAccount = TestHelper.getRandomLongAccount(1000);
    nodeAccount = TestHelper.getRandomLongAccount(100);
    payerAccountId = RequestBuilder.getAccountIdBuild(payerAccount, 0l, 0l);
    nodeAccountId = RequestBuilder.getAccountIdBuild(nodeAccount, 0l, 0l);
    System.out.println("Payer Account:" + payerAccountId);
    System.out.println("Node Account:" + nodeAccountId);
    senderAccountId = RequestBuilder.getAccountIdBuild(9999l, 0l, 0l);
    receiverAccountId = RequestBuilder.getAccountIdBuild(8888l, 0l, 0l);
    receiverAccountId_overflow_1 = RequestBuilder.getAccountIdBuild(7777l, 0l, 0l);
    receiverAccountId_overflow_2 = RequestBuilder.getAccountIdBuild(6666l, 0l, 0l);

    //Init FCMap
    accountFCMap = new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);
    topicFCMap = new FCMap<>(MapKey::deserialize, Topic::deserialize);
    storageMap = new FCMap<>(StorageKey::deserialize, StorageValue::deserialize);
    MapKey mk = new MapKey();
    mk.setAccountNum(payerAccount);
    mk.setRealmNum(0);

    HederaAccount mv = new HederaAccount();
    mv.setBalance(10000);
    accountFCMap.put(mk, mv);
    //Add sender & receiver Accounts
    mk = new MapKey();
    mk.setAccountNum(senderAccountId.getAccountNum());
    mk.setRealmNum(0);
    mv = new HederaAccount();
    mv.setBalance(20000);
    accountFCMap.put(mk, mv);
    mk = new MapKey();
    mk.setAccountNum(receiverAccountId.getAccountNum());
    mk.setRealmNum(0);
    mv = new HederaAccount();
    mv.setBalance(500);
    accountFCMap.put(mk, mv);

    mk = new MapKey();
    mk.setAccountNum(receiverAccountId_overflow_1.getAccountNum());
    mk.setRealmNum(0);
    mv = new HederaAccount();
    mv.setBalance(0);
    accountFCMap.put(mk, mv);

    mk = new MapKey();
    mk.setAccountNum(receiverAccountId_overflow_2.getAccountNum());
    mk.setRealmNum(0);
    mv = new HederaAccount();
    mv.setBalance(0);
    accountFCMap.put(mk, mv);

    //Init SolidityAddress Map
    solAddressMap = new FCMap<>(SolidityAddress::deserialize, MapKey::deserialize);
    SolidityAddress solAddress = new SolidityAddress("abcdefghijklmnop");
    MapKey solMapKey = new MapKey(0l, 0l, 5l);
    solAddressMap.put(solAddress, solMapKey);
    // Init FSAccount
    mockStorageWrapper = new MockStorageWrapper();

    cryptoHandler = new CryptoHandlerTestHelper(accountFCMap);
    //Initial Account
    utTxBody = getDummyTransactionBody("addAccount");

    accountID = RequestBuilder.getAccountIdBuild(111l, 1l, 0l);
    proxyAccountID = RequestBuilder.getAccountIdBuild(999l, 1l, 0l);
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    // Create account
    Transaction createAccountTx = TestHelper
        .createAccount(payerAccountId, nodeAccountId, firstPair, 1000l);
    Transaction signTransaction = TransactionSigner
        .signTransaction(createAccountTx, Collections.singletonList(firstPair.getPrivate()));

    mockStorageWrapper.fileCreate(fsPath, createAccountTx.toByteArray(), 1000, 2, 3, null);
    PropertyLoaderTest.populatePropertiesWithConfigFilesPath(
        "../../../configuration/dev/application.properties",
        "../../../configuration/dev/api-permission.properties");
    recordCache = new RecordCache(CacheBuilder.newBuilder().build());

    PrecheckVerifier precheckVerifier = mock(PrecheckVerifier.class);
    given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
    transactionHandler = new TransactionHandler(recordCache, accountFCMap, nodeAccountId,
            precheckVerifier,
            TEST_USAGE_PRICES, TestExchangeRates.TEST_EXCHANGE,
            TestFeesFactory.FEES_FACTORY.get(), () -> new StateView(topicFCMap, accountFCMap),
            new BasicPrecheck(TestContextValidator.TEST_VALIDATOR),
            new QueryFeeCheck(accountFCMap));
    GlobalFlag.getInstance().setExchangeRateSet(getDefaultExchangeRateSet());
  }

  private static ExchangeRateSet getDefaultExchangeRateSet() {
    long expiryTime = PropertiesLoader.getExpiryTime();
    return RequestBuilder.getExchangeRateSetBuilder(1, 1, expiryTime, 1, 1, expiryTime);
  }


  /**
   * Should get rid of this , just regular stuff
   */
  @BeforeEach
  @DisplayName("Checking Objects & If they changed")
  public void doSanityChecks() {
    // System.out.println("Crypto" + cryptoHandler);
    assertAll("cryptoHandler",
        () -> assertNotNull(cryptoHandler)
    );

    assertAll("TransactionHandler",
        () -> assertNotNull(transactionHandler)
    );

    assertAll("TransactionBody",
        () -> assertNotNull(utTxBody),
        () -> assertNotNull(utTxBody.getTransactionID()),
        () -> assertNotNull(utTxBody.getCryptoCreateAccount())

    );

  }

  public TransactionBody getDummyTransactionBody(String action) throws Exception {
    //Long payerAccountNum = 111l;
    Long payerRealmNum = 0l;
    Long payerShardNum = 0l;
    //Long nodeAccountNum=123l;
    Long nodeRealmNum = 0l;
    Long nodeShardNum = 0l;
    long transactionFee = 0l;
    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(100);
    boolean generateRecord = false;
    String memo = "UnitTesting";
    int thresholdValue = 10;
    List<Key> keyList = new ArrayList<>();
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    Key akey = Key.newBuilder().setEd25519(ByteString.copyFromUtf8((MiscUtils.commonsBytesToHex(pubKey))))
        .build();
    keyList.add(akey);
    long initBal = 100;
    long sendRecordThreshold = 5;
    long receiveRecordThreshold = 5;
    boolean receiverSign = false;
    Duration autoRenew = RequestBuilder.getDuration(100);
    ;
    long proxyAccountNum = 12345l;
    long proxyRealmNum = 0l;
    long proxyShardNum = 0l;
    long shardID = 0l;
    long realmID = 0l;
    TransactionBody txbody = null;

    if ("AddAccount".equalsIgnoreCase(action)) {
      txbody = RequestBuilder.getCreateAccountTxBody(payerAccount, payerRealmNum, payerShardNum,
          nodeAccount, nodeRealmNum, nodeShardNum, transactionFee, startTime,
          transactionDuration, generateRecord, memo, thresholdValue, keyList,
          initBal, sendRecordThreshold, receiveRecordThreshold, receiverSign,
          autoRenew, proxyAccountNum, proxyRealmNum, proxyShardNum, shardID, realmID);
    }
    if ("AddAccountFail1".equalsIgnoreCase(action)) {

      MapKey mk = new MapKey();
      mk.setAccountNum(payerAccount);
      mk.setRealmNum(0);

      HederaAccount mv = new HederaAccount();
      mv.setBalance(1);
      accountFCMap.replace(mk, mv);
      transactionFee = 10l; // fee is more than balance ok ?
      txbody = RequestBuilder.getCreateAccountTxBody(payerAccount, payerRealmNum, payerShardNum,
          nodeAccount, nodeRealmNum, nodeShardNum, transactionFee, startTime,
          transactionDuration, generateRecord, memo, thresholdValue, keyList,
          initBal, sendRecordThreshold, receiveRecordThreshold, receiverSign,
          autoRenew, proxyAccountNum, proxyRealmNum, proxyShardNum, shardID, realmID);
    }
    Transaction trx;
    if ("updateAccount".equalsIgnoreCase(action)) {

      SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
      trx = RequestBuilder
          .getAccountUpdateRequest(testAccountId, payerAccount, payerRealmNum, payerShardNum,
              nodeAccount, nodeRealmNum, nodeShardNum, transactionFee,
              startTime, transactionDuration, true, "UnitTesting",
              sendRecordThreshold, receiveRecordThreshold, autoRenew, signatures);
      txbody = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(trx);

    }

    if ("cryptoTransfer".equalsIgnoreCase(action)) {

      SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
      trx = RequestBuilder.getCryptoTransferRequest(payerAccount, payerRealmNum,
          payerShardNum, nodeAccount, nodeRealmNum, nodeShardNum, transactionFee,
          startTime, transactionDuration, true, "UnitTesting", signatures,
          senderAccountId.getAccountNum(), -123l,
          receiverAccountId.getAccountNum(), 123l);
      txbody = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(trx);
    }

    if ("cryptoTransfer_overflow".equalsIgnoreCase(action)) {
      // Build TransferList
      long sendAmount = 2;
      long receiveAmountEach = Long.MAX_VALUE;
      TransferList.Builder listBuilder = TransferList.newBuilder();
      listBuilder.addAccountAmounts(getAccountAmount(senderAccountId, sendAmount));
      listBuilder
          .addAccountAmounts(getAccountAmount(receiverAccountId_overflow_1, receiveAmountEach));
      listBuilder
          .addAccountAmounts(getAccountAmount(receiverAccountId_overflow_2, receiveAmountEach));
      Assert.assertEquals(0, sendAmount + receiveAmountEach + receiveAmountEach);
      TransferList transferList = listBuilder.build();
      // Build TransactionBody
      SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
      trx = RequestBuilder.getCryptoTransferRequest(payerAccount, payerRealmNum,
          payerShardNum, nodeAccount, nodeRealmNum, nodeShardNum, transactionFee,
          startTime, transactionDuration, true, "UnitTesting", signatures,
          transferList);
      txbody = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(trx);
    }

    return txbody;
  }


  private long getBalance(AccountID accountID) {
    MapKey mk = new MapKey();
    mk.setAccountNum(accountID.getAccountNum());
    mk.setRealmNum(0);
    mk.setShardNum(0l);
    HederaAccount mv = accountFCMap.get(mk);
    return mv.getBalance();
  }

  private AccountAmount getAccountAmount(AccountID accountID, long amount) {
    return AccountAmount.newBuilder()
        .setAccountID(accountID)
        .setAmount(amount).build();
  }


  /**
   * This is required to close all objects Else next set of test cases will fail
   */
  @AfterAll
  public void tearDown() throws Exception {
      mockStorageWrapper = null;
  }


  @Nested
  @DisplayName("1.AddAccount Feature")
  class AddingAccount {
    @Nested
    @DisplayName("2.UpdateAccount Feature")
    class UpdatingAccount {

      @Nested
      @DisplayName("3.Crypto Transfer")
      class CryptoTransferAfterAdd {
        @Test
        @DisplayName("6A.CryptoAddLiveHash")
        @Disabled
        public void gg_cryptoAddLiveHash() {
          Instant consensusTime = new Date().toInstant();
          byte[] messageDigest = null;

          MessageDigest md = null;
          try {
            md = MessageDigest.getInstance("SHA-384");
          } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
          }
          messageDigest = md.digest("achal".getBytes());
          KeyPair firstPair = new KeyPairGenerator().generateKeyPair();

//
          Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
          Duration transactionValidDuration = RequestBuilder.getDuration(100);
          AccountID nodeAccountID = AccountID.newBuilder().setAccountNum(3l).build();
          long transactionFee = 100000;
          boolean generateRecord = false;
          String memo = "add claims";
//    KeyList test = KeyList.newBuilder().addKeys(key).build();

          List<Key> waclPubKeyList = new ArrayList<>();

          List<PrivateKey> waclPrivKeyList;
          waclPrivKeyList = new ArrayList<>();
          TestHelper.genWacl(5, waclPubKeyList, waclPrivKeyList);
//
          ByteString claimbyteString = ByteString.copyFrom(messageDigest);
          LiveHash claim = LiveHash.newBuilder().setHash(claimbyteString)
              .setAccountId(AccountID.newBuilder()
                  .setAccountNum(1001l).build())
              .setKeys(KeyList.newBuilder().addAllKeys(waclPubKeyList).build()).build();
          CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = CryptoAddLiveHashTransactionBody
              .
                  newBuilder().setLiveHash(claim).build();
          TransactionID transactionID = TransactionID.newBuilder()
              .setAccountID(AccountID.newBuilder()
                  .setAccountNum(1001l).build())
              .setTransactionValidStart(timestamp).build();

          TransactionBody transactionBody = TransactionBody.newBuilder()
              .setTransactionID(transactionID)
              .setNodeAccountID(nodeAccountID)
              .setTransactionFee(transactionFee)
              .setTransactionValidDuration(transactionValidDuration)
              .setGenerateRecord(generateRecord)
              .setMemo(memo)
              .setCryptoAddLiveHash(cryptoAddLiveHashTransactionBody)
              .build();
          //Stuff things into the trans object
          TransactionRecord record = cryptoHandler.cryptoAddLiveHash(transactionBody, consensusTime);
          System.out.println(record + " :: is the record");

          assertAll("CryptoAddLiveHash",
              () -> assertNotNull(record),
              () -> assertTrue(record.hasReceipt()));

          //Internally does get the success
          assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

          addLiveHash = true;
        }

        @Test
        @DisplayName("8B.GetFsAccount:Checking FS Account")
        public void ij_getFsAccount() {

          assertAll("FS Account Checks",
              () -> assertNotNull(mockStorageWrapper));

        }
      }
    }
  }
}
