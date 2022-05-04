package com.hedera.services.utils;

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

import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.EXPECTED_SIG;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.pk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RationalizedSigMetaTest {
	@Mock
	private EthTxSigs ethTxSigs;
	@Mock
	private TxnAccessor accessor;

	private final Map<String, Object> spanMap = new HashMap<>();

	private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
	private final List<JKey> othersKeys = List.of(TxnHandlingScenario.MISC_ADMIN_KT.asJKeyUnchecked());
	private final List<TransactionSignature> rationalizedSigs = List.of(EXPECTED_SIG);

	private RationalizedSigMeta subject;

	@Test
	void noneAvailableHasNoInfo() {
		subject = RationalizedSigMeta.noneAvailable();

		// then:
		assertFalse(subject.couldRationalizePayer());
		assertFalse(subject.couldRationalizeOthers());
		// and:
		assertThrows(IllegalStateException.class, subject::verifiedSigs);
		assertThrows(IllegalStateException.class, subject::payerKey);
		assertThrows(IllegalStateException.class, subject::othersReqSigs);
		assertThrows(IllegalStateException.class, subject::pkToVerifiedSigFn);
	}

	@Test
	void payerOnlyHasExpectedInfo() {
		givenNonEthTx();

		subject = RationalizedSigMeta.forPayerOnly(payerKey, rationalizedSigs, accessor);

		// then:
		assertTrue(subject.couldRationalizePayer());
		assertFalse(subject.couldRationalizeOthers());
		// and:
		assertSame(payerKey, subject.payerKey());
		assertSame(rationalizedSigs, subject.verifiedSigs());
		assertSame(EXPECTED_SIG, subject.pkToVerifiedSigFn().apply(pk));
	}

	@Test
	void forBothHaveExpectedInfo() {
		givenNonEthTx();

		subject = RationalizedSigMeta.forPayerAndOthers(payerKey, othersKeys, rationalizedSigs, accessor);

		assertTrue(subject.couldRationalizePayer());
		assertTrue(subject.couldRationalizeOthers());
		// and:
		assertSame(payerKey, subject.payerKey());
		assertSame(othersKeys, subject.othersReqSigs());
		assertSame(rationalizedSigs, subject.verifiedSigs());
		assertSame(EXPECTED_SIG, subject.pkToVerifiedSigFn().apply(pk));
	}

	@Test
	void ethTxCanMatchExtractedPublicKey() {
		final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
		final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
		final var compressed = q.getEncoded(true);
		givenEthTx();
		given(accessor.getSpanMap()).willReturn(spanMap);
		final var spanMapAccessor = new ExpandHandleSpanMapAccessor();
		spanMapAccessor.setEthTxSigsMeta(accessor, ethTxSigs);
		given(ethTxSigs.publicKey()).willReturn(compressed);

		subject = RationalizedSigMeta.forPayerAndOthers(payerKey, othersKeys, rationalizedSigs, accessor);

		assertTrue(subject.couldRationalizePayer());
		assertTrue(subject.couldRationalizeOthers());
		// and:
		assertSame(payerKey, subject.payerKey());
		assertSame(othersKeys, subject.othersReqSigs());
		assertSame(rationalizedSigs, subject.verifiedSigs());
		// and:
		final var verifiedSigsFn = subject.pkToVerifiedSigFn();
		assertSame(EXPECTED_SIG, verifiedSigsFn.apply(pk));
		// and:
		final var ethSigStatus = verifiedSigsFn.apply(compressed).getSignatureStatus();
		assertEquals(VerificationStatus.VALID, ethSigStatus);
	}

	@Test
	void worksAroundNullEthTxSigs() {
		givenEthTx();
		given(accessor.getSpanMap()).willReturn(spanMap);

		subject = RationalizedSigMeta.forPayerAndOthers(payerKey, othersKeys, rationalizedSigs, accessor);

		assertTrue(subject.couldRationalizePayer());
		assertTrue(subject.couldRationalizeOthers());
		// and:
		assertSame(payerKey, subject.payerKey());
		assertSame(othersKeys, subject.othersReqSigs());
		assertSame(rationalizedSigs, subject.verifiedSigs());
		// and:
		final var verifiedSigsFn = subject.pkToVerifiedSigFn();
		assertSame(EXPECTED_SIG, verifiedSigsFn.apply(pk));
	}

	private void givenNonEthTx() {
		given(accessor.getFunction()).willReturn(HederaFunctionality.ContractCall);
	}

	private void givenEthTx() {
		given(accessor.getFunction()).willReturn(HederaFunctionality.EthereumTransaction);
	}
}
