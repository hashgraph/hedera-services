package com.hedera.services.sigs.verification;

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

import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static java.util.stream.Collectors.toList;
import static org.mockito.BDDMockito.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.hedera.services.sigs.HederaToPlatformSigOps.PRE_HANDLE_SUMMARY_FACTORY;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
public class PrecheckKeyReqsTest {
	private List<JKey> keys;
	private PrecheckKeyReqs subject;
	private HederaSigningOrder keyOrder;
	private HederaSigningOrder keyOrderModuloRetry;
	private final List<JKey> PAYER_KEYS = List.of(new JKeyList());
	private final List<JKey> OTHER_KEYS = List.of(new JKeyList(), new JKeyList());
	private final List<JKey> ALL_KEYS = Stream.of(PAYER_KEYS, OTHER_KEYS).flatMap(List::stream).collect(toList());
	private final AccountID invalidAccount = IdUtils.asAccount("1.2.3");
	private final TransactionID txnId = TransactionID.getDefaultInstance();
	private final TransactionBody txn = TransactionBody.getDefaultInstance();
	private final Predicate<TransactionBody> FOR_QUERY_PAYMENT = ignore -> true;
	private final Predicate<TransactionBody> FOR_NON_QUERY_PAYMENT = ignore -> false;
	private final SigStatusOrderResultFactory factory = new SigStatusOrderResultFactory(false);

	@BeforeEach
	private void setup() {
		keyOrder = mock(HederaSigningOrder.class);
		keyOrderModuloRetry = mock(HederaSigningOrder.class);
	}

	@Test
	public void throwsGenericExceptionAsExpected() throws Exception {
		given(keyOrder.keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		given(keyOrderModuloRetry.keysForOtherParties(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(factory.forGeneralError(txnId));
		givenImpliedSubject(FOR_QUERY_PAYMENT);

		// expect:
		assertThrows(Exception.class, () -> subject.getRequiredKeys(txn));
	}

	@Test
	public void throwsInvalidAccountAsExpected() throws Exception {
		given(keyOrder.keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		given(keyOrderModuloRetry.keysForOtherParties(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(factory.forMissingAccount(invalidAccount, txnId));
		givenImpliedSubject(FOR_QUERY_PAYMENT);

		// expect:
		assertThrows(InvalidAccountIDException.class, () -> subject.getRequiredKeys(txn));
	}

	@Test
	public void throwsInvalidPayerAccountAsExpected() throws Exception {
		given(keyOrder.keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(factory.forInvalidAccount(invalidAccount, txnId));
		givenImpliedSubject(FOR_NON_QUERY_PAYMENT);

		// expect:
		assertThrows(InvalidPayerAccountException.class, () -> subject.getRequiredKeys(txn));
	}

	@Test
	public void usesStdKeyOrderForNonQueryPayment() throws Exception {
		given(keyOrder.keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		givenImpliedSubject(FOR_NON_QUERY_PAYMENT);

		// when:
		keys = subject.getRequiredKeys(txn);

		// then:
		verify(keyOrder).keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY);
		verifyNoMoreInteractions(keyOrder);
		verifyNoInteractions(keyOrderModuloRetry);
		assertEquals(keys, PAYER_KEYS);
	}

	@Test
	public void usesBothOrderForQueryPayments() throws Exception {
		given(keyOrder.keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(PAYER_KEYS));
		given(keyOrderModuloRetry.keysForOtherParties(txn, PRE_HANDLE_SUMMARY_FACTORY))
				.willReturn(new SigningOrderResult<>(OTHER_KEYS));
		givenImpliedSubject(FOR_QUERY_PAYMENT);

		// when:
		keys = subject.getRequiredKeys(txn);

		// then:
		verify(keyOrder).keysForPayer(txn, PRE_HANDLE_SUMMARY_FACTORY);
		verifyNoMoreInteractions(keyOrder);
		verify(keyOrderModuloRetry).keysForOtherParties(txn, PRE_HANDLE_SUMMARY_FACTORY);
		verifyNoMoreInteractions(keyOrderModuloRetry);
		assertEquals(keys, ALL_KEYS);
	}

	private void givenImpliedSubject(Predicate<TransactionBody> isQueryPayment) {
		subject = new PrecheckKeyReqs(keyOrder, keyOrderModuloRetry, isQueryPayment);
	}
}
