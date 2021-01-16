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

import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.DefaultSigBytesProvider;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.CryptoCreateFactory;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.hedera.services.keys.HederaKeyActivation.otherPartySigsAreActive;
import static com.hedera.services.keys.HederaKeyActivation.payerSigIsActive;
import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.sigs.HederaToPlatformSigOps.PRE_HANDLE_SUMMARY_FACTORY;
import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.sigs.HederaToPlatformSigOps.rationalizeIn;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsPlusAccountRetriesFor;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.INVALID_PAYER_ID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.NEW_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_ADD_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.sigs.SigWrappers.asKind;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
public class SigOpsRegressionTest {
	private HederaFs hfs;
	private MiscRunningAvgs runningAvgs;
	private MiscSpeedometers speedometers;
	private List<TransactionSignature> expectedSigs;
	private SignatureStatus actualStatus;
	private SignatureStatus successStatus;
	private SignatureStatus syncSuccessStatus;
	private SignatureStatus asyncSuccessStatus;
	private SignatureStatus expectedErrorStatus;
	private SignatureStatus sigCreationFailureStatus;
	private PlatformTxnAccessor platformTxn;
	private HederaSigningOrder signingOrder;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(new MockEntityNumbers());
	private Predicate<TransactionBody> updateAccountSigns = txn ->
			mockSystemOpPolicies.check(txn, HederaFunctionality.CryptoUpdate) != AUTHORIZED;
	private BiPredicate<TransactionBody, HederaFunctionality> targetWaclSigns = (txn, function) ->
			mockSystemOpPolicies.check(txn, function) != AUTHORIZED;

