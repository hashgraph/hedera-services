package com.hedera.services.legacy.unit.service;

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

import static com.hedera.services.context.ServicesNodeType.STAKED_NODE;
import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static com.hedera.test.mocks.TestExchangeRates.TEST_EXCHANGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.service.SmartContractServiceImpl;
import com.hedera.services.legacy.util.MockStorageWrapper;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.RecordCache;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.SolidityLifecycleFactory;
import com.hedera.test.mocks.StorageSourceFactory;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.mocks.TestExchangeRates;
import com.hedera.test.mocks.TestFeesFactory;
import com.hedera.test.mocks.TestProperties;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.unit.PropertyLoaderTest;
import com.hedera.services.legacy.unit.handler.SolidityAddress;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.legacy.unit.handler.StoragePersistenceImpl;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.swirlds.common.Platform;
import com.swirlds.fcmap.FCMap;
import io.grpc.stub.StreamObserver;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.Source;
import org.ethereum.db.ServicesRepositoryRoot;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * @author oc
 * @version Junit5 Tests the SmartContractServiceImpl class features
 */

@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmartContractServiceImpl Test Suite")
public class SmartContractServiceImplTest {
	MockStorageWrapper mockStorageWrapper;
	StreamObserver<TransactionResponse> responseObserver = null;
	long payerAccount;
	long nodeAccount;
	long feeAccount;
	long DAY_SEC = 24 * 60 * 60;
	long DEFAULT_CONTRACT_OP_GAS = 1000000l;
	FCMap<MerkleEntityId, MerkleAccount> accountFCMap = null;
	FCMap<MerkleEntityId, MerkleTopic> topicFCMap = null;
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap;
	ServicesRepositoryRoot repository;
	SmartContractServiceImpl smartContractImpl = null;
	TransactionHandler transactionHandler = null;
	RecordCache recordCache = new RecordCache(
			null,
			CacheBuilder.newBuilder().build(),
			new HashMap<>());
	SmartContractRequestHandler smartContractHandler = null;
	@Mock
	Platform platform;
	BigInteger gasPrice;
	KeyPair genKpair;
	byte[] genPubKey;
	String pubKeyStr;
	List<KeyPair> keys;
	Map<AccountID, List<KeyPair>> account2keyMap = new HashMap<>();
	TransactionResponse tResponse = null;
	String tempSolidityId = "9f70327fd873cacfbb14b67d9069e0b793699eae";
	private AccountID payerAccountId;
	private AccountID nodeAccountId;
	private AccountID feeCollectionAccountId;
	private AccountID senderAccountId;
	private AccountID receiverAccountId;
	private LedgerAccountsSource ledgerSource;

	@Mock
	private HederaNodeStats hederaNodeStats;

	@BeforeAll
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		payerAccount = TestHelper.getRandomLongAccount(1000);
		nodeAccount = TestHelper.getRandomLongAccount(100);
		feeAccount = TestHelper.getRandomLongAccount(900);
		payerAccountId = RequestBuilder.getAccountIdBuild(payerAccount, 0l, 0l);
		nodeAccountId = RequestBuilder.getAccountIdBuild(nodeAccount, 0l, 0l);
		feeCollectionAccountId = RequestBuilder.getAccountIdBuild(feeAccount, 0l, 0l);

