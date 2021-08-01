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
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.factories.PlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.sigs.HederaToPlatformSigOps.rationalizeIn;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.sigs.SyncVerifiers.ALWAYS_VALID;
import static com.hedera.test.factories.txns.SystemDeleteFactory.newSignedSystemDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class HederaToPlatformSigOpsTest {
	static List<JKey> payerKey;
	static List<JKey> otherKeys;
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
	}

	private void wellBehavedOrdersAndSigSources() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
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
		wellBehavedOrdersAndSigSources();

		// when:
		final var status = expandIn(platformTxn, keyOrdering, allSigBytes);

		// then:
		assertEquals(OK, status);
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	void returnsImmediatelyOnPayerKeyOrderFailure() {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(INVALID_ACCOUNT_ID));

		// when:
		final var status = expandIn(platformTxn, keyOrdering, allSigBytes);

		// then:
		assertEquals(INVALID_ACCOUNT_ID, status);
	}

	@Test
	void doesntAddSigsIfCreationResultIsNotSuccess() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(allSigBytes.sigBytesFor(any()))
				.willReturn("1".getBytes())
				.willReturn("2".getBytes())
				.willThrow(KeyPrefixMismatchException.class);

		// when:
		final var status = expandIn(platformTxn, keyOrdering, allSigBytes);

		// then:
		assertEquals(OK, status);
		assertEquals(expectedSigsWithOtherPartiesCreationError(), platformTxn.getPlatformTxn().getSignatures());
	}

	@Test
	void rationalizesMissingSigs() throws Exception {
		// given:
		wellBehavedOrdersAndSigSources();

		// when:
		final var ans = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(OK, ans.finalStatus());
		assertTrue(ans.usedSyncVerification());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	void stopImmediatelyOnPayerKeyOrderFailure() {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(INVALID_ACCOUNT_ID));

		// when:
		final var ans = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(INVALID_ACCOUNT_ID, ans.finalStatus());
	}

	@Test
	void stopImmediatelyOnOtherPartiesKeyOrderFailure() throws Exception {
		// given:
		wellBehavedOrdersAndSigSources();
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(INVALID_ACCOUNT_ID));

		// when:
		final var ans = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(INVALID_ACCOUNT_ID, ans.finalStatus());
	}

	@Test
	void stopImmediatelyOnOtherPartiesSigCreationFailure() throws Exception {
		given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(payerKey));
		given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(otherKeys));
		// and:
		given(allSigBytes.sigBytesFor(any()))
				.willReturn("1".getBytes())
				.willReturn("2".getBytes())
				.willThrow(KeyPrefixMismatchException.class);

		// when:
		final var ans = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertEquals(KEY_PREFIX_MISMATCH, ans.finalStatus());
	}

	@Test
	void rationalizesOnlyMissingSigs() throws Exception {
		// given:
		wellBehavedOrdersAndSigSources();
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
		final var ans = rationalizeIn(
				platformTxn,
				syncVerifier,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertTrue(ans.usedSyncVerification());
		assertEquals(OK, ans.finalStatus());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	void rationalizesSigsWithUnknownStatus() throws Exception {
		// given:
		wellBehavedOrdersAndSigSources();
		platformTxn.getPlatformTxn().addAll(
				expectedSigsWithNoErrors().subList(0, 1).toArray(new TransactionSignature[0]));

		// when:
		final var ans = rationalizeIn(
				platformTxn,
				ALWAYS_VALID,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertTrue(ans.usedSyncVerification());
		assertEquals(OK, ans.finalStatus());
		assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
		assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
	}

	@Test
	void doesNothingToTxnIfAllSigsAreRational() throws Exception {
		// given:
		wellBehavedOrdersAndSigSources();
		platformTxn = new PlatformTxnAccessor(PlatformTxnFactory.withClearFlag(platformTxn.getPlatformTxn()));
		platformTxn.getPlatformTxn().addAll(
				asValid(expectedSigsWithNoErrors()).toArray(new TransactionSignature[0]));
		// and:
		SyncVerifier syncVerifier = l -> {
			throw new AssertionError("All sigs were verified async!");
		};

		// when:
		final var ans = rationalizeIn(
				platformTxn,
				syncVerifier,
				keyOrdering,
				allSigBytes,
				new BodySigningSigFactory(platformTxn));

		// then:
		assertFalse(ans.usedSyncVerification());
		assertEquals(OK, ans.finalStatus());
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
