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
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.FeeCalcUtilsTest;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.unit.FCStorageWrapper;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.util.SCEncoding;
import static com.hedera.services.legacy.util.SCEncoding.*;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.SolidityLifecycleFactory;
import com.hedera.test.mocks.StorageSourceFactory;
import com.hedera.test.mocks.TestUsagePricesProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.unit.StorageKeyNotFoundException;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.contracts.sources.LedgerAccountsSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.swirlds.fcmap.FCMap;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections4.Predicate;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.Source;
import org.ethereum.db.ServicesRepositoryRoot;
import org.ethereum.solidity.Abi;
import org.ethereum.solidity.Abi.Event;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author oc/peter
 * @version Junit5 Tests the SmartContractRequestHandler class features
 */

@Disabled
@RunWith(JUnitPlatform.class)
public class SmartContractRequestHandlerStorageTest {
  public static final String SIMPLE_STORAGE_BIN = "/testfiles/simpleStorage.bin";
  public static final String CHILD_STORAGE_BIN = "/testfiles/ChildStorage.bin";
  public static final String SIMPLE_STORAGE_WITH_EVENTS_BIN = "/testfiles/SimpleStorageWithEvents.bin";
  public static final int SIMPLE_STORAGE_VALUE = 12345;
  private static final long payerAccount = 787L;
  private static final long nodeAccount = 3L;
  private static final long feeCollAccount = 9876L;
  private static final long contractFileNumber = 333L;
  private static final long contractSequenceNumber = 334L;
  private static final long secondContractSequenceNumber = 668L;
	// FSFC path constants
	public static String ADDRESS_PATH = "/{0}/s{1}";
	SmartContractRequestHandler smartHandler;
  FileServiceHandler fsHandler;
  FCMap<MerkleEntityId, MerkleAccount> contracts = null;
  private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap;
  ServicesRepositoryRoot repository;

  MerkleEntityId payerMerkleEntityId; // fcMap key for payer account
  byte[] payerKeyBytes = null; // Repository key for payer account
  AccountID payerAccountId;
  AccountID nodeAccountId;
  AccountID feeCollAccountId;
  FileID contractFileId;
  BigInteger gasPrice;
  private long selfID = 9870798L;
  private LedgerAccountsSource ledgerSource;
  private FCStorageWrapper storageWrapper;
  private HederaLedger ledger;

