package com.hedera.services.sigs.verification;

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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.sigs.order.CodeOrderResultFactory;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoInteractions;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;

class PrecheckKeyReqsTest {
	private List<JKey> keys;
	private PrecheckKeyReqs subject;
	private SigRequirements keyOrder;
	private SigRequirements keyOrderModuloRetry;
	private final List<JKey> PAYER_KEYS = List.of(new JKeyList());
	private final List<JKey> OTHER_KEYS = List.of(new JKeyList(), new JKeyList());
	private final List<JKey> ALL_KEYS = Stream.of(PAYER_KEYS, OTHER_KEYS).flatMap(List::stream).collect(toList());
	private final AccountID invalidAccount = IdUtils.asAccount("1.2.3");
	private final TransactionID txnId = TransactionID.getDefaultInstance();
	private final TransactionBody txn = TransactionBody.getDefaultInstance();
	private final Predicate<TransactionBody> FOR_QUERY_PAYMENT = ignore -> true;
	private final Predicate<TransactionBody> FOR_NON_QUERY_PAYMENT = ignore -> false;
	private final CodeOrderResultFactory factory = CODE_ORDER_RESULT_FACTORY;

	@BeforeEach
	private void setup() {
		keyOrder = mock(SigRequirements.class);
		keyOrderModuloRetry = mock(SigRequirements.class);
	}

	@Test
	void throwsGenericExceptionAsExpected() {
		given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		given(keyOrderModuloRetry.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(factory.forGeneralError());
		givenImpliedSubject(FOR_QUERY_PAYMENT);

		// expect:
		assertThrows(Exception.class, () -> subject.getRequiredKeys(txn));
	}

	@Test
	void throwsInvalidAccountAsExpected() {
		given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		given(keyOrderModuloRetry.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(factory.forMissingAccount());
		givenImpliedSubject(FOR_QUERY_PAYMENT);

		// expect:
		assertThrows(InvalidAccountIDException.class, () -> subject.getRequiredKeys(txn));
	}

	@Test
	void throwsInvalidPayerAccountAsExpected() {
		given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY)).willReturn(factory.forInvalidAccount());
		givenImpliedSubject(FOR_NON_QUERY_PAYMENT);

		// expect:
		assertThrows(InvalidPayerAccountException.class, () -> subject.getRequiredKeys(txn));
	}

	@Test
	void usesStdKeyOrderForNonQueryPayment() throws Exception {
		given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		givenImpliedSubject(FOR_NON_QUERY_PAYMENT);

		// when:
		keys = subject.getRequiredKeys(txn);

		// then:
		verify(keyOrder).keysForPayer(txn, CODE_ORDER_RESULT_FACTORY);
		verifyNoMoreInteractions(keyOrder);
		verifyNoInteractions(keyOrderModuloRetry);
		assertEquals(keys, PAYER_KEYS);
	}

	@Test
	void usesBothOrderForQueryPayments() throws Exception {
		final var key1 = new JEd25519Key("firstKey".getBytes());
		final var key2 = new JEd25519Key("secondKey".getBytes());
		given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(List.of(key1)));
		given(keyOrderModuloRetry.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY))
				.willReturn(new SigningOrderResult<>(List.of(key1, key2, key2, key2, key1)));
		givenImpliedSubject(FOR_QUERY_PAYMENT);

		// when:
		keys = subject.getRequiredKeys(txn);

		// then:
		verify(keyOrder).keysForPayer(txn, CODE_ORDER_RESULT_FACTORY);
		verifyNoMoreInteractions(keyOrder);
		verify(keyOrderModuloRetry).keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY);
		verifyNoMoreInteractions(keyOrderModuloRetry);
		assertEquals(2, keys.size());
		assertTrue(keys.contains(key1));
		assertTrue(keys.contains(key2));
	}

	private void givenImpliedSubject(Predicate<TransactionBody> isQueryPayment) {
		subject = new PrecheckKeyReqs(keyOrder, keyOrderModuloRetry, isQueryPayment);
	}
}
