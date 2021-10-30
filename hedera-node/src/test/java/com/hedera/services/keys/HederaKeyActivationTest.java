package com.hedera.services.keys;

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
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.sigs.SigWrappers;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.keys.HederaKeyActivation.isActive;
import static com.hedera.services.keys.HederaKeyActivation.pkToSigMapFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.createEd25519;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HederaKeyActivationTest {
	private static JKey complexKey;
	private static final byte[] pk = "PK".getBytes();
	private static final byte[] sig = "SIG".getBytes();
	private static final byte[] data = "DATA".getBytes();
	private Function<byte[], TransactionSignature> sigsFn;
	private static final TransactionSignature VALID_SIG = SigWrappers
			.asValid(List.of(createEd25519(pk, sig, data)))
			.get(0);
	private static final TransactionSignature INVALID_SIG = SigWrappers
			.asInvalid(List.of(createEd25519(pk, sig, data)))
			.get(0);

	private static final Function<Integer, TransactionSignature> mockSigFn = i -> createEd25519(
			String.format("PK%d", i).getBytes(),
			String.format("SIG%d", i).getBytes(),
			String.format("DATA%d", i).getBytes());

	@BeforeAll
	private static void setupAll() throws Throwable {
		complexKey = KeyTree.withRoot(
				list(
						ed25519(),
						threshold(1,
								list(list(ed25519(), ed25519()), ed25519()), ed25519()),
						ed25519(),
						list(
								threshold(2,
										ed25519(), ed25519(), ed25519())))).asJKey();
	}

	@BeforeEach
	void setup() {
		sigsFn = (Function<byte[], TransactionSignature>) mock(Function.class);
	}

	@Test
	void revocationServiceActivatesWithOneTopLevelSig() {
		final var characteristics = RevocationServiceCharacteristics.forTopLevelFile((JKeyList) complexKey);
		given(sigsFn.apply(any())).willReturn(
				VALID_SIG,
				INVALID_SIG, INVALID_SIG, INVALID_SIG, VALID_SIG,
				INVALID_SIG,
				INVALID_SIG, INVALID_SIG, VALID_SIG);

		assertTrue(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID, characteristics));
		verify(sigsFn, times(9)).apply(any());
	}

	@Test
	void revocationServiceRequiresOneTopLevelSig() {
		final var characteristics = RevocationServiceCharacteristics.forTopLevelFile((JKeyList) complexKey);
		given(sigsFn.apply(any())).willReturn(INVALID_SIG);

		assertFalse(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID, characteristics));
		verify(sigsFn, times(9)).apply(any());
	}

	@Test
	void mapSupplierReflectsInputList() {
		final var presentSigs = List.of(mockSigFn.apply(0), mockSigFn.apply(1));
		final var missingSig = mockSigFn.apply(2);
		final var sigsFn = pkToSigMapFrom(presentSigs);

		final var present0 = sigsFn.apply(presentSigs.get(0).getExpandedPublicKeyDirect());
		final var present1 = sigsFn.apply(presentSigs.get(1).getExpandedPublicKeyDirect());
		final var missing = sigsFn.apply(missingSig.getExpandedPublicKeyDirect());

		assertEquals(presentSigs.get(0), present0);
		assertEquals(presentSigs.get(1), present1);
		assertEquals(HederaKeyActivation.INVALID_MISSING_SIG, missing);
		assertEquals(HederaKeyActivation.INVALID_MISSING_SIG.getSignatureStatus(), VerificationStatus.INVALID);
	}

	@Test
	void topLevelListActivatesOnlyIfAllChildrenAreActive() {
		given(sigsFn.apply(any())).willReturn(INVALID_SIG, VALID_SIG);

		assertFalse(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID));
		verify(sigsFn, times(9)).apply(any());
	}

	@Test
	void topLevelActivatesIfAllChildrenAreActive() {
		given(sigsFn.apply(any())).willReturn(
				VALID_SIG,
				INVALID_SIG, INVALID_SIG, INVALID_SIG, VALID_SIG,
				VALID_SIG,
				INVALID_SIG, VALID_SIG, VALID_SIG);

		assertTrue(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID));
		verify(sigsFn, times(9)).apply(any());
	}

	@Test
	void throwsIfNoSigMetaHasBeenRationalized() {
		final var accessor = mock(TxnAccessor.class);

		assertThrows(IllegalArgumentException.class,
				() -> HederaKeyActivation.payerSigIsActive(accessor, ONLY_IF_SIG_IS_VALID));
	}

	@Test
	void immediatelyReturnsFalseForNoRationalizedPayerData() {
		final var accessor = mock(TxnAccessor.class);

		given(accessor.getSigMeta()).willReturn(RationalizedSigMeta.noneAvailable());

		assertFalse(HederaKeyActivation.payerSigIsActive(accessor, ONLY_IF_SIG_IS_VALID));
	}

	@Test
	void assertConstructorThrowsException() throws NoSuchMethodException {
		Constructor<HederaKeyActivation> constructor = HederaKeyActivation.class.getDeclaredConstructor();
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
		constructor.setAccessible(true);
		assertThrows(InvocationTargetException.class,
				() -> {
					constructor.newInstance();
				});
	}
}
