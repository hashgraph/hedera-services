package com.hedera.services.legacy.unit;

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

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.context.ContextPlatformStatus;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.unit.handler.FeeScheduleInterceptor;
import com.hedera.services.legacy.unit.handler.FileServiceHandler;
import com.hedera.services.legacy.util.MockStorageWrapper;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.RecordCache;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.mocks.TestFeesFactory;
import com.hedera.test.mocks.TestProperties;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.swirlds.common.PlatformStatus;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.fcmap.FCMap;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static com.hedera.test.mocks.TestExchangeRates.TEST_EXCHANGE;
import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
@TestInstance(Lifecycle.PER_CLASS)
class PreCheckValidationTest {

	@BeforeAll
	@BeforeClass
	public static void setupAll() {
		SettingsCommon.transactionMaxBytes = 1_234_567;
	}

	private PrecheckVerifier precheckVerifier;
	long payerAccountInitialBalance = 1000000000;
	private MockStorageWrapper storageWrapper = new MockStorageWrapper();
	private RecordCache recordCache = new RecordCache(
			null,
			CacheBuilder.newBuilder().build(),
			new HashMap<>());
	private FCMap<MerkleEntityId, MerkleAccount> accountFCMap =
			new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
	FCMap<MerkleEntityId, MerkleTopic> topicFCMap = new FCMap<>(new MerkleEntityId.Provider(),
			new MerkleTopic.Provider());
	private AccountID nodeAccount = AccountID.newBuilder().setAccountNum(3).setRealmNum(0).setShardNum(0).build();
	private AccountID payerAccount = AccountID.newBuilder().setAccountNum(300).setRealmNum(0).setShardNum(0).build();
	private KeyPair payerKeyGenerated = new KeyPairGenerator().generateKeyPair();
	private TransactionHandler transactionHandler;
	private FileServiceHandler fileServiceHandler;

