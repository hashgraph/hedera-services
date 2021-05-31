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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.factories.PlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static com.hedera.services.sigs.HederaToPlatformSigOps.PRE_HANDLE_SUMMARY_FACTORY;
import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.sigs.HederaToPlatformSigOps.rationalizeIn;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.sigs.SyncVerifiers.ALWAYS_VALID;
import static com.hedera.test.factories.txns.SystemDeleteFactory.newSignedSystemDelete;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class HederaToPlatformSigOpsTest {
	static List<JKey> payerKey;
	static List<JKey> otherKeys;
	SignatureStatus successStatus;
	SignatureStatus failureStatus;
	SignatureStatus syncSuccessStatus;
	SignatureStatus asyncSuccessStatus;
	SignatureStatus sigCreationFailureStatus;
	SignatureStatus rationalizingFailureStatus;
	PubKeyToSigBytes allSigBytes;
	PlatformTxnAccessor platformTxn;
	HederaSigningOrder keyOrdering;


	@BeforeAll
	private static void setupAll() throws Throwable {
		payerKey = List.of(KeyTree.withRoot(ed25519()).asJKey());
		otherKeys = List.of(
				KeyTree.withRoot(ed25519()).asJKey(),
				KeyTree.withRoot(ed25519()).asJKey());
	}

	@BeforeEach
	private void setup() throws Throwable {
		allSigBytes = mock(PubKeyToSigBytes.class);
		keyOrdering = mock(HederaSigningOrder.class);
		platformTxn = new PlatformTxnAccessor(PlatformTxnFactory.from(newSignedSystemDelete().get()));
		successStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS, ResponseCodeEnum.OK,
				false, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		failureStatus = new SignatureStatus(
				SignatureStatusCode.INVALID_ACCOUNT_ID, ResponseCodeEnum.INVALID_ACCOUNT_ID,
				false, platformTxn.getTxn().getTransactionID(),
				SignedTxnFactory.DEFAULT_PAYER, null, null, null);
		syncSuccessStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS_VERIFY_SYNC, ResponseCodeEnum.OK,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		asyncSuccessStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS_VERIFY_ASYNC, ResponseCodeEnum.OK,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		rationalizingFailureStatus = new SignatureStatus(
				SignatureStatusCode.INVALID_ACCOUNT_ID, ResponseCodeEnum.INVALID_ACCOUNT_ID,
				true, platformTxn.getTxn().getTransactionID(),
				SignedTxnFactory.DEFAULT_PAYER, null, null, null);
		sigCreationFailureStatus = new SignatureStatus(
				SignatureStatusCode.KEY_PREFIX_MISMATCH, ResponseCodeEnum.KEY_PREFIX_MISMATCH,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
	}

	private void wellBehavedOrdersAndSigSourcesPreHandle() throws Exception {
		wellBehavedOrdersAndSigSources(PRE_HANDLE_SUMMARY_FACTORY);
	}

	private void wellBehavedOrdersAndSigSourcesInHandle() throws Exception {
		wellBehavedOrdersAndSigSources(IN_HANDLE_SUMMARY_FACTORY);
	}

	private void wellBehavedOrdersAndSigSources(SigStatusOrderResultFactory factory) throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), factory))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), factory))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(allSigBytes.sigBytesFor(any()))
				.willReturn("1".getBytes())
				.willReturn("2".getBytes())
				.willReturn("3".getBytes());
	}

	@Test
	void includesSuccessfulExpansions() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesPreHandle();

		// when:
		SignatureStatus status = expandIn(platformTxn, keyOrdering, allSigBytes);

		// then:
		assertEquals(successStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	void returnsImmediatelyOnPayerKeyOrderFailure() {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(failureStatus));

		// when:
		SignatureStatus status = expandIn(platformTxn, keyOrdering, allSigBytes);

		// then:
		assertEquals(failureStatus.toString(), status.toString());
	}

	@Test
	void doesntAddSigsIfCreationResultIsNotSuccess() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(allSigBytes.sigBytesFor(any()))
				.willReturn("1".getBytes())
				.willReturn("2".getBytes())
				.willThrow(KeyPrefixMismatchException.class);

		// when:
		SignatureStatus status = expandIn(platformTxn, keyOrdering, allSigBytes);

		// then:
		assertEquals(successStatus.toString(), status.toString());
		assertEquals(expectedSigsWithOtherPartiesCreationError(), platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	void rationalizesMissingSigs() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(syncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	void stopImmediatelyOnPayerKeyOrderFailure() {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(rationalizingFailureStatus));

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(rationalizingFailureStatus.toString(), status.toString());
	}

	@Test
	void stopImmediatelyOnOtherPartiesKeyOrderFailure() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(rationalizingFailureStatus));

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(rationalizingFailureStatus.toString(), status.toString());
	}

	@Test
	void stopImmediatelyOnOtherPartiesSigCreationFailure() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(allSigBytes.sigBytesFor(any()))
				.willReturn("1".getBytes())
				.willReturn("2".getBytes())
				.willThrow(KeyPrefixMismatchException.class);

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(sigCreationFailureStatus.toString(), status.toString());
	}

	@Test
	void rationalizesOnlyMissingSigs() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		platformTxn.getPlatformTxn().addAll(
				asValid(expectedSigsWithNoErrors().subList(0, 1)).toArray(new TransactionSignature[0]));
		// and:
		SyncVerifier syncVerifier = l -> {
			if (l.equals(expectedSigsWithNoErrors().subList(0, 1))) {
				throw new AssertionError("Payer sigs were verified async!");
			} else {
				ALWAYS_VALID.verifySync(l);
			}
		};

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				syncVerifier,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(syncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	void rationalizesSigsWithUnknownStatus() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		platformTxn.getPlatformTxn().addAll(
				expectedSigsWithNoErrors().subList(0, 1).toArray(new TransactionSignature[0]));

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(syncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	void doesNothingToTxnIfAllSigsAreRational() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		platformTxn = new PlatformTxnAccessor(PlatformTxnFactory.withClearFlag(platformTxn.getPlatformTxn()));
		platformTxn.getPlatformTxn().addAll(
				asValid(expectedSigsWithNoErrors()).toArray(new TransactionSignature[0]));
		// and:
		SyncVerifier syncVerifier = l -> {
			throw new AssertionError("All sigs were verified async!");
		};

		// when:
		SignatureStatus status = rationalizeIn(
				platformTxn,
				syncVerifier,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(asyncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
		assertFalse(((PlatformTxnFactory.TransactionWithClearFlag) platformTxn.getPlatformTxn()).hasClearBeenCalled());
	}

	private boolean allVerificationStatusesAre(Predicate<VerificationStatus> statusPred) {
		return platformTxn.getSigMeta().verifiedSigs().stream()
				.map(TransactionSignature::getSignatureStatus)
				.allMatch(statusPred);
	}

	private List<TransactionSignature> expectedSigsWithNoErrors() {
		return List.of(
				dummyFor(payerKey.get(0), "1"),
				dummyFor(otherKeys.get(0), "2"),
				dummyFor(otherKeys.get(1), "3"));
	}

	private List<TransactionSignature> expectedSigsWithOtherPartiesCreationError() {
		return expectedSigsWithNoErrors().subList(0, 1);
	}

	private TransactionSignature dummyFor(JKey key, String sig) {
		return PlatformSigFactory.createEd25519(
				key.getEd25519(),
				sig.getBytes(),
				platformTxn.getTxnBytes());
	}
}