		System.out.println("Payer Account:" + payerAccountId);
		System.out.println("Node Account:" + nodeAccountId);
		senderAccountId = RequestBuilder.getAccountIdBuild(9999l, 0l, 0l);
		receiverAccountId = RequestBuilder.getAccountIdBuild(8888l, 0l, 0l);
		storageMap = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());
		// Init FCMap & Put Balances
		accountFCMap = new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
		topicFCMap = new FCMap<>(new MerkleEntityId.Provider(), new MerkleTopic.Provider());
		MerkleEntityId mk = new MerkleEntityId();
		mk.setNum(payerAccount);
		mk.setRealm(0);

		MerkleAccount mv = new MerkleAccount();
		mv.setBalance(500000000000l);
		accountFCMap.put(mk, mv);
		SolidityAddress solAddress = new SolidityAddress(tempSolidityId);
		MerkleEntityId solMerkleEntityId = new MerkleEntityId(0l, 0l, 9999l);

		DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
				AccountProperty.class,
				() -> new MerkleAccount(),
				new FCMapBackingAccounts(() -> accountFCMap),
				new ChangeSummaryManager<>());
		HederaLedger ledger = new HederaLedger(
				mock(EntityIdSource.class),
				mock(ExpiringCreations.class),
				mock(AccountRecordsHistorian.class),
				delegate);
		ledgerSource = new LedgerAccountsSource(ledger, TestProperties.TEST_PROPERTIES);
		Source<byte[], AccountState> accountSource = ledgerSource;
		repository = new ServicesRepositoryRoot(accountSource, repDBFile);
		repository.setStoragePersistence(new StoragePersistenceImpl(storageMap));

		responseObserver = new StreamObserver<>() {
			@Override
			public void onNext(TransactionResponse response) {
				tResponse = response;

				if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
					System.out.println("System OK");
				} else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY) {
					System.out.println("System BUSY");
				} else {
					System.out.println("System BAD");
					Assert.assertNotNull(response.getNodeTransactionPrecheckCode());
				}
			}

			@Override
			public void onError(Throwable t) {
				System.out.println("Error Happened " + t.getMessage());
			}

			@Override
			public void onCompleted() {
				System.out.println("Completed" + tResponse);
			}

		};
		PropertyLoaderTest.populatePropertiesWithConfigFilesPath(
				"../../../configuration/dev/application.properties",
				"../../../configuration/dev/api-permission.properties");
		gasPrice = new BigInteger("0");
		mockStorageWrapper = new MockStorageWrapper();

		PrecheckVerifier precheckVerifier = mock(PrecheckVerifier.class);
		given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
		var policies = new SystemOpPolicies(new MockEntityNumbers());
		transactionHandler = new TransactionHandler(
				recordCache,
				() -> accountFCMap,
				nodeAccountId,
				precheckVerifier,
				TEST_USAGE_PRICES,
				TestExchangeRates.TEST_EXCHANGE,
				TestFeesFactory.FEES_FACTORY.get(),
				() -> new StateView(() -> topicFCMap, () -> accountFCMap),
				new BasicPrecheck(TestProperties.TEST_PROPERTIES, TestContextValidator.TEST_VALIDATOR),
				new QueryFeeCheck(() -> accountFCMap),
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies));
		HbarCentExchange exchange = mock(HbarCentExchange.class);
		long expiryTime = Long.MAX_VALUE;
		ExchangeRateSet rates = RequestBuilder
				.getExchangeRateSetBuilder(
						1, 12,
						expiryTime,
						1, 15,
						expiryTime);
		given(exchange.activeRates()).willReturn(rates);
		smartContractHandler = new SmartContractRequestHandler(
				repository,
				feeCollectionAccountId,
				ledger,
				() -> accountFCMap,
				() -> storageMap,
				ledgerSource,
				null,
				exchange,
				TEST_USAGE_PRICES,
				TestProperties.TEST_PROPERTIES,
				() -> repository,
				SolidityLifecycleFactory.newTestInstance(),
				ignore -> true,
				null);

		genKpair = new KeyPairGenerator().generateKeyPair();
		genPubKey = ((EdDSAPublicKey) genKpair.getPublic()).getAbyte();
		pubKeyStr = MiscUtils.commonsBytesToHex(genPubKey);
		keys = new ArrayList<>();
		keys.add(genKpair);
		account2keyMap.put(payerAccountId, keys);
		KeyPair p1 = new KeyPairGenerator().generateKeyPair();
		KeyPair p2 = new KeyPairGenerator().generateKeyPair();

		List<KeyPair> keys1 = new ArrayList<>();

		keys1.add(p1);
		keys1.add(p2);
		account2keyMap.put(senderAccountId, keys1);
	}

	@BeforeEach
	@DisplayName("Checking Objects & If they changed")
	public void doSanityChecks() {

	}

	/**
	 * Prepares a test case specific transaction & returns it
	 */
	public Transaction getDummyTransaction(String action) {

		// Long payerAccountNum = 111l;
		Long payerRealmNum = 0l;
		Long payerShardNum = 0l;
		// Long nodeAccountNum=123l;
		Long nodeRealmNum = 0l;
		Long nodeShardNum = 0l;
		long transactionFee = 0l;
		Timestamp startTime =
				RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(100);
		boolean generateRecord = false;
		String memo = "UnitTesting";
		int thresholdValue = 10;
		List<Key> keyList = new ArrayList<>();
		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key akey =
				Key.newBuilder().setEd25519(ByteString.copyFromUtf8((MiscUtils.commonsBytesToHex(pubKey)))).build();
		PrivateKey priv = pair.getPrivate();
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
		int proxyFraction = 10;
		int maxReceiveProxyFraction = 10;
		long shardID = 0l;
		long realmID = 0l;

		Transaction trx = null;
		SignatureList sigList = SignatureList.getDefaultInstance();
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();

		/**
		 * SignatureList sigList = SignatureList.getDefaultInstance(); Transaction transferTx =
		 * RequestBuilder.getCryptoTransferRequest( payer.getAccountNum(), payer.getRealmNum(),
		 * payer.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
		 * nodeAccount.getShardNum(), 800, timestamp, transactionDuration, false, "test", sigList,
		 * payer.getAccountNum(), -100l, nodeAccount.getAccountNum(), 100l); transferTx =
		 * TransactionSigner.signTransaction(transferTx, accountKeys.get(payer));
		 */

		if ("SolidityIDQuery".equalsIgnoreCase(action)) {
			trx = RequestBuilder.getCryptoTransferRequest(payerAccountId.getAccountNum(),
					payerAccountId.getRealmNum(), payerAccountId.getShardNum(), nodeAccountId.getAccountNum(),
					nodeAccountId.getRealmNum(), nodeAccountId.getShardNum(), 800, timestamp,
					transactionDuration, false, "test", sigList, payerAccountId.getAccountNum(), -100l,
					nodeAccountId.getAccountNum(), 100l);
			// trx = TransactionSigner.signTransaction(trx, account2keyMap.get(payerAccountId));
		}

		if ("createContract".equalsIgnoreCase(action)) {
			FileID fileID = FileID.newBuilder().setFileNum(9999l).setRealmNum(1l).setShardNum(2l).build();

			trx = RequestBuilder.getCreateContractRequest(payerAccountId.getAccountNum(),
					payerAccountId.getRealmNum(), payerAccountId.getShardNum(), nodeAccountId.getAccountNum(),
					nodeAccountId.getRealmNum(), nodeAccountId.getShardNum(), 50000000000l, timestamp,
					transactionDuration, true, "createContract", DEFAULT_CONTRACT_OP_GAS, fileID,
					ByteString.EMPTY, 0, transactionDuration,
					SignatureList.newBuilder().addSigs(
							Signature.newBuilder().setEd25519(ByteString.copyFrom("testsignature".getBytes())))
							.build(),
					"");
		}

		// if("SolidityIDQuery".equalsIgnoreCase(action)) {
		// long durationInSeconds = DAY_SEC * 30;
		// * Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
		// * Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		// * Duration transactionDuration = RequestBuilder.getDuration(30, 0);
		// * Transaction createContractRequest =
		// RequestBuilder.getCreateContractRequest(payerAccountId.getAccountNum(),
		// * payerAccountId.getRealmNum(), payerAccountId.getShardNum(), nodeAccountId.getAccountNum(),
		// * nodeAccountId.getRealmNum(), nodeAccountId.getShardNum(), 100l, timestamp,
		// transactionDuration, true, "createContract",
		// * DEFAULT_CONTRACT_OP_GAS, contractFile, ByteString.EMPTY, 0, contractAutoRenew,
		// * SignatureList.newBuilder()
		// *
		// .addSigs(Signature.newBuilder().setEd25519(ByteString.copyFrom("testsignature".getBytes())))
		// * .build());
		// }

		return trx;

	}

	/**
	 *
	 */
	public Query getDummyQuery(String action, Transaction trx, String solidityId) {

		Query query = null;
		if ("SolidityIDQuery".equalsIgnoreCase(action)) {
			query = RequestBuilder.getBySolidityIDQuery(solidityId, trx, ResponseType.ANSWER_ONLY);
		}

		return query;

	}

	public Object process(Mock annotation, Field field) {
		MockSettings mockSettings = Mockito.withSettings();
		if (annotation.extraInterfaces().length > 0) { // never null
			mockSettings.extraInterfaces(annotation.extraInterfaces());
		}
		if ("".equals(annotation.name())) {
			mockSettings.name(field.getName());
		} else {
			mockSettings.name(annotation.name());
		}
		if (annotation.serializable()) {
			mockSettings.serializable();
		}

		// see @Mock answer default value
		mockSettings.defaultAnswer(annotation.answer().get());
		return Mockito.mock(field.getType(), mockSettings);
	}

	@Test
	@Disabled
	@DisplayName("1A Creates Contract & tests")
	public void aa_createContract() {

		Transaction trx = getDummyTransaction("createContract");

		MockSettings mockSettings =
				new MockSettingsImpl<>().defaultAnswer(RETURNS_SMART_NULLS).stubOnly();

		Platform platform = Mockito.mock(Platform.class);
		when(platform.createTransaction(new com.swirlds.common.Transaction(trx.toByteArray())))
				.thenReturn(true);

		smartContractImpl = new SmartContractServiceImpl(
				platform, transactionHandler,
				smartContractHandler,
				hederaNodeStats,
				TEST_USAGE_PRICES,
				TEST_EXCHANGE,
				STAKED_NODE,
				null,
				null);

		smartContractImpl.createContract(trx, responseObserver);

		verify(hederaNodeStats, times(1)).smartContractTransactionReceived("createContract");
		verify(hederaNodeStats, times(1)).smartContractTransactionSubmitted("createContract");
	}

	@AfterAll
	public void tearDown() throws Exception {
		try {
			repository.close();
		} catch (Throwable tx) {
			tx.printStackTrace();
		} finally {
			mockStorageWrapper = null;
		}
	}

	@Nested
	@DisplayName("2.Get the Smart Contract Details")
	class OnceCreatedGetSolidityDetails {
		@Test
		public void aa_getSolidityIdTesNotSupported() {
			Transaction trx = getDummyTransaction("SolidityIDQuery");
			String solidityId =
					"f1876d5ecde2da7f0e4dd1b34e850ce24133f01e83dac3aba9db7af508007f8ee482a610055ccaf5b3bfbe50a7c7ece5";
			Query getBySolidityIdQuery = getDummyQuery("SolidityIDQuery", trx, solidityId);
			MockSettings mockSettings = new MockSettingsImpl<>().defaultAnswer(RETURNS_SMART_NULLS).stubOnly();

			Platform platform = Mockito.mock(Platform.class);

			smartContractImpl = new SmartContractServiceImpl(platform, transactionHandler,
					smartContractHandler, hederaNodeStats,
					TEST_USAGE_PRICES, TEST_EXCHANGE, STAKED_NODE, null, null);

			StreamObserver<Response> respOb = new StreamObserver<Response>() {

				@Override
				public void onNext(Response response) {
					if (response != null) {
						GetBySolidityIDResponse res = response.getGetBySolidityID();
						Assert.assertEquals(ResponseCodeEnum.NOT_SUPPORTED,
								res.getHeader().getNodeTransactionPrecheckCode());
						System.out.println("***RESPONSE***" + res);
					} else {
						System.out.println("***RESPONSE***" + response);
					}
				}

				@Override
				public void onError(Throwable t) {
					System.out.println("Error Happened " + t.getMessage());
				}

				@Override
				public void onCompleted() {
					System.out.println("Completed");
				}

			};

			smartContractImpl.getBySolidityID(getBySolidityIdQuery, respOb);
		}
	}
}