	private long getCalculatedTransactionFee(Transaction tr, List<PrivateKey> keys, List<PublicKey> publicKeys,
			TransactionHandler trHandler) {
		long feeToreturn = 0L;
		try {
			Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(tr, keys, publicKeys);
			TransactionBody txBody = CommonUtils.extractTransactionBody(signedTransaction);
			int totalSignatureCount = FeeBuilder.getSignatureCount(signedTransaction);
			int signatureSize = FeeBuilder.getSignatureSize(signedTransaction);
			int payerAcctSigCount = keys.size();
			SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
					signatureSize);
			feeToreturn = getTransactionFee(txBody, sigValueObj);
		} catch (Exception e) {
		}
		return feeToreturn;
	}

	public long getTransactionFee(TransactionBody txn, SigValueObj sigValueObj) throws Exception {
		HederaFunctionality function = MiscUtils.functionOf(txn);
		Timestamp at = txn.getTransactionID().getTransactionValidStart();
		FeeData prices = TEST_USAGE_PRICES.pricesGiven(function, at);
		FeeData usageMetrics = FeeDataLookups.computeUsageMetrics(txn, fileServiceHandler, accountFCMap, topicFCMap,
				sigValueObj);

		return FeeBuilder.getTotalFeeforRequest(prices, usageMetrics, TEST_EXCHANGE.rate(at));
	}

	private Transaction createPossibleTransaction() throws Exception {
		KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper.createAccount(payerAccount, nodeAccount, keyGenerated, 30);

		// calculate fee required
		long correctFee = getCalculatedTransactionFee(transaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()), transactionHandler);
		TransactionBody trBody = CommonUtils.extractTransactionBody(transaction);
		trBody = trBody.toBuilder().setTransactionFee(correctFee).build();
		transaction = transaction.toBuilder().setBodyBytes(trBody.toByteString()).build();
		return transaction;
	}

	@BeforeAll
	void initializeState() throws Exception {
		FeeScheduleInterceptor feeScheduleInterceptor = mock(FeeScheduleInterceptor.class);
		fileServiceHandler = new FileServiceHandler(storageWrapper, feeScheduleInterceptor, new ExchangeRates());

		precheckVerifier = mock(PrecheckVerifier.class);
		var policies = new SystemOpPolicies(new MockEntityNumbers());
		var platformStatus = new ContextPlatformStatus();
		platformStatus.set(PlatformStatus.ACTIVE);
		PropertySource propertySource = mock(PropertySource.class);
		transactionHandler = new TransactionHandler(
				recordCache,
				() -> accountFCMap,
				nodeAccount,
				precheckVerifier,
				TestFeesFactory.FEES_FACTORY.get(),
				() -> new StateView(() -> topicFCMap, () -> accountFCMap, propertySource, null),
				new BasicPrecheck(TestContextValidator.TEST_VALIDATOR, new MockGlobalDynamicProps()),
				new QueryFeeCheck(() -> accountFCMap),
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies),
				platformStatus);
		PropertyLoaderTest.populatePropertiesWithConfigFilesPath(
				"./configuration/dev/application.properties",
				"./configuration/dev/api-permission.properties");
		byte[] pubKey = ((EdDSAPublicKey) payerKeyGenerated.getPublic()).getAbyte();

		onboardAccount(payerAccount, pubKey, payerAccountInitialBalance);
	}

	@BeforeEach
	private void setup() throws Exception {
		given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
	}

	private void onboardAccount(AccountID account, byte[] publicKey, long initialBalance)
			throws Exception {
		NodeAccountsCreation.insertAccount(initialBalance, MiscUtils.commonsBytesToHex(publicKey), account, accountFCMap
		);
	}

	@Test
	void testCreateAccountPreCheckPositive() throws Exception {
		Transaction origTransaction = createPossibleTransaction();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		Assertions.assertEquals(OK, result.getValidity());
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testCreateAccountPreCheckPayerAccountNotFound() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);

		TransactionID trId = trBody.getTransactionID();
		AccountID acctId = trId.getAccountID();
		// change account Id to non existent account number
		acctId = acctId.toBuilder().setAccountNum(90001).build();
		trId = trId.toBuilder().setAccountID(acctId).build();
		trBody = trBody.toBuilder().setTransactionID(trId).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testNodeAccountNotFound() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		AccountID wrongNodeAccountID =
				AccountID.newBuilder().setAccountNum(92300).setRealmNum(0).setShardNum(0).build();
		trBody = trBody.toBuilder().setNodeAccountID(wrongNodeAccountID).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_NODE_ACCOUNT);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testInvalidTransactionStart() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		TransactionID trId = trBody.getTransactionID();
		Timestamp trValidStart = trId.getTransactionValidStart();
		// put validstart into future time
		trValidStart = trValidStart.toBuilder().setSeconds(trValidStart.getSeconds() + 3600).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		trBody = trBody.toBuilder().setTransactionID(trId).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_TRANSACTION_START);
	}

	@Test
	void testInvalidTransactionDuration() throws Exception {
		// send duration more than 5sec
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		Duration validDuration = trBody.getTransactionValidDuration();
		validDuration = validDuration.toBuilder().setSeconds(6000).build();
		trBody = trBody.toBuilder().setTransactionValidDuration(validDuration).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_TRANSACTION_DURATION);
		assert (result.getRequiredFee() == 0L);

		// send duration less than 5sec
		origTransaction = createPossibleTransaction();
		trBody = CommonUtils.extractTransactionBody(origTransaction);
		validDuration = trBody.getTransactionValidDuration();
		validDuration = validDuration.toBuilder().setSeconds(3).build();
		trBody = trBody.toBuilder().setTransactionValidDuration(validDuration).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_TRANSACTION_DURATION);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testInvalidSignature() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		// sign transaction with key that is different
		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(new KeyPairGenerator().generateKeyPair().getPrivate()),
				Collections.singletonList(new KeyPairGenerator().generateKeyPair().getPublic()));
		assert (signedTransaction != null);
		given(precheckVerifier.hasNecessarySignatures(any())).willReturn(false);
		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_SIGNATURE);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testInvalidMemoTooLong() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		trBody = trBody.toBuilder().setMemo(StringUtils.repeat("*", 101)).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.MEMO_TOO_LONG);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testInsufficientTransactionFee() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		long correctFee = trBody.getTransactionFee();
		trBody = trBody.toBuilder().setTransactionFee(correctFee - 1).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INSUFFICIENT_TX_FEE);
		assert (result.getRequiredFee() == correctFee);
	}

	@Test
	void testInsufficientPayerBalance() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		TransactionID trId = trBody.getTransactionID();
		AccountID acctId = trId.getAccountID();
		// change account Id to non existent account number
		acctId = acctId.toBuilder().setAccountNum(900089).build();
		trId = trId.toBuilder().setAccountID(acctId).build();
		trBody = trBody.toBuilder().setTransactionID(trId).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();
		long correctFee = trBody.getTransactionFee();

		// onboard account with balance less than fee
		KeyPair accountKey = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) accountKey.getPublic()).getAbyte();

		onboardAccount(acctId, pubKey, (correctFee - 1) / 100);
		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(accountKey.getPrivate()),
				Collections.singletonList(accountKey.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testThrottled() throws Exception {
		Transaction origTransaction = createPossibleTransaction();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		PrecheckVerifier precheckVerifier = mock(PrecheckVerifier.class);
		given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
		var policies = new SystemOpPolicies(new MockEntityNumbers());
		var platformStatus = new ContextPlatformStatus();
		platformStatus.set(PlatformStatus.ACTIVE);
		PropertySource propertySource = mock(PropertySource.class);
		TransactionHandler localTransactionHandler = new TransactionHandler(
				recordCache,
				() -> accountFCMap,
				nodeAccount,
				precheckVerifier,
				TestFeesFactory.FEES_FACTORY.get(),
				() -> new StateView(() -> topicFCMap, () -> accountFCMap, propertySource, null),
				new BasicPrecheck(TestContextValidator.TEST_VALIDATOR, new MockGlobalDynamicProps()),
				new QueryFeeCheck(() -> accountFCMap),
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies),
				platformStatus);
		localTransactionHandler.setThrottling(function -> true);
		TxnValidityAndFeeReq result =
				localTransactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.BUSY);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testCreateDuplicate() throws Exception {
		Transaction origTransaction = createPossibleTransaction();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);
		TransactionBody body = CommonUtils.extractTransactionBody(origTransaction);
		TransactionID trId = body.getTransactionID();
		RecordCache localRecordCache = new RecordCache(
				null,
				CacheBuilder.newBuilder().build(),
				new HashMap<>());
		TransactionReceipt txReceipt = RequestBuilder.getTransactionReceipt(OK);
		TransactionRecord transactionRecord =
				TransactionRecord.newBuilder().setReceipt(txReceipt).build();
		localRecordCache.setPostConsensus(
				trId,
				transactionRecord.getReceipt().getStatus(),
				ExpirableTxnRecord.fromGprc(transactionRecord));
		PrecheckVerifier precheckVerifier = mock(PrecheckVerifier.class);
		given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
		var policies = new SystemOpPolicies(new MockEntityNumbers());
		var platformStatus = new ContextPlatformStatus();
		platformStatus.set(PlatformStatus.ACTIVE);
		PropertySource propertySource = mock(PropertySource.class);
		TransactionHandler localTransactionHandler = new TransactionHandler(
				localRecordCache,
				() -> accountFCMap,
				nodeAccount,
				precheckVerifier,
				TestFeesFactory.FEES_FACTORY.get(),
				() -> new StateView(() -> topicFCMap, () -> accountFCMap, propertySource, null),
				new BasicPrecheck(TestContextValidator.TEST_VALIDATOR, new MockGlobalDynamicProps()),
				new QueryFeeCheck(() -> accountFCMap),
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies),
				platformStatus);

		TxnValidityAndFeeReq result =
				localTransactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.DUPLICATE_TRANSACTION);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testInvalidTransactionStartOutOfRange() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		TransactionID trId = trBody.getTransactionID();
		Timestamp trValidStart = trId.getTransactionValidStart();
		// put validstart into future time
		trValidStart = trValidStart.toBuilder().setSeconds(Instant.MAX.getEpochSecond() - 1).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		trBody = trBody.toBuilder().setTransactionID(trId).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		Assertions.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_START, result.getValidity());
		assert (result.getRequiredFee() == 0L);

		trValidStart = trValidStart.toBuilder().setSeconds(Instant.MAX.getEpochSecond() + 1).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		trBody = trBody.toBuilder().setTransactionID(trId).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_TRANSACTION_START);
		assert (result.getRequiredFee() == 0L);
	}

	@Test
	void testInvalidTransactionStartTime() throws Exception {
		Transaction origTransaction = createPossibleTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		TransactionID trId = trBody.getTransactionID();
		Timestamp trValidStart = trId.getTransactionValidStart();
		// put validstart into future time
		Instant startTime = Instant.now(Clock.systemUTC()).plusSeconds(60);
		trValidStart = trValidStart.toBuilder().setSeconds(startTime.getEpochSecond()).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		trBody = trBody.toBuilder().setTransactionID(trId).build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		Transaction signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		TxnValidityAndFeeReq result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == ResponseCodeEnum.INVALID_TRANSACTION_START);
		assert (result.getRequiredFee() == 0L);

		// 1. Negative : test start + duration should be less than node time
		startTime = Instant.now(Clock.systemUTC()).plusSeconds(-11);
		trValidStart = trValidStart.toBuilder().setSeconds(startTime.getEpochSecond()).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		Duration duration = RequestBuilder.getDuration(20);
		trBody = trBody.toBuilder().setTransactionID(trId).setTransactionValidDuration(duration)
				.build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		Assertions.assertEquals(ResponseCodeEnum.TRANSACTION_EXPIRED, result.getValidity());
		assert (result.getRequiredFee() == 0L);

		// 2. Positive : test start + duration should be less than node time
		startTime = Instant.now(Clock.systemUTC()).plusSeconds(-9);
		trValidStart = trValidStart.toBuilder().setSeconds(startTime.getEpochSecond()).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		duration = RequestBuilder.getDuration(20);
		trBody = trBody.toBuilder().setTransactionID(trId).setTransactionValidDuration(duration)
				.build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == OK);
		assert (result.getRequiredFee() == 0L);

		// 3. Positive : test start + duration should be less than node time
		startTime = Instant.now(Clock.systemUTC()).plusSeconds(-1);
		trValidStart = trValidStart.toBuilder().setSeconds(startTime.getEpochSecond()).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		duration = RequestBuilder.getDuration(20);
		trBody = trBody.toBuilder().setTransactionID(trId).setTransactionValidDuration(duration)
				.build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == OK);
		assert (result.getRequiredFee() == 0L);

		// 4. Positive : test start + duration should be less than node time
		startTime = Instant.now(Clock.systemUTC()).plusSeconds(-1);
		trValidStart = trValidStart.toBuilder().setSeconds(startTime.getEpochSecond()).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		duration = RequestBuilder.getDuration(120);
		trBody = trBody.toBuilder().setTransactionID(trId).setTransactionValidDuration(duration)
				.build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == OK);
		assert (result.getRequiredFee() == 0L);

		// 5. Positive : test start + duration should be less than node time
		startTime = Instant.now(Clock.systemUTC()).plusSeconds(-1);
		trValidStart = trValidStart.toBuilder().setSeconds(startTime.getEpochSecond()).build();
		trId = trId.toBuilder().setTransactionValidStart(trValidStart).build();
		duration = RequestBuilder.getDuration(15);
		trBody = trBody.toBuilder().setTransactionID(trId).setTransactionValidDuration(duration)
				.build();
		origTransaction = origTransaction.toBuilder().setBodyBytes(trBody.toByteString()).build();

		signedTransaction = TransactionSigner.signTransactionWithSignatureMap(origTransaction,
				Collections.singletonList(payerKeyGenerated.getPrivate()),
				Collections.singletonList(payerKeyGenerated.getPublic()));
		assert (signedTransaction != null);

		result =
				transactionHandler.validateTransactionPreConsensus(signedTransaction, false);
		assert (result.getValidity() == OK);
		assert (result.getRequiredFee() == 0L);
	}

	private SignatureMap fakeSigMap() {
		return SignatureMap.newBuilder()
				.addSigPair(SignaturePair.newBuilder()
						.setPubKeyPrefix(ByteString.copyFromUtf8("fake public key"))
						.setEd25519(ByteString.copyFromUtf8("fake private key")))
				.build();
	}

	private TransactionBody testBody() {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")))
				.build();
	}

	private Transaction.Builder transactionWithSignedTransactionBytes() {
		SignedTransaction signedTransaction = SignedTransaction.newBuilder()
				.setBodyBytes(testBody().toByteString())
				.setSigMap(fakeSigMap())
				.build();
		return Transaction.newBuilder().setSignedTransactionBytes(signedTransaction.toByteString());
	}

	@Test
	void failsFastOnInvalidTransactionBody() {
		TxnValidityAndFeeReq result = transactionHandler.validateTransactionPreConsensus(
				Transaction.newBuilder().setSigMap(fakeSigMap()).build(), false);
		Assert.assertEquals(INVALID_TRANSACTION_BODY, result.getValidity());
		Assert.assertEquals(0l, result.getRequiredFee());
	}

	@Test
	void failsFastOnSignedTransactionBytesCombinedWithSigMap() {
		TxnValidityAndFeeReq result = transactionHandler.validateTransactionPreConsensus(
				transactionWithSignedTransactionBytes().setSigMap(fakeSigMap()).build(), false);
		Assert.assertEquals(INVALID_TRANSACTION, result.getValidity());
		Assert.assertEquals(0l, result.getRequiredFee());
	}

	@Test
	void failsFastOnSignedTransactionBytesCombinedWithBodyBytes() {
		TxnValidityAndFeeReq result = transactionHandler.validateTransactionPreConsensus(
				transactionWithSignedTransactionBytes().setBodyBytes(testBody().toByteString()).build(), false);
		Assert.assertEquals(INVALID_TRANSACTION, result.getValidity());
		Assert.assertEquals(0l, result.getRequiredFee());
	}

	@Test
	void failsOnUnsupportedTransactionWithSignedTransactionBytes() {
		TxnValidityAndFeeReq result = transactionHandler.validateTransactionPreConsensus(
				transactionWithSignedTransactionBytes().build(), false);
		Assert.assertEquals(NOT_SUPPORTED, result.getValidity());
		Assert.assertEquals(0l, result.getRequiredFee());
	}

	@Test
	void failsOnUnsupportedTransactionWithBodyBytesAndSigMap() {
		TxnValidityAndFeeReq result = transactionHandler.validateTransactionPreConsensus(
				Transaction.newBuilder()
						.setBodyBytes(testBody().toByteString())
						.setSigMap(fakeSigMap())
						.build(), false);
		Assert.assertEquals(NOT_SUPPORTED, result.getValidity());
		Assert.assertEquals(0l, result.getRequiredFee());
	}
}
