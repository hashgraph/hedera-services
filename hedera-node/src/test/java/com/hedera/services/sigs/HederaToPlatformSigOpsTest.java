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

import com.hedera.services.sigs.factories.PlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytesProvider;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.VerificationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.sigs.HederaToPlatformSigOps.*;
import static com.hedera.test.factories.txns.SystemDeleteFactory.*;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;

import java.util.List;
import java.util.function.Predicate;

import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.sigs.SyncVerifiers.ALWAYS_VALID;

@RunWith(JUnitPlatform.class)
public class HederaToPlatformSigOpsTest {
	static List<JKey> payerKey;
	static List<JKey> otherKeys;
	SignatureStatus successStatus;
	SignatureStatus failureStatus;
	SignatureStatus syncSuccessStatus;
	SignatureStatus asyncSuccessStatus;
	SignatureStatus sigCreationFailureStatus;
	SignatureStatus rationalizingFailureStatus;
	PubKeyToSigBytes payerSigBytes;
	PubKeyToSigBytes othersSigBytes;
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
		payerSigBytes = mock(PubKeyToSigBytes.class);
		othersSigBytes = mock(PubKeyToSigBytes.class);
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
		sigCreationFailureStatus = new SignatureStatus(
				SignatureStatusCode.KEY_COUNT_MISMATCH, ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);
		rationalizingFailureStatus = new SignatureStatus(
				SignatureStatusCode.INVALID_ACCOUNT_ID, ResponseCodeEnum.INVALID_ACCOUNT_ID,
				true, platformTxn.getTxn().getTransactionID(),
				SignedTxnFactory.DEFAULT_PAYER, null, null, null);
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
		given(payerSigBytes.sigBytesFor(any())).willReturn("1".getBytes());
		given(othersSigBytes.sigBytesFor(any())).willReturn("2".getBytes()).willReturn("3".getBytes());
	}

	@Test
	public void includesSuccessfulExpansions() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesPreHandle();

		// when:
		SignatureStatus status = expandIn(platformTxn, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(successStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	public void returnsImmediatelyOnPayerKeyOrderFailure() {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(failureStatus));

		// when:
		SignatureStatus status = expandIn(platformTxn, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(failureStatus.toString(), status.toString());
	}

	@Test
	public void doesntAddSigsIfCreationResultIsNotSuccess() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(payerSigBytes.sigBytesFor(any())).willReturn("1".getBytes());
		given(othersSigBytes.sigBytesFor(any()))
				.willReturn("2".getBytes())
				.willThrow(KeySignatureCountMismatchException.class);

		// when:
		SignatureStatus status = expandIn(platformTxn, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(successStatus.toString(), status.toString());
		assertEquals(expectedSigsWithOtherPartiesCreationError(), platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	public void rationalizesMissingSigs() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, ALWAYS_VALID, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(syncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	public void stopImmediatelyOnPayerKeyOrderFailure() {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(rationalizingFailureStatus));

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, ALWAYS_VALID, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(rationalizingFailureStatus.toString(), status.toString());
	}

	@Test
	public void stopImmediatelyOnOtherPartiesKeyOrderFailure() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(rationalizingFailureStatus));

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, ALWAYS_VALID, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(rationalizingFailureStatus.toString(), status.toString());
	}

	@Test
	public void stopImmediatelyOnOtherPartiesSigCreationFailure() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), IN_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(payerSigBytes.sigBytesFor(any())).willReturn("1".getBytes());
		given(othersSigBytes.sigBytesFor(any()))
				.willReturn("2".getBytes())
				.willThrow(KeySignatureCountMismatchException.class);

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, ALWAYS_VALID, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(sigCreationFailureStatus.toString(), status.toString());
	}

	@Test
	public void rationalizesOnlyMissingSigs() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		platformTxn.getPlatformTxn().addAll(
				asValid(expectedSigsWithNoErrors().subList(0, 1)).toArray(new Signature[0]));
		// and:
		SyncVerifier syncVerifier = l -> {
			if (l.equals(expectedSigsWithNoErrors().subList(0, 1))) {
				throw new AssertionError("Payer sigs were verified async!");
			} else {
				ALWAYS_VALID.verifySync(l);
			}
		};

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, syncVerifier, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(syncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	public void rationalizesSigsWithUnknownStatus() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		platformTxn.getPlatformTxn().addAll(expectedSigsWithNoErrors().subList(0, 1).toArray(new Signature[0]));

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, ALWAYS_VALID, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(syncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	public void doesNothingToTxnIfAllSigsAreRational() throws Exception {
		// given:
		wellBehavedOrdersAndSigSourcesInHandle();
		platformTxn = new PlatformTxnAccessor(PlatformTxnFactory.withClearFlag(platformTxn.getPlatformTxn()));
		platformTxn.getPlatformTxn().addAll(
				asValid(expectedSigsWithNoErrors()).toArray(new Signature[0]));
		// and:
		SyncVerifier syncVerifier = l -> {
			throw new AssertionError("All sigs were verified async!");
		};

		// when:
		SignatureStatus status = rationalizeIn(platformTxn, syncVerifier, keyOrdering, sigBytesProvider);

		// then:
		assertEquals(asyncSuccessStatus.toString(), status.toString());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
		assertFalse(((PlatformTxnFactory.TransactionWithClearFlag) platformTxn.getPlatformTxn()).hasClearBeenCalled());
	}

	private boolean allVerificationStatusesAre(Predicate<VerificationStatus> statusPred) {
		return platformTxn.getPlatformTxn().getSignatures().stream()
				.map(Signature::getSignatureStatus)
				.allMatch(statusPred);
	}

	private List<Signature> expectedSigsWithNoErrors() {
		return List.of(
				dummyFor(payerKey.get(0), "1"),
				dummyFor(otherKeys.get(0), "2"),
				dummyFor(otherKeys.get(1), "3"));
	}

	private List<Signature> expectedSigsWithOtherPartiesCreationError() {
		return expectedSigsWithNoErrors().subList(0, 1);
	}

	private Signature dummyFor(JKey key, String sig) {
		return PlatformSigFactory.createEd25519(
				key.getEd25519(),
				sig.getBytes(),
				platformTxn.getTxnBytes());
	}

	PubKeyToSigBytesProvider sigBytesProvider = new PubKeyToSigBytesProvider() {
		@Override
		public PubKeyToSigBytes payerSigBytesFor(Transaction signedTxn) {
			return payerSigBytes;
		}

		@Override
		public PubKeyToSigBytes otherPartiesSigBytesFor(Transaction signedTxn) {
			return othersSigBytes;
		}

		@Override
		public PubKeyToSigBytes allPartiesSigBytesFor(Transaction signedTxn) {
			throw new AssertionError("Irrelevant to this operation!");
		}
	};
}
