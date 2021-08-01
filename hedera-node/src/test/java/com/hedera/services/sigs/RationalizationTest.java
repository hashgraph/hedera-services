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
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RationalizationTest {
	private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
	private final TransactionBody txn = TransactionBody.getDefaultInstance();
	private final SigningOrderResult<ResponseCodeEnum> generalError =
			CODE_ORDER_RESULT_FACTORY.forGeneralError(null);
	private final SigningOrderResult<ResponseCodeEnum> othersError =
			CODE_ORDER_RESULT_FACTORY.forImmutableContract(null, null);

	@Mock
	private SwirldTransaction swirldsTxn;
	@Mock
	private TxnAccessor txnAccessor;
	@Mock
	private SyncVerifier syncVerifier;
	@Mock
	private HederaSigningOrder keyOrderer;
	@Mock
	private TxnScopedPlatformSigFactory sigFactory;
	@Mock
	private PubKeyToSigBytes pkToSigFn;
	@Mock
	private SigningOrderResult<ResponseCodeEnum> mockOrderResult;

	private Rationalization subject;

	@BeforeEach
	void setUp() {
		given(txnAccessor.getTxn()).willReturn(txn);
		given(txnAccessor.getPlatformTxn()).willReturn(swirldsTxn);

		subject = new Rationalization(txnAccessor, syncVerifier, keyOrderer, pkToSigFn, sigFactory);
	}

	@Test
	void setsUnavailableMetaIfCannotListPayerKey() {
		// setup:
		ArgumentCaptor<RationalizedSigMeta> captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);

		given(keyOrderer.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY)).willReturn(generalError);

		// when:
		subject.execute();

		// then:
		assertEquals(generalError.getErrorReport(), subject.finalStatus());
		// and:
		verify(txnAccessor).setSigMeta(captor.capture());
		assertSame(RationalizedSigMeta.noneAvailable(), captor.getValue());
	}

	@Test
	void propagatesFailureIfCouldNotExpandOthersKeys() {
		// setup:
		ArgumentCaptor<RationalizedSigMeta> captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);

		given(mockOrderResult.getPayerKey()).willReturn(payerKey);
		given(keyOrderer.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY)).willReturn(mockOrderResult);
		given(keyOrderer.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY)).willReturn(othersError);

		// when:
		subject.execute();

		// then:
		assertEquals(othersError.getErrorReport(), subject.finalStatus());
		// and:
		verify(txnAccessor).setSigMeta(captor.capture());
		final var sigMeta = captor.getValue();
		assertTrue(sigMeta.couldRationalizePayer());
		assertFalse(sigMeta.couldRationalizeOthers());
		assertSame(payerKey, sigMeta.payerKey());
	}
}
