/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.sigs.verification;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.sigs.order.CodeOrderResultFactory;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrecheckKeyReqsTest {
    private List<JKey> keys;
    private PrecheckKeyReqs subject;
    private SigRequirements keyOrder;
    private final List<JKey> PAYER_KEYS = List.of(new JKeyList());
    private final List<JKey> OTHER_KEYS = List.of(new JKeyList(), new JKeyList());
    private final TransactionBody txn = TransactionBody.getDefaultInstance();
    private final Predicate<TransactionBody> FOR_QUERY_PAYMENT = ignore -> true;
    private final Predicate<TransactionBody> FOR_NON_QUERY_PAYMENT = ignore -> false;
    private final CodeOrderResultFactory factory = CODE_ORDER_RESULT_FACTORY;

    @BeforeEach
    void setup() {
        keyOrder = mock(SigRequirements.class);
    }

    @Test
    void throwsGenericExceptionAsExpected() {
        given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(new SigningOrderResult<>(PAYER_KEYS));
        given(keyOrder.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(factory.forGeneralError());
        givenImpliedSubject(FOR_QUERY_PAYMENT);

        // expect:
        assertThrows(Exception.class, () -> subject.getRequiredKeys(txn));
    }

    @Test
    void throwsInvalidAccountAsExpected() {
        given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(new SigningOrderResult<>(PAYER_KEYS));
        given(keyOrder.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(factory.forMissingAccount());
        givenImpliedSubject(FOR_QUERY_PAYMENT);

        // expect:
        assertThrows(InvalidAccountIDException.class, () -> subject.getRequiredKeys(txn));
    }

    @Test
    void throwsInvalidPayerAccountAsExpected() {
        given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(factory.forInvalidAccount());
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
        assertEquals(keys, PAYER_KEYS);
    }

    @Test
    void usesBothOrderForQueryPayments() throws Exception {
        final JKey key1 = new JEd25519Key("firstKey".getBytes());
        final JKey key2 = new JEd25519Key("secondKey".getBytes());
        final JKey key3 = new JEd25519Key("thirdKey".getBytes());
        final JKey key4 = new JEd25519Key("firstKey".getBytes());
        given(keyOrder.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(new SigningOrderResult<>(List.of(key1)));
        given(keyOrder.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY))
                .willReturn(new SigningOrderResult<>(List.of(key2, key3, key4)));
        givenImpliedSubject(FOR_QUERY_PAYMENT);

        // when:
        keys = subject.getRequiredKeys(txn);

        // then:
        verify(keyOrder).keysForPayer(txn, CODE_ORDER_RESULT_FACTORY);
        verify(keyOrder).keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY);
        verifyNoMoreInteractions(keyOrder);
        assertEquals(3, keys.size());
        assertTrue(keys.contains(key1));
        assertTrue(keys.contains(key2));
        assertTrue(keys.contains(key3));
    }

    private void givenImpliedSubject(Predicate<TransactionBody> isQueryPayment) {
        subject = new PrecheckKeyReqs(keyOrder, isQueryPayment);
    }
}
