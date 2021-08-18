package com.hedera.services.sigs;

/*-
 * ‌
 * Hedera Services Node
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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.utils.PrecheckUtils;
import com.hedera.services.sigs.verification.PrecheckKeyReqs;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.BDDMockito.anyDouble;
import static org.mockito.BDDMockito.anyInt;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

public class SigVerifierRegressionTest {
	private PrecheckKeyReqs precheckKeyReqs;
	private PrecheckVerifier precheckVerifier;
	private HederaSigningOrder keyOrder;
	private HederaSigningOrder retryingKeyOrder;
	private Predicate<TransactionBody> isQueryPayment;
	private PlatformTxnAccessor platformTxn;
	private VirtualMap<MerkleEntityId, MerkleAccount> accounts;
	private MiscRunningAvgs runningAvgs;
	private MiscSpeedometers speedometers;

	private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(new MockEntityNumbers());
	private Predicate<TransactionBody> updateAccountSigns = txn ->
			mockSystemOpPolicies.check(txn, HederaFunctionality.CryptoUpdate) != AUTHORIZED;
	private BiPredicate<TransactionBody, HederaFunctionality> targetWaclSigns = (txn, function) ->
			mockSystemOpPolicies.check(txn, function) != AUTHORIZED;

	@Test
	void rejectsInvalidTxn() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		Transaction invalidSignedTxn = Transaction.newBuilder()
				.setBodyBytes(ByteString.copyFrom("NONSENSE".getBytes()))
				.build();

		// expect:
		assertFalse(sigVerifies(invalidSignedTxn));
	}

	@Test
	void acceptsValidNonCryptoTransferPayerSig() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(FULL_PAYER_SIGS_VIA_MAP_SCENARIO);

		// expect:
		assertTrue(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void rejectsIncompleteNonCryptoTransferPayerSig() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(MISSING_PAYER_SIGS_VIA_MAP_SCENARIO);

		// expect:
		assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void rejectsInvalidNonCryptoTransferPayerSig() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(INVALID_PAYER_SIGS_VIA_MAP_SCENARIO);

		// expect:
		assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void acceptsNonQueryPaymentTransfer() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

		// expect:
		assertTrue(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void acceptsQueryPaymentTransfer() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(VALID_QUERY_PAYMENT_SCENARIO);

		// expect:
		assertTrue(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void rejectsInvalidPayerAccount() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(INVALID_PAYER_ID_SCENARIO);

		// expect:
		assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void throwsOnInvalidSenderAccount() throws Throwable {
		// given:
		setupFor(QUERY_PAYMENT_INVALID_SENDER_SCENARIO);

		// expect:
		assertThrows(InvalidAccountIDException.class,
				() -> sigVerifies(platformTxn.getSignedTxnWrapper()));
		verify(runningAvgs).recordAccountLookupRetries(anyInt());
		verify(runningAvgs).recordAccountRetryWaitMs(anyDouble());
		verify(speedometers).cycleAccountLookupRetries();
	}

	@Test
	void throwsOnInvalidSigMap() throws Throwable {
		// given:
		setupFor(AMBIGUOUS_SIG_MAP_SCENARIO);

		// expect:
		assertThrows(KeyPrefixMismatchException.class,
				() -> sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	@Test
	void rejectsQueryPaymentTransferWithMissingSigs() throws Throwable {
		assumeFalse(isInCircleCi);

		// given:
		setupFor(QUERY_PAYMENT_MISSING_SIGS_SCENARIO);

		// expect:
		assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
	}

	private boolean sigVerifies(Transaction signedTxn) throws Exception {
		try {
			SignedTxnAccessor accessor = new SignedTxnAccessor(signedTxn);
			return precheckVerifier.hasNecessarySignatures(accessor);
		} catch (InvalidProtocolBufferException ignore) {
			return false;
		}
	}

	private void setupFor(TxnHandlingScenario scenario) throws Throwable {
		final int MN = 10;
		accounts = scenario.accounts();
		platformTxn = scenario.platformTxn();
		runningAvgs = mock(MiscRunningAvgs.class);
		speedometers = mock(MiscSpeedometers.class);
		keyOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				defaultLookupsFor(null, () -> accounts, () -> null, ref -> null, ref -> null),
				updateAccountSigns,
				targetWaclSigns,
				new MockGlobalDynamicProps());
		retryingKeyOrder =
				new HederaSigningOrder(
						new MockEntityNumbers(),
						defaultLookupsPlusAccountRetriesFor(
								null, () -> accounts, () -> null, ref -> null, ref -> null,
								MN, MN, runningAvgs, speedometers),
						updateAccountSigns,
						targetWaclSigns,
						new MockGlobalDynamicProps());
		isQueryPayment = PrecheckUtils.queryPaymentTestFor(DEFAULT_NODE);
		SyncVerifier syncVerifier = new CryptoEngine()::verifySync;
		precheckKeyReqs = new PrecheckKeyReqs(keyOrder, retryingKeyOrder, isQueryPayment);
		final var pkToSigFn = platformTxn.getPkToSigsFn();
		precheckVerifier = new PrecheckVerifier(syncVerifier, precheckKeyReqs, ignore -> pkToSigFn);
	}
}

