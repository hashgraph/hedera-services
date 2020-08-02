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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.unit.FCStorageWrapper;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.util.SCEncoding;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.SolidityLifecycleFactory;
import com.hedera.test.mocks.StorageSourceFactory;
import com.hedera.test.mocks.TestProperties;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.hedera.services.legacy.exception.NoFeeScheduleExistsException;
import com.hedera.services.legacy.exception.StorageKeyNotFoundException;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.legacy.unit.PropertyLoaderTest;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.Source;
import org.ethereum.db.ServicesRepositoryRoot;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author oc/peter
 * @version Junit5 Tests the SmartContractRequestHandler class features
 */

@Disabled
@RunWith(JUnitPlatform.class)
public class SmartContractRequestHandlerMiscTest {

  public static final String MAPPING_STORAGE_BIN = "/testfiles/MapStorage.bin";
  public static final String FALLBACK_BIN = "/testfiles/Fallback.bin";
  public static final String CREATE_TRIVIAL_BIN = "/testfiles/CreateTrivial.bin";
  public static final String NEW_OPCODES_BIN = "/testfiles/NewOpcodes.bin";
  public static final String CREATE_IN_CONSTRUCTOR_BIN = "/testfiles/createInConstructor.bin";
  private static final int CREATED_TRIVIAL_CONTRACT_RETURNS = 7;
  private static final long INITIAL_BALANCE_OFFERED = 20_000L;

  // Arbitrary account numbers.
  private static final long payerAccount = 787L;
  private static final long nodeAccount = 3L;
  private static final long feeCollAccount = 9876L;
  private static final long contractFileNumber = 333L;
  private static final long contractSequenceNumber = 334L;
  private static final long secondContractSequenceNumber = 668L;
  SmartContractRequestHandler smartHandler;
  FileServiceHandler fsHandler;
  FCMap<MerkleEntityId, MerkleAccount> fcMap = null;
  private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap;
  ServicesRepositoryRoot repository;
  LedgerAccountsSource ledgerSource;
  HederaLedger ledger;

  MerkleEntityId payerMerkleEntityId; // fcMap key for payer account
  byte[] payerKeyBytes = null; // Repository key for payer account
  AccountID payerAccountId;
  AccountID nodeAccountId;
  AccountID feeCollAccountId;
  FileID contractFileId;
  BigInteger gasPrice;
  private long selfID = 9870798L;
  private FCStorageWrapper storageWrapper;
  private static final Logger log = LogManager.getLogger(SmartContractRequestHandlerMiscTest.class);
  HbarCentExchange exchange;
  ExchangeRateSet rates;

  /**
   * TestInstance.Lifecycle.PER_CLASS is used to force non static implementation of BeforeAll When
   * using this mode, a new test instance will be created once per test class. Thus, if your test
   * methods rely on state stored in instance variables, you may need to reset that state in
   *
   * @BeforeEach or @AfterEach methods.
   */

