package com.hedera.services.sigs;

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
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.ContextPlatformStatus;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.sourcing.DefaultSigBytesProvider;
import com.hedera.services.sigs.utils.PrecheckUtils;
import com.hedera.services.sigs.verification.PrecheckKeyReqs;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.mocks.TestExchangeRates;
import com.hedera.test.mocks.TestFeesFactory;
import com.hedera.test.mocks.TestProperties;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.PlatformStatus;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsPlusAccountRetriesFor;
import static com.hedera.test.CiConditions.isInCircleCi;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.INVALID_PAYER_ID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.QUERY_PAYMENT_INVALID_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.QUERY_PAYMENT_MISSING_SIGS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.VALID_QUERY_PAYMENT_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.AMBIGUOUS_SIG_MAP_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.FULL_PAYER_SIGS_VIA_MAP_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.INVALID_PAYER_SIGS_VIA_MAP_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.MISSING_PAYER_SIGS_VIA_MAP_SCENARIO;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE;
import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.BDDMockito.anyDouble;
import static org.mockito.BDDMockito.anyInt;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class TxnHandlerVerifySigRegressionTest {
	private SyncVerifier syncVerifier;
	private PrecheckKeyReqs precheckKeyReqs;
	private PrecheckVerifier precheckVerifier;
	private HederaSigningOrder keyOrder;
	private HederaSigningOrder retryingKeyOrder;
	private Predicate<TransactionBody> isQueryPayment;
	private PlatformTxnAccessor platformTxn;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private TransactionHandler subject;
	private MiscRunningAvgs runningAvgs;
	private MiscSpeedometers speedometers;

	private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(new MockEntityNumbers());
	private Predicate<TransactionBody> updateAccountSigns = txn ->
			mockSystemOpPolicies.check(txn, HederaFunctionality.CryptoUpdate) != AUTHORIZED;
	private BiPredicate<TransactionBody, HederaFunctionality> targetWaclSigns = (txn, function) ->
			mockSystemOpPolicies.check(txn, function) != AUTHORIZED;

	@Test
	public void rejectsInvalidTxn() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		Transaction invalidSignedTxn = Transaction.newBuilder()
				.setBodyBytes(ByteString.copyFrom("NONSENSE".getBytes())).build();
		var policies = new SystemOpPolicies(new MockEntityNumbers());
		var platformStatus = new ContextPlatformStatus();
		platformStatus.set(PlatformStatus.ACTIVE);
		PropertySource propertySource = mock(PropertySource.class);
		subject = new TransactionHandler(
				null,
				() -> accounts,
				DEFAULT_NODE,
				null,
				TestFeesFactory.FEES_FACTORY.get(),
				() -> new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, propertySource, null),
				new BasicPrecheck(TestContextValidator.TEST_VALIDATOR, new MockGlobalDynamicProps()),
				new QueryFeeCheck(() -> accounts),
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies),
				platformStatus);

		// expect:
		assertFalse(subject.verifySignature(invalidSignedTxn));
	}

	@Test
	public void acceptsValidNonCryptoTransferPayerSig() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(FULL_PAYER_SIGS_VIA_MAP_SCENARIO);

		// expect:
		assertTrue(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void rejectsIncompleteNonCryptoTransferPayerSig() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(MISSING_PAYER_SIGS_VIA_MAP_SCENARIO);

		// expect:
		assertFalse(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void rejectsInvalidNonCryptoTransferPayerSig() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(INVALID_PAYER_SIGS_VIA_MAP_SCENARIO);

		// expect:
		assertFalse(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void acceptsNonQueryPaymentTransfer() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

		// expect:
		assertTrue(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void acceptsQueryPaymentTransfer() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(VALID_QUERY_PAYMENT_SCENARIO);

		// expect:
		assertTrue(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void rejectsInvalidPayerAccount() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(INVALID_PAYER_ID_SCENARIO);

		// expect:
		assertFalse(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void throwsOnInvalidSenderAccount() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(QUERY_PAYMENT_INVALID_SENDER_SCENARIO);

		// expect:
		assertThrows(InvalidAccountIDException.class,
				() -> subject.verifySignature(platformTxn.getSignedTxn()));
		verify(runningAvgs).recordAccountLookupRetries(anyInt());
		verify(runningAvgs).recordAccountRetryWaitMs(anyDouble());
		verify(speedometers).cycleAccountLookupRetries();
	}

	@Test
	public void throwsOnInvalidSigMap() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(AMBIGUOUS_SIG_MAP_SCENARIO);

		// expect:
		assertThrows(KeyPrefixMismatchException.class,
				() -> subject.verifySignature(platformTxn.getSignedTxn()));
	}

	@Test
	public void rejectsQueryPaymentTransferWithMissingSigs() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(QUERY_PAYMENT_MISSING_SIGS_SCENARIO);

		// expect:
		assertFalse(subject.verifySignature(platformTxn.getSignedTxn()));
	}

	private void setupFor(TxnHandlingScenario scenario)	throws Throwable {
		final int MN = 10;
		accounts = scenario.accounts();
		platformTxn = scenario.platformTxn();
		runningAvgs = mock(MiscRunningAvgs.class);
		speedometers = mock(MiscSpeedometers.class);
		keyOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				defaultLookupsFor(null, () -> accounts, () -> null, ref -> null),
				updateAccountSigns,
				targetWaclSigns);
		retryingKeyOrder =
				new HederaSigningOrder(
						new MockEntityNumbers(),
						defaultLookupsPlusAccountRetriesFor(
								null, () -> accounts, () -> null, ref -> null,
								MN, MN, runningAvgs, speedometers),
						updateAccountSigns,
						targetWaclSigns);
		isQueryPayment = PrecheckUtils.queryPaymentTestFor(DEFAULT_NODE);
		SyncVerifier syncVerifier = new CryptoEngine()::verifySync;
		precheckKeyReqs = new PrecheckKeyReqs(keyOrder, retryingKeyOrder, isQueryPayment);
		precheckVerifier = new PrecheckVerifier(syncVerifier, precheckKeyReqs, DefaultSigBytesProvider.DEFAULT_SIG_BYTES);

		var policies = new SystemOpPolicies(new MockEntityNumbers());
		var platformStatus = new ContextPlatformStatus();
		platformStatus.set(PlatformStatus.ACTIVE);
		subject = new TransactionHandler(
				null,
				precheckVerifier,
				() -> accounts,
				DEFAULT_NODE,
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies),
				platformStatus);
	}
}