  private ServicesRepositoryRoot getLocalRepositoryInstance() {
    DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);
    TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
            AccountProperty.class,
            () -> new MerkleAccount(),
            new FCMapBackingAccounts(() -> contracts),
            new ChangeSummaryManager<>());
    ledger = new HederaLedger(
            mock(TokenStore.class),
            mock(ScheduleStore.class),
            mock(EntityIdSource.class),
            mock(ExpiringCreations.class),
            mock(AccountRecordsHistorian.class),
            delegate);
    ledgerSource = new LedgerAccountsSource(ledger, new MockGlobalDynamicProps());
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

    contracts = new FCMap<>();
    storageMap = new FCMap<>();
    createAccount(payerAccountId, 1_000_000_000L);
    createAccount(nodeAccountId, 10_000L);
    createAccount(feeCollAccountId, 10_000L);

    repository = getLocalRepositoryInstance();
    gasPrice = new BigInteger("1");
    HbarCentExchange exchange = mock(HbarCentExchange.class);
    long expiryTime = Long.MAX_VALUE;
    ExchangeRateSet rates = RequestBuilder
            .getExchangeRateSetBuilder(
                    1, 12,
                    expiryTime,
                    1, 15,
                    expiryTime);
    given(exchange.activeRates()).willReturn(rates);
    given(exchange.rate(any())).willReturn(rates.getCurrentRate());
    smartHandler = new SmartContractRequestHandler(
            repository,
            ledger,
            () -> contracts,
            null,
            exchange,
            TestUsagePricesProvider.TEST_USAGE_PRICES,
            () -> repository,
            SolidityLifecycleFactory.newTestInstance(),
            ignore -> true,
            null,
            new MockGlobalDynamicProps());
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
  }

  private void createAccount(AccountID payerAccount, long balance)
      throws NegativeAccountBalanceException {
    MerkleEntityId mk = new MerkleEntityId();
    mk.setNum(payerAccount.getAccountNum());
    mk.setRealm(0);
    MerkleAccount mv = new MerkleAccount();
    mv.setBalance(balance);
    contracts.put(mk, mv);
  }

  private byte[] createFile(String filePath, FileID fileId) {
    InputStream fis = SmartContractRequestHandlerStorageTest.class.getResourceAsStream(filePath);
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
    Transaction txn = RequestBuilder.getFileCreateBuilder(payerAccount, 0L, 0L,
        nodeAccount, 0L, 0L,
        100L, startTime, transactionDuration, generateRecord,
        memo, fileData, expTime, Collections.emptyList());

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
    Transaction txn = RequestBuilder.getCreateContractRequest(payerAccount, 0L, 0L,
        nodeAccount, 0L, 0L,
        100L, startTime, transactionDuration, generateRecord,
        memo, gas, contractFileId, ByteString.EMPTY, initialBalance,
        renewalDuration, sCMemo, adminKey);

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
    MerkleAccount mv = contracts.get(mk);
    Assert.assertNotNull(mv);
    Assert.assertNotNull(mv.getKey());
    Assert.assertNotNull(mv.getKey());

    String bytesPath = String.format("/%d/s%d", contractId.getRealmNum(), contractId.getContractNum());
    Assert.assertTrue(storageWrapper.fileExists(bytesPath));

    String sCMetaDataPath = String.format("/%d/m%d", contractId.getRealmNum(), contractId.getContractNum());
    Assert.assertFalse(storageWrapper.fileExists(sCMetaDataPath));
    String sCAdminKeyPath = String.format("/%d/a%d", contractId.getRealmNum(), contractId.getContractNum());
    Assert.assertFalse(storageWrapper.fileExists(sCAdminKeyPath));
  }

  private void checkContractDataArtifactExists(ContractID contractId) {
    String bytesPath = String.format("/%d/d%d", contractId.getRealmNum(), contractId.getContractNum());
    Assert.assertTrue(storageWrapper.fileExists(bytesPath));
  }

  @Test
  @DisplayName("createContract: Success")
  public void createContractWithAdminKey() {
    KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
    Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(0L, 250000L, adminPubKey);

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
  @DisplayName("createContract: No gas")
  public void createContractNoGas() {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(0L, 0L, null);

    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS, record.getReceipt().getStatus());
  }

  @Test
  @DisplayName("createContract: Insufficient gas")
  public void createContractInsufficientGas() {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();

    // Create a good contract to get the amount of gas needed
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();

    Assert.assertEquals(ResponseCodeEnum.SUCCESS, record.getReceipt().getStatus());
    Assert.assertTrue(record.hasContractCreateResult());
    long gasUsed = record.getContractCreateResult().getGasUsed();
    // Attempt to create a contract with a little less gas
    body = getCreateTransactionBody(0L, (long) Math.floor((double)gasUsed *0.9) , null);
    consensusTime = new Date().toInstant();
    seqNumber = new SequenceNumber(secondContractSequenceNumber);
    ledger.begin();
    record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS, record.getReceipt().getStatus());
  }

  @Test
  @DisplayName("createContract: Invalid initial balance")
  public void createContractInitialBalance() {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(100L, 250000, null);

    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, record.getReceipt().getStatus());
  }

  private TransactionBody getCallTransactionBody(ContractID newContractId,
      ByteString functionData, long gas, long value) {
    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()));
    Duration transactionDuration = RequestBuilder.getDuration(100);

    Transaction txn = RequestBuilder.getContractCallRequest(payerAccount, 0L, 0L,
        nodeAccount, 0L, 0L,
        100L /* fee */, startTime,
        transactionDuration, gas, newContractId,
        functionData, value);

    TransactionBody body = null;
    try {
      body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txn);
    } catch (InvalidProtocolBufferException e) {
      Assert.fail("Error calling contract: parsing transaction body");
    }
    return body;
  }

  @Test
  @DisplayName("ContractSetCall: Success")
  public void contractSetCall() {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to set value
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
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

    checkContractDataArtifactExists(newContractId);
  }

  @Test
  @DisplayName("ContractSetCall: Invalid contract ID")
  public void contractSetCallInvalidID() {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();
    // Call the contract to set value
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
    // Fail: wrong ID, should be newContractId
    body = getCallTransactionBody(
        RequestBuilder.getContractIdBuild(secondContractSequenceNumber, 0L, 0L), dataToSet,
        250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID, record.getReceipt().getStatus());
  }

  @Test
  @DisplayName("ContractSetCall: Value proferred to improper call")
  public void contractSetCallInvalidValue() {
    // Create the contract
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to set value
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
    // Fail: wrong ID, should be newContractId
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 100L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, record.getReceipt().getStatus());
  }

  @Test
  @DisplayName("ContractSetCall: Invalid call data")
  public void contractSetCallInvalidData() {
    // Create the contract
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to set value
    // Fail: contract call data is empty
    ByteString dataToSet = ByteString.EMPTY;
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, record.getReceipt().getStatus());
  }

  @Test
  @DisplayName("ContractSetCall: Insufficient gas")
  public void contractSetCallInsufficientGas() {
    // Create the contract
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to set value
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
    // Fail: not enough gas to call
    body = getCallTransactionBody(newContractId, dataToSet, 20L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS, record.getReceipt().getStatus());
  }

  private Query getCallLocalQuery(ContractID newContractId, ByteString functionData, long gas) {
    Transaction transferTransaction = TestHelper.createTransferUnsigned(payerAccountId,
        feeCollAccountId, payerAccountId, nodeAccountId, 100000L /* amount */);
    return RequestBuilder.getContractCallLocalQuery(newContractId, gas,
        functionData, 0L /* value */, 5000L /* maxResultSize */,
        transferTransaction, ResponseType.ANSWER_ONLY);
  }

  @Test
  @DisplayName("ContractGetCall: Success")
  public void contractGetCall() throws Exception {
    // Create the contract
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to set value
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    // Call the contract to get the value back
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeGetValue());
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L)
        .getContractCallLocal();
    seqNumber.getAndIncrement();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeGetValueResult(callResults);
    Assert.assertEquals(SIMPLE_STORAGE_VALUE, retVal);
  }

  private TransactionBody getUpdateTransactionBody(ContractID contractId, String contractMemo,
      Duration renewalDuration, Timestamp expirationTime) {
    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()));
    Duration transactionDuration = RequestBuilder.getDuration(100);
    boolean generateRecord = true;
    String memo = "SmartContract update";
    Transaction txn = RequestBuilder.getContractUpdateRequest(payerAccountId, nodeAccountId,
        100L /* fee */, startTime, transactionDuration, generateRecord, memo,
        contractId, renewalDuration, null /* admin keys */, null /* proxy acct */,
        expirationTime, contractMemo);

    TransactionBody body = null;
    try {
      body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txn);
    } catch (InvalidProtocolBufferException e) {
      Assert.fail("Error updating contract: parsing transaction body");
    }
    return body;
  }

  @Test
  @DisplayName("UpdateContract: Success")
  public void updateContract() throws Exception {
    KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
    Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody(0L, 250000L, adminPubKey);

    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    String newMemo = "Changed SmartContractMemo";
    Duration renewalDuration = RequestBuilder.getDuration(50_000);
    Timestamp expirationTime = RequestBuilder.getTimestamp(Instant.now().plusSeconds(1_000_000_000L));

    body = getUpdateTransactionBody(newContractId, newMemo, renewalDuration, expirationTime);
    consensusTime = new Date().toInstant();
    ledger.begin();
    record = smartHandler.updateContract(body, consensusTime);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    var contract = contracts.get(MerkleEntityId.fromContractId(newContractId));
    Assert.assertEquals(newMemo, contract.getMemo());
    Assert.assertEquals(renewalDuration.getSeconds(), contract.getAutoRenewSecs());
    Assert.assertEquals(expirationTime.getSeconds(), contract.getExpiry());
  }

  @Test
  @DisplayName("UpdateContract: immutable")
  public void updateContractImmutable() {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();

    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Information to change
    String newMemo = "Changed SmartContractMemo";
    Duration renewalDuration = RequestBuilder.getDuration(50_000);
    Timestamp expirationTime = RequestBuilder.getTimestamp(Instant.now().plusSeconds(1_000_000_000L));
    body = getUpdateTransactionBody(newContractId, newMemo, renewalDuration, expirationTime);
    consensusTime = new Date().toInstant();
    ledger.begin();
    record = smartHandler.updateContract(body, consensusTime);
    ledger.commit();
    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT, record.getReceipt().getStatus());
  }

  @Test
  @DisplayName("ChildStorage call")
  public void childStorageCall() throws Exception {
    byte[] contractBytes = createFile(CHILD_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    var createRecord = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    var childStorageId = createRecord.getReceipt().getContractID();

    var cclQuery = getCallLocalQuery(
            childStorageId,
            ByteString.copyFrom(encodeVia(GET_MY_VALUE_ABI)),
            250000L).getContractCallLocal();
    var response = smartHandler.contractCallLocal(cclQuery, System.currentTimeMillis());
    byte[] responseBytes = response.getFunctionResult().getContractCallResult().toByteArray();

    var callBytes = ByteString.copyFrom(encodeVia(GROW_CHILD_ABI, 0, 1, 17));
    body = getCallTransactionBody(childStorageId, callBytes, 250000L, 0L);
    seqNumber.getAndIncrement();
    ledger.begin();
    var callRecord = smartHandler.contractCall(body, Instant.now(), seqNumber);
    ledger.commit();
  }

  @Test
  @DisplayName("ContractSetCall with event")
  public void contractSetCallWithEvent() throws Exception {
    byte[] contractBytes = createFile(SIMPLE_STORAGE_WITH_EVENTS_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();

    // Call the contract to set value and trigger the event
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertTrue(validateSetRecord(newContractId, SIMPLE_STORAGE_VALUE, record));

    // Call the contract to get the value back
    ByteString dataToGet = ByteString.copyFrom(SCEncoding.encodeGetValue());
    ContractCallLocalQuery cCLQuery = getCallLocalQuery(newContractId, dataToGet, 250000L)
        .getContractCallLocal();
    seqNumber.getAndIncrement();
    ContractCallLocalResponse response = smartHandler.contractCallLocal(cCLQuery, System.currentTimeMillis());
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getFunctionResult().getContractCallResult());

    byte[] callResults = response.getFunctionResult().getContractCallResult().toByteArray();
    Assert.assertNotNull(callResults);
    Assert.assertTrue(callResults.length > 0);
    int retVal = SCEncoding.decodeGetValueResult(callResults);
    Assert.assertEquals(SIMPLE_STORAGE_VALUE, retVal);
  }

  @Test
  @DisplayName("ContractSetCallEmptyByteCode: Failure")
  public void contractSetCallEmptyByteCode() throws StorageKeyNotFoundException {
    // Create the contract
    byte[] contractBytes = createFile(SIMPLE_STORAGE_BIN, contractFileId);
    TransactionBody body = getCreateTransactionBody();
    Instant consensusTime = new Date().toInstant();
    SequenceNumber seqNumber = new SequenceNumber(contractSequenceNumber);
    ledger.begin();
    TransactionRecord record = smartHandler.createContract(body, consensusTime, contractBytes, seqNumber);
    ledger.commit();
    ContractID newContractId = record.getReceipt().getContractID();
    String byteCodePath = FeeCalcUtilsTest.buildPath(
        ADDRESS_PATH, Long.toString(newContractId.getRealmNum()),
        Long.toString(newContractId.getContractNum()));
    storageWrapper.delete(byteCodePath, 0, 0);
    // Call the contract to set value
    ByteString dataToSet = ByteString.copyFrom(SCEncoding.encodeSet(SIMPLE_STORAGE_VALUE));
    body = getCallTransactionBody(newContractId, dataToSet, 250000L, 0L);
    consensusTime = new Date().toInstant();
    seqNumber.getAndIncrement();
    ledger.begin();
    record = smartHandler.contractCall(body, consensusTime, seqNumber);
    ledger.commit();

    Assert.assertNotNull(record);
    Assert.assertNotNull(record.getTransactionID());
    Assert.assertNotNull(record.getReceipt());
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY, record.getReceipt().getStatus());
    Assert.assertEquals(contractSequenceNumber, record.getReceipt().getContractID().getContractNum());
  }

  private boolean validateSetRecord(ContractID contractCalled, int valuePassed,
      TransactionRecord setRecord) {
    boolean retValue = false;
    if (setRecord.hasContractCallResult()) {
      ContractFunctionResult setResults = setRecord.getContractCallResult();
      List<ContractLoginfo> logs = setResults.getLogInfoList();
      for (ContractLoginfo currLog : logs) {
        ContractID logContractId = currLog.getContractID();
        assert (logContractId.equals(contractCalled));
        ByteString logdata = currLog.getData();
        byte[] dataArr = {};
        if (logdata != null) {
          dataArr = logdata.toByteArray();
        }
        List<ByteString> topicsBstr = currLog.getTopicList();
        int topicSize = 0;
        if (topicsBstr != null) {
          topicSize = topicsBstr.size();
        }
        byte[][] topicsArr = new byte[topicSize][];
        for (int topicIndex = 0; topicIndex < topicsBstr.size(); topicIndex++) {
          topicsArr[topicIndex] = topicsBstr.get(topicIndex).toByteArray();
        }

        Event storedEvnt = getStoredEvent();
        List<?> eventData = storedEvnt.decode(dataArr, topicsArr);
        BigInteger valueFromEvent = (BigInteger) eventData.get(1);
        byte[] senderAddress = (byte[]) eventData.get(0);
        String senderAddrInStrFormat = ByteUtil.toHexString(senderAddress);
        assert (valueFromEvent.intValue() == valuePassed);
        retValue = true;
      }
    }
    return retValue;
  }

  private static Event getStoredEvent() {
    Abi abi = Abi.fromJson(SCEncoding.SC_ALL_ABI);
    Predicate<Event> searchEventPredicate = sep -> {
      return sep.name.equals("Stored");
    };

    return abi.findEvent(searchEventPredicate);
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
}