	@Test
	public void setsExpectedPlatformSigsForCryptoCreate() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);

		// when:
		actualStatus = invokeExpansionScenario();

		// then:
		statusMatches(successStatus);
		assertEquals(expectedSigs, platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	public void setsExpectedErrorForBadPayer() throws Throwable {
		// given:
		setupFor(INVALID_PAYER_ID_SCENARIO);

		// when:
		actualStatus = invokeExpansionScenario();

		// then:
		statusMatches(expectedErrorStatus);
		assertEquals(expectedSigs, platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	public void setsExpectedErrorAndSigsForMissingTargetAccount() throws Throwable {
		// given:
		setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);

		// when:
		actualStatus = invokeExpansionScenario();

		// then:
		statusMatches(expectedErrorStatus);
		assertEquals(expectedSigs, platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	public void rationalizesExpectedPlatformSigsForCryptoCreate() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);
		// and:
		List<TransactionSignature> expectedSigs = expectedCryptoCreateScenarioSigs();

		// when:
		actualStatus = invokeRationalizationScenario();

		// then:
		statusMatches(syncSuccessStatus);
		assertEquals(expectedSigs, platformTxn.getPlatformTxn().getSignatures());
		// and:
		allVerificationStatusesAre(vs -> !VerificationStatus.UNKNOWN.equals(vs));
	}

	@Test
	public void rubberstampsCorrectPlatformSigsForCryptoCreate() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);
		// and:
		List<TransactionSignature> expectedSigs = expectedCryptoCreateScenarioSigs();
		platformTxn.getPlatformTxn().addAll(asValid(expectedSigs).toArray(new TransactionSignature[0]));

		// when:
		actualStatus = invokeRationalizationScenario();

		// then:
		statusMatches(asyncSuccessStatus);
		assertEquals(expectedSigs, platformTxn.getPlatformTxn().getSignatures());
		// and:
		allVerificationStatusesAre(vs -> VerificationStatus.VALID.equals(vs));
	}

	@Test
	public void validatesComplexPayerSigActivation() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO);
		// and:
		List<TransactionSignature> unknownSigs = PlatformSigOps.createEd25519PlatformSigsFrom(
				List.of(COMPLEX_KEY_ACCOUNT_KT.asJKey(), CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asJKey()),
				PubKeyToSigBytes.from(platformTxn.getBackwardCompatibleSignedTxn().getSigMap()),
				new BodySigningSigFactory(platformTxn)
		).getPlatformSigs();
		List<TransactionSignature> knownSigs = asKind(List.of(
				new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(1), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(3), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(6), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID)));

		// expect:
		assertTrue(invokePayerSigActivationScenario(knownSigs));
	}

	@Test
	public void deniesInactiveComplexPayerSig() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO);
		// and:
		List<TransactionSignature> unknownSigs = PlatformSigOps.createEd25519PlatformSigsFrom(
				List.of(COMPLEX_KEY_ACCOUNT_KT.asJKey(), CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asJKey()),
				PubKeyToSigBytes.from(platformTxn.getBackwardCompatibleSignedTxn().getSigMap()),
				new BodySigningSigFactory(platformTxn)
		).getPlatformSigs();
		List<TransactionSignature> knownSigs = asKind(List.of(
				new AbstractMap.SimpleEntry<>(unknownSigs.get(0), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(1), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID)));

		// expect:
		assertFalse(invokePayerSigActivationScenario(knownSigs));
	}

	@Test
	public void validatesComplexOtherPartySigActivation() throws Throwable {
		// given:
		setupFor(CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO);
		// and:
		List<TransactionSignature> unknownSigs = PlatformSigOps.createEd25519PlatformSigsFrom(
				List.of(DEFAULT_PAYER_KT.asJKey(), COMPLEX_KEY_ACCOUNT_KT.asJKey()),
				PubKeyToSigBytes.from(platformTxn.getBackwardCompatibleSignedTxn().getSigMap()),
				new BodySigningSigFactory(platformTxn)
		).getPlatformSigs();
		List<TransactionSignature> knownSigs = asKind(List.of(
				new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(1), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(2), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(4), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(7), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(9), VALID)));

		// expect:
		assertTrue(invokeOtherPartySigActivationScenario(knownSigs));
	}

	@Test
	public void deniesInactiveComplexOtherPartySig() throws Throwable {
		// given:
		setupFor(CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO);
		// and:
		List<TransactionSignature> unknownSigs = PlatformSigOps.createEd25519PlatformSigsFrom(
				List.of(DEFAULT_PAYER_KT.asJKey(), COMPLEX_KEY_ACCOUNT_KT.asJKey()),
				PubKeyToSigBytes.from(platformTxn.getBackwardCompatibleSignedTxn().getSigMap()),
				new BodySigningSigFactory(platformTxn)
		).getPlatformSigs();
		List<TransactionSignature> knownSigs = asKind(List.of(
				new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(1), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(9), VALID)));

		// expect:
		assertFalse(invokeOtherPartySigActivationScenario(knownSigs));
	}

	@Test
	public void deniesSecondInactiveComplexOtherPartySig() throws Throwable {
		// given:
		setupFor(CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_ADD_NEW_KEY_SCENARIO);
		// and:
		List<TransactionSignature> unknownSigs = PlatformSigOps.createEd25519PlatformSigsFrom(
				List.of(DEFAULT_PAYER_KT.asJKey(), COMPLEX_KEY_ACCOUNT_KT.asJKey(), NEW_ACCOUNT_KT.asJKey()),
				PubKeyToSigBytes.from(platformTxn.getBackwardCompatibleSignedTxn().getSigMap()),
				new BodySigningSigFactory(platformTxn)
		).getPlatformSigs();
		List<TransactionSignature> knownSigs = asKind(List.of(
				new AbstractMap.SimpleEntry<>(unknownSigs.get(0), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(1), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(2), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(3), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(4), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(5), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(6), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(7), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(8), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(9), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(10), VALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(11), INVALID),
				new AbstractMap.SimpleEntry<>(unknownSigs.get(12), INVALID)
		));

		// expect:
		assertFalse(invokeOtherPartySigActivationScenario(knownSigs));
	}

	private List<TransactionSignature> expectedCryptoCreateScenarioSigs() throws Throwable {
		return PlatformSigOps.createEd25519PlatformSigsFrom(
				List.of(
						DEFAULT_PAYER_KT.asJKey(),
						CryptoCreateFactory.DEFAULT_ACCOUNT_KT.asJKey()),
				PubKeyToSigBytes.from(platformTxn.getBackwardCompatibleSignedTxn().getSigMap()),
				new BodySigningSigFactory(platformTxn)
		).getPlatformSigs();
	}

	private boolean allVerificationStatusesAre(Predicate<VerificationStatus> statusPred) {
		return platformTxn.getPlatformTxn().getSignatures().stream()
				.map(TransactionSignature::getSignatureStatus)
				.allMatch(statusPred);
	}

	private void statusMatches(SignatureStatus expectedStatus) {
		assertEquals(expectedStatus.toLogMessage(), actualStatus.toLogMessage());
	}

	private boolean invokePayerSigActivationScenario(List<TransactionSignature> knownSigs) {
		platformTxn.getPlatformTxn().clear();
		platformTxn.getPlatformTxn().addAll(knownSigs.toArray(new TransactionSignature[0]));
		HederaSigningOrder keysOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				defaultLookupsFor(null, () -> accounts, () -> null, ref -> null, ref -> null),
				updateAccountSigns,
				targetWaclSigns);

		return payerSigIsActive(platformTxn, keysOrder, IN_HANDLE_SUMMARY_FACTORY);
	}

	private boolean invokeOtherPartySigActivationScenario(List<TransactionSignature> knownSigs) {
		platformTxn.getPlatformTxn().clear();
		platformTxn.getPlatformTxn().addAll(knownSigs.toArray(new TransactionSignature[0]));
		HederaSigningOrder keysOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				defaultLookupsFor(hfs, () -> accounts, null, ref -> null, ref -> null),
				updateAccountSigns,
				targetWaclSigns);

		return otherPartySigsAreActive(platformTxn, keysOrder, IN_HANDLE_SUMMARY_FACTORY);
	}

	private SignatureStatus invokeExpansionScenario() {
		int MAGIC_NUMBER = 10;
		SigMetadataLookup sigMetaLookups =
				defaultLookupsPlusAccountRetriesFor(
						hfs, () -> accounts, () -> null, ref -> null, ref -> null, MAGIC_NUMBER, MAGIC_NUMBER,
						runningAvgs, speedometers);
		HederaSigningOrder keyOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				sigMetaLookups,
				updateAccountSigns,
				targetWaclSigns);

		return expandIn(platformTxn, keyOrder, DefaultSigBytesProvider.DEFAULT_SIG_BYTES, BodySigningSigFactory::new);
	}

	private SignatureStatus invokeRationalizationScenario() throws Exception {
		SyncVerifier syncVerifier = new CryptoEngine()::verifySync;
		SigMetadataLookup sigMetaLookups = defaultLookupsFor(hfs, () -> accounts, () -> null, ref -> null, ref -> null);
		HederaSigningOrder keyOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				sigMetaLookups,
				updateAccountSigns,
				targetWaclSigns);

		return rationalizeIn(platformTxn, syncVerifier, keyOrder, DefaultSigBytesProvider.DEFAULT_SIG_BYTES);
	}

	private void setupFor(TxnHandlingScenario scenario) throws Throwable {
		hfs = scenario.hfs();
		runningAvgs = mock(MiscRunningAvgs.class);
		speedometers = mock(MiscSpeedometers.class);
		accounts = scenario.accounts();
		platformTxn = scenario.platformTxn();

		expectedErrorStatus = null;

		signingOrder = new HederaSigningOrder(
				new MockEntityNumbers(),
				defaultLookupsFor(hfs, () -> accounts, () -> null, ref -> null, ref -> null),
				updateAccountSigns,
				targetWaclSigns);
		SigningOrderResult<SignatureStatus> payerKeys =
				signingOrder.keysForPayer(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY);
		expectedSigs = new ArrayList<>();
		if (payerKeys.hasErrorReport()) {
			expectedErrorStatus = payerKeys.getErrorReport();
		} else {
			PlatformSigsCreationResult payerResult = PlatformSigOps.createEd25519PlatformSigsFrom(
					payerKeys.getOrderedKeys(),
					PubKeyToSigBytes.forPayer(platformTxn.getBackwardCompatibleSignedTxn()),
					new BodySigningSigFactory(platformTxn)
			);
			expectedSigs.addAll(payerResult.getPlatformSigs());
			SigningOrderResult<SignatureStatus> otherKeys =
					signingOrder.keysForOtherParties(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY);
			if (otherKeys.hasErrorReport()) {
				expectedErrorStatus = otherKeys.getErrorReport();
			} else {
				PlatformSigsCreationResult otherResult = PlatformSigOps.createEd25519PlatformSigsFrom(
						otherKeys.getOrderedKeys(),
						PubKeyToSigBytes.forOtherParties(platformTxn.getBackwardCompatibleSignedTxn()),
						new BodySigningSigFactory(platformTxn)
				);
				if (!otherResult.hasFailed()) {
					expectedSigs.addAll(otherResult.getPlatformSigs());
				}
			}
		}
		successStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS, ResponseCodeEnum.OK,
				false, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		syncSuccessStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS_VERIFY_SYNC, ResponseCodeEnum.OK,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		asyncSuccessStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS_VERIFY_ASYNC, ResponseCodeEnum.OK,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		sigCreationFailureStatus = new SignatureStatus(
				SignatureStatusCode.KEY_COUNT_MISMATCH, ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
	}
}