  private ServicesRepositoryRoot getLocalRepositoryInstance() {
    DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);
    TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
            AccountProperty.class,
            () -> new MerkleAccount(),
            new FCMapBackingAccounts(fcMap),
            new ChangeSummaryManager<>());
    ledger = new HederaLedger(
            mock(EntityIdSource.class),
            mock(ExpiringCreations.class),
            mock(AccountRecordsHistorian.class),
            delegate);
    ledgerSource = new LedgerAccountsSource(ledger, TestProperties.TEST_PROPERTIES);
    Source<byte[], AccountState> repDatabase = ledgerSource;
    ServicesRepositoryRoot repository = new ServicesRepositoryRoot(repDatabase, repDBFile);
    repository.setStoragePersistence(new StoragePersistenceImpl(storageMap));
    return repository;
  }

  @BeforeEach
  public void setUp() throws Exception {
    payerAccountId = RequestBuilder.getAccountIdBuild(payerAccount, 0l, 0l);
    nodeAccountId = RequestBuilder.getAccountIdBuild(nodeAccount, 0l, 0l);
    feeCollAccountId = RequestBuilder.getAccountIdBuild(feeCollAccount, 0l, 0l);
    contractFileId = RequestBuilder.getFileIdBuild(contractFileNumber, 0L, 0L);

    //Init FCMap
    fcMap = new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
    storageMap = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());
    // Create accounts
    createAccount(payerAccountId, 1_000_000_000L);
    createAccount(nodeAccountId, 10_000L);
    createAccount(feeCollAccountId, 10_000L);

    //Init Repository
    repository = getLocalRepositoryInstance();

    gasPrice = new BigInteger("1");

    HbarCentExchange exchange = mock(HbarCentExchange.class);
    long expiryTime = Long.MAX_VALUE;
    rates = RequestBuilder
            .getExchangeRateSetBuilder(
                    1, 12,
                    expiryTime,
                    1, 15,
                    expiryTime);
    given(exchange.activeRates()).willReturn(rates);
    given(exchange.rate(any())).willReturn(rates.getCurrentRate());
    smartHandler = new SmartContractRequestHandler(
            repository,
            feeCollAccountId,
            ledger,
            fcMap,
            storageMap,
            ledgerSource,
            null,
            exchange,
            TEST_USAGE_PRICES,
            TestProperties.TEST_PROPERTIES,
            () -> repository,
            SolidityLifecycleFactory.newTestInstance(),
            ignore -> true,
            null);
    storageWrapper = new FCStorageWrapper(storageMap);
    FeeScheduleInterceptor feeScheduleInterceptor = mock(FeeScheduleInterceptor.class);
    fsHandler = new FileServiceHandler(storageWrapper, feeScheduleInterceptor, new ExchangeRates());
    String key = Hex.encodeHexString(EntityIdUtils.asSolidityAddress(0, 0, payerAccount));
    try {
      payerKeyBytes = MiscUtils.commonsHexToBytes(key);
    } catch (DecoderException e) {
      Assert.fail("Failure building solidity key for payer account");
    }
    payerMerkleEntityId = new MerkleEntityId();
    payerMerkleEntityId.setNum(payerAccount);
    payerMerkleEntityId.setRealm(0);
    payerMerkleEntityId.setShard(0);
    PropertyLoaderTest.populatePropertiesWithConfigFilesPath(
        "../../../configuration/dev/application.properties",
        "../../../configuration/dev/api-permission.properties");
  }

  private void createAccount(AccountID payerAccount, long balance)
      throws NegativeAccountBalanceException {
    MerkleEntityId mk = new MerkleEntityId();
    mk.setNum(payerAccount.getAccountNum());
    mk.setRealm(0);
    MerkleAccount mv = new MerkleAccount();
    mv.setBalance(balance);
    fcMap.put(mk, mv);
  }

  private byte[] createFile(String filePath, FileID fileId) {
    InputStream fis = SmartContractRequestHandlerMiscTest.class.getResourceAsStream(filePath);
    byte[] fileBytes = null;
    try {
      fileBytes = fis.readAllBytes();
    } catch (IOException e) {
      Assert.fail("Error creating file: reading contract file " + filePath);
    }
    ByteString fileData = ByteString.copyFrom(fileBytes);

    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()));
    Timestamp expTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).plusSeconds(130));
    Duration transactionDuration = RequestBuilder.getDuration(100);
    boolean generateRecord = true;
    String memo = "SmartContractFile";
    SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();

    Transaction txn = RequestBuilder.getFileCreateBuilder(payerAccount, 0L, 0L,
        nodeAccount, 0L, 0L,
        100L, startTime, transactionDuration, generateRecord,
        memo, signatures, fileData, expTime, Collections.emptyList());

    TransactionBody body = null;
    try {
      body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txn);
    } catch (InvalidProtocolBufferException e) {
      Assert.fail("Error creating file: parsing transaction body");
    }

    Instant consensusTime = new Date().toInstant();
    TransactionRecord record = fsHandler.createFile(body, consensusTime, fileId, selfID);

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertEquals(fileId.getFileNum(), record.getReceipt().getFileID().getFileNum());
    return fileBytes;
  }


  private TransactionBody getCreateTransactionBody() {
    return getCreateTransactionBody(0L, 250000L, null);
  }

  private TransactionBody getCreateTransactionBody(long initialBalance, long gas, Key adminKey) {
    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()));
    Duration transactionDuration = RequestBuilder.getDuration(100);
    Duration renewalDuration = RequestBuilder.getDuration(3600 * 24);
    boolean generateRecord = true;
    String memo = "SmartContract";
    String sCMemo = "SmartContractMemo";
    SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();

    Transaction txn = RequestBuilder.getCreateContractRequest(payerAccount, 0L, 0L,
        nodeAccount, 0L, 0L,
        100L, startTime, transactionDuration, generateRecord,
        memo, gas, contractFileId, ByteString.EMPTY, initialBalance,
        renewalDuration, signatures, sCMemo, adminKey);

    TransactionBody body = null;
    try {
      body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txn);
    } catch (InvalidProtocolBufferException e) {
      Assert.fail("Error creating contract: parsing transaction body");
    }
    return body;
  }

  private void checkContractArtifactsExist(ContractID contractId) {
    MerkleEntityId mk = new MerkleEntityId();
    mk.setNum(contractId.getContractNum());
    mk.setRealm(contractId.getRealmNum());
    mk.setShard(contractId.getShardNum());
    MerkleAccount mv = fcMap.get(mk);
    Assert.assertNotNull(mv);
    Assert.assertNotNull(mv.getKey());
    Assert.assertTrue(mv.getKey() instanceof JContractIDKey);
    String bytesPath = String.format("/%d/s%d", contractId.getRealmNum(), contractId.getContractNum());
    Assert.assertTrue(storageWrapper.fileExists(bytesPath));
  }

  private void checkContractDataArtifactExists(ContractID contractId) {
    String bytesPath = String.format("/%d/d%d", contractId.getRealmNum(), contractId.getContractNum());
    Assert.assertTrue(storageWrapper.fileExists(bytesPath));
  }

  @Test
  @DisplayName("aa createMapContract: Success")
  public void aa_createMapContract() {
    byte[] contractBytes = createFile(MAPPING_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(0L, 250000L, null);

    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
    Assert.assertTrue(record.hasContractCreateResult());

    ContractID newContractId = record.getReceipt().getContractID();
    checkContractArtifactsExist(newContractId);
  }


  private TransactionBody getCallTransactionBody(ContractID newContractId,
      ByteString functionData, long gas, long value) {
    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()));
    Duration transactionDuration = RequestBuilder.getDuration(100);
    SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();

    Transaction txn = RequestBuilder.getContractCallRequest(payerAccount, 0L, 0L,
        nodeAccount, 0L, 0L,
        100L /* fee */, startTime,
        transactionDuration, gas, newContractId,
        functionData, value, signatures);

    TransactionBody body = null;
    try {
      body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txn);
    } catch (InvalidProtocolBufferException e) {
      Assert.fail("Error calling contract: parsing transaction body");
    }
    return body;
  }

  @Test
  @DisplayName("ab MapContractSetCall: Success")
  public void ab_mapContractSetCall() {
    // Create the contract
    byte[] contractBytes = createFile(MAPPING_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);

    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to store a map entry
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeMapPut(1, 100));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert
        .assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
  }

  private Query getCallLocalQuery(ContractID newContractId,
      ByteString functionData, long gas, long maxResultSize) {

    Transaction transferTransaction = TestHelper.createTransferUnsigned(payerAccountId,
        feeCollAccountId, payerAccountId, nodeAccountId, 100000L /* amount */);

    return RequestBuilder.getContractCallLocalQuery(newContractId, gas,
        functionData, 0L /* value */, maxResultSize,
        transferTransaction, ResponseType.ANSWER_ONLY);
  }

  @Test
  @DisplayName("ac MapContractGetCall: Success")
  public void ac_mapContractGetCall() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(MAPPING_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to store a map entry
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeMapPut(1, 100));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to store another map entry
    dataToSet = ByteString.copyFrom(SCEncoding.encodeMapPut(2, 200));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to store another map entry
    dataToSet = ByteString.copyFrom(SCEncoding.encodeMapPut(3, 300));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to get the entry back
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeMapGet(2));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeGetValueResult(callResults);
    Assert.assertEquals(200, retVal);
  }

  @Test
  @DisplayName("ba create CreateTrivial contract: Success")
  public void ba_createCTContract() throws StorageKeyNotFoundException {
    byte[] contractBytes = createFile(CREATE_TRIVIAL_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();

    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
    Assert.assertTrue(record.hasContractCreateResult());

    ContractID newContractId = record.getReceipt().getContractID();
    checkContractArtifactsExist(newContractId);
  }

  @Test
  @DisplayName("bb CreateTrivial create call: Success")
  public void bb_CTCreateCall() {
    // Create the contract
    byte[] contractBytes = createFile(CREATE_TRIVIAL_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to create another contract
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialCreate());
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
  }

  @Test
  @DisplayName("bc CreateTrivial get value from created contract: Success")
  public void bc_CTGetIndirectCall() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(CREATE_TRIVIAL_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to create another contract
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialCreate());
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to get a value from the created contract.  The created contract
    // is  hard-coded to return uint 7.
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialGetIndirect());
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeCreateTrivialGetResult(callResults);
    Assert.assertEquals(CREATED_TRIVIAL_CONTRACT_RETURNS, retVal);
  }

  @Test
  @DisplayName("bd CreateTrivial get address of created contract: Success")
  public void bd_CTGetAddressCall() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(CREATE_TRIVIAL_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to create another contract
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialCreate());
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to get the address of the created contract.
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialGetAddress());
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    byte[] retVal = SCEncoding.decodeCreateTrivialGetAddress(callResults);

    // Validate that the artifacts are there for the created contract
    ContractID createdContractId = asContract(accountParsedFromSolidityAddress(retVal));
    System.out.println("Created contract is: " + createdContractId);

    checkContractArtifactsExist(createdContractId);
    // Data storage should have been created for both contracts
    checkContractDataArtifactExists(newContractId);
    checkContractDataArtifactExists(createdContractId);

    System.out.println("FCMap and FCFS artifacts exist for the contract-created contract");
  }

  @Test
  @DisplayName("ca Call to invalid function: fails")
  public void ca_InvalidFunctionCall() {
    // Create the contract
    byte[] contractBytes = createFile(MAPPING_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Try to call the get function from simpleStorage.bin
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeGetValue());
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    //validate that contruct function results are empty in case of a failure
    assert (record.hasContractCallResult());
    assert (record.getContractCallResult().getContractCallResult().isEmpty());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, record.getReceipt().getStatus());
    Assert.assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
  }

  @Test
  @DisplayName("cb Call to invalid function: fallback")
  public void cb_FallbackFunctionCall() {
    // Create the contract
    byte[] contractBytes = createFile(FALLBACK_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Try to call the get function from simpleStorage.bin.  This will succeed because
    // the contract has a fallback function.
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeGetValue());
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
  }

  @Test
  @DisplayName("cc Create with invalid initial value: fails")
  public void cc_InvalidInitialBalance() throws NoFeeScheduleExistsException {
    long payerBefore = getBalance(payerAccountId);
    long totalBefore = getTotalBalance();

    // Create the contract
    byte[] contractBytes = createFile(MAPPING_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(INITIAL_BALANCE_OFFERED, 250000L, null);
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    long payerAfter = getBalance(payerAccountId);
    long totalAfter = getTotalBalance();

    Assert.assertEquals(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, record.getReceipt().getStatus());
    Assert.assertEquals(totalBefore, totalAfter);
    // In unit testing, no fees, just gas used.  The offered value was not taken from the payer.
    Timestamp consensusTimeStamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
    long gasPrice = getContractCreateGasPriceInTinyBars(consensusTimeStamp);
    Assert.assertEquals(
            record.getContractCreateResult().getGasUsed() * gasPrice, payerBefore - payerAfter);
  }

  @Test
  @DisplayName("cd Create with valid initial value: succeeds")
  public void cd_ValidInitialBalance() throws NoFeeScheduleExistsException {
    long payerBefore = getBalance(payerAccountId);
    long totalBefore = getTotalBalance();

    // Create the contract
    byte[] contractBytes = createFile(FALLBACK_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(INITIAL_BALANCE_OFFERED, 250000L, null);
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();

    long payerAfter = getBalance(payerAccountId);
    long totalAfter = getTotalBalance();

    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertEquals(totalBefore, totalAfter);
    Timestamp consensusTimeStamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond())
        .build();
    long gasPrice = getContractCreateGasPriceInTinyBars(consensusTimeStamp);
    // In unit testing, no fees, just gas used.  The offered value was taken from the payer.
    Assert.assertEquals(
        record.getContractCreateResult().getGasUsed() * gasPrice + INITIAL_BALANCE_OFFERED,
        payerBefore - payerAfter);
  }

  @Test
  @DisplayName("da Opcode shl: Success")
  public void da_OpcodeShl() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to exercise SHL
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeOpShl(2, 4));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeOpShl(callResults);
    Assert.assertEquals(16, retVal);
  }

  @Test
  @DisplayName("db Opcode shr: Success")
  public void db_OpcodeShr() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to exercise SHR
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeOpShr(2, 4));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeOpShr(callResults);
    Assert.assertEquals(1, retVal);
  }

  @Test
  @DisplayName("dc Opcode sar: Success")
  public void dc_OpcodeSar() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to exercise SAR
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeOpSar(2, 4));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeOpSar(callResults);
    Assert.assertEquals(1, retVal);
  }

  @Test
  @DisplayName("dd Opcode extcodehash: Success")
  public void dd_OpcodeExtCodeHash() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();
    String contractSolidityAddress = Hex.encodeHexString(EntityIdUtils.asSolidityAddress(
        0, 0, newContractId.getContractNum()));

    // Call the contract to exercise EXTCODEHASH
    ByteString dataToGet = ByteString
        .copyFrom(SCEncoding.encodeOpExtCodeHash(contractSolidityAddress));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    byte[] retVal = SCEncoding.decodeOpExtCodeHash(callResults);
    // Test the local convention of returning the address, not a Keccak256 hash
    String returnedLast20Bytes = ByteUtil.toHexString(retVal).substring(24);
    Assert.assertEquals(contractSolidityAddress, returnedLast20Bytes);
  }

  @Test
  @DisplayName("de Opcode extcodehash: Account")
  public void de_OpcodeExtCodeHashAccount() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();
    String accountSolidityAddress = Hex.encodeHexString(EntityIdUtils.asSolidityAddress(
        0, 0, payerAccount));

    // Call the contract to exercise EXTCODEHASH
    ByteString dataToGet = ByteString
        .copyFrom(SCEncoding.encodeOpExtCodeHash(accountSolidityAddress));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    byte[] retVal = SCEncoding.decodeOpExtCodeHash(callResults);
    // Test the local convention of returning the empty hash for a regular account
    String returned = ByteUtil.toHexString(retVal);
    String expected = ByteUtil.toHexString(HashUtil.EMPTY_DATA_HASH);
    Assert.assertEquals(expected, returned);
  }

  // Test a contract created by another contract
  @Test
  @DisplayName("df Opcode extcodehash: Contract-created contract")
  public void df_OpcodeExtCodeHashCreated() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(CREATE_TRIVIAL_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to create another contract
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialCreate());
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to get the address of the created contract.
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeCreateTrivialGetAddress());
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    byte[] bytesSolidityAddress = SCEncoding.decodeCreateTrivialGetAddress(callResults);
    String innerContractSolidityAddress = ByteUtil.toHexString(bytesSolidityAddress);

    // Create the opcode contract with EXTCODEHASH
    contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    body = getCreateTransactionBody();
    consensusTime = new Date().toInstant();
    seqNumber = new SequenceNumber(secondContractSequenceNumber);
    ledger.begin();
    record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID opcodeContractId = record.getReceipt().getContractID();

    // Call the opcode contract to exercise EXTCODEHASH
    dataToGet = ByteString
        .copyFrom(SCEncoding.encodeOpExtCodeHash(innerContractSolidityAddress));
    cCLQuery = getCallLocalQuery(opcodeContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    byte[] retVal = SCEncoding.decodeOpExtCodeHash(callResults);
    // Test the local convention of returning the address, not a Keccak256 hash
    String returnedLast20Bytes = ByteUtil.toHexString(retVal).substring(24);
    Assert.assertEquals(innerContractSolidityAddress, returnedLast20Bytes);
  }

  @Test
  @DisplayName("dg Opcode extcodehash: Precompiled contract")
  public void dg_OpcodeExtCodeHashPrecompiled() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();
    String precompiledSolidityAddress = Hex.encodeHexString(EntityIdUtils.asSolidityAddress(0, 0, 3));

    // Call the contract to exercise EXTCODEHASH
    ByteString dataToGet = ByteString
        .copyFrom(SCEncoding.encodeOpExtCodeHash(precompiledSolidityAddress));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 0)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    byte[] retVal = SCEncoding.decodeOpExtCodeHash(callResults);
    // Finds existing account and returns not-contract, correct because there is no code at that address.
    String returned = ByteUtil.toHexString(retVal);
    String expected = ByteUtil.toHexString(HashUtil.EMPTY_DATA_HASH);
    Assert.assertEquals(expected, returned);
  }

  @Test
  @DisplayName("ea maxResultSize success")
  public void ea_MaxResultSizeSuccess() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to exercise SHL
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeOpShl(2, 4));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 1200)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeOpShl(callResults);
    Assert.assertEquals(16, retVal);
  }

  @Test
  @DisplayName("eb maxResultSize failure")
  public void eb_MaxResultSizeFailure() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(NEW_OPCODES_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to exercise SHL
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeOpShl(2, 4));
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L, 12)
        .getContractCallLocal();
    ContractCallLocalResponse response = smartHandler
        .contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED,
        response.getHeader().getNodeTransactionPrecheckCode());
    Assert.assertNotNull(response.getFunctionResult().getErrorMessage());
    Assert.assertTrue(response.getFunctionResult().getErrorMessage().length() > 0);

    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());
    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertEquals(0, callResults.length);
  }

  @AfterEach
  public void tearDown() throws Exception {
    try {
      repository.close();
    } catch (Throwable tx) {
      //do nothing now.
    } finally {
      repository = null;

    }
  }

  private long getBalance(AccountID accountId) {
    MerkleEntityId mk = new MerkleEntityId();
    mk.setNum(accountId.getAccountNum());
    mk.setRealm(0);
    mk.setShard(0);

    MerkleAccount mv = fcMap.get(mk);
    if (mv == null) {
      return 0;
    } else {
      return mv.getBalance();
    }
  }

  private long getTotalBalance() {
    long total = 0L;
    for (MerkleAccount val : fcMap.values()) {
      total += val.getBalance();
    }
    return total;
  }

  public long getContractCreateGasPriceInTinyBars(Timestamp at) {
    FeeData usagePrices = TEST_USAGE_PRICES.pricesGiven(HederaFunctionality.ContractCreate, at);
    long feeInTinyCents = usagePrices.getServicedata().getGas() / 1000;
    long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(rates.getCurrentRate(), feeInTinyCents);
    return feeInTinyBars == 0 ? 1 : feeInTinyBars;
  }


  @Test
  @DisplayName("cr Create In Constructor")
  public void cr_CreateInConstructor() {
    // Create the contract
    byte[] contractBytes = createFile(CREATE_IN_CONSTRUCTOR_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertNotEquals(0, record.getContractCreateResult().getContractID().getContractNum());
  }
}
