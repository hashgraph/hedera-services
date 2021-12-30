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

import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.KeyType;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.SigObserver;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.services.sigs.order.SigningOrderResult.noKnownKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpansionTest {
	@Mock
	private PubKeyToSigBytes pkToSigFn;
	@Mock
	private SigRequirements sigReqs;
	@Mock
	private PlatformTxnAccessor txnAccessor;
	@Mock
	private TxnScopedPlatformSigFactory sigFactory;
	@Mock
	private SwirldTransaction swirldTransaction;
	@Mock
	private TransactionSignature ed25519Sig;
	@Mock
	private TransactionSignature secp256k1Sig;

	private Expansion subject;

	@BeforeEach
	void setUp() {
		subject = new Expansion(txnAccessor, sigReqs, pkToSigFn, sigFactory);
	}

	@Test
	void tracksLinkedRefs() {
		final var mockTxn = TransactionBody.getDefaultInstance();
		given(sigReqs.keysForPayer(eq(mockTxn), eq(CODE_ORDER_RESULT_FACTORY), any()))
				.willAnswer(invocationOnMock -> {
					final var linkedRefs = (LinkedRefs) invocationOnMock.getArgument(2);
					linkedRefs.link(1L);
					return SigningOrderResult.noKnownKeys();
				});
		given(sigReqs.keysForOtherParties(eq(mockTxn), eq(CODE_ORDER_RESULT_FACTORY), any()))
				.willAnswer(
						invocationOnMock -> {
							final var linkedRefs = (LinkedRefs) invocationOnMock.getArgument(2);
							linkedRefs.link(2L);
							return SigningOrderResult.noKnownKeys();
						});
		given(txnAccessor.getTxn()).willReturn(mockTxn);
		given(txnAccessor.getPlatformTxn()).willReturn(swirldTransaction);

		final var result = subject.execute();

		assertEquals(OK, result);
		final ArgumentCaptor<LinkedRefs> captor = ArgumentCaptor.forClass(LinkedRefs.class);
		verify(txnAccessor).setLinkedRefs(captor.capture());
		final var linkedRefs = captor.getValue();
		assertArrayEquals(new long[] { 1L, 2L }, linkedRefs.linkedNumbers());
	}

	@Test
	void skipsUnusedFullKeySigsIfNotPresent() {
		setupDegenerateMocks();

		final var result = subject.execute();

		assertEquals(OK, result);
		verify(pkToSigFn, never()).forEachUnusedSigWithFullPrefix(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void appendsUnusedFullKeySignaturesToList() {
		final var pretendEd25519FullKey = "COMPLETE".getBytes(StandardCharsets.UTF_8);
		final var pretendEd25519FullSig = "NONSENSE".getBytes(StandardCharsets.UTF_8);
		final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
		final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);

		given(sigFactory.signAppropriately(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig))
				.willReturn(ed25519Sig);
		given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
				.willReturn(secp256k1Sig);
		setupDegenerateMocks();
		given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
		willAnswer(inv -> {
			final var obs = (SigObserver) inv.getArgument(0);
			obs.accept(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig);
			obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
			return null;
		}).given(pkToSigFn).forEachUnusedSigWithFullPrefix(any());

		final var result = subject.execute();

		assertEquals(OK, result);
		verify(swirldTransaction).add(ed25519Sig);
		verify(swirldTransaction).add(secp256k1Sig);
	}

	private void setupDegenerateMocks() {
		final var degenTxnBody = TransactionBody.getDefaultInstance();
		given(txnAccessor.getTxn()).willReturn(degenTxnBody);
		given(sigReqs.keysForPayer(degenTxnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(noKnownKeys());
		given(sigReqs.keysForOtherParties(degenTxnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(noKnownKeys());
		given(txnAccessor.getPlatformTxn()).willReturn(swirldTransaction);
	}
}
