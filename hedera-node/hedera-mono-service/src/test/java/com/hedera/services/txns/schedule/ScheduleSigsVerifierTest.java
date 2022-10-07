/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.hedera.services.keys.CharacteristicsFactory;
import com.hedera.services.keys.KeyActivationCharacteristics;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleSigsVerifierTest {

    @Mock private JKey key1;
    private final byte[] key1Bytes = new byte[] {1};
    @Mock private JKey key2;
    @Mock private JKey key3;
    private final byte[] key3Bytes = new byte[] {1, 1, 1};
    @Mock private JKey key4;
    private final byte[] key4Bytes = new byte[] {1, 1, 1, 1};
    @Mock private CharacteristicsFactory characteristics;
    @Mock private SigRequirements workingSigReqs;
    @Mock private ScheduleVirtualValue schedule;
    @Mock private SigningOrderResult keysForOtherParties;
    @Mock private KeyActivationCharacteristics inferredCharacteristics;
    @Mock private TransactionBody txnBody;

    private ScheduleSigsVerifier subject;

    @BeforeEach
    void setUp() {
        subject = new ScheduleSigsVerifier(workingSigReqs, characteristics);
    }

    @Test
    void happyPathWorks() {
        subject = spy(subject);
        doReturn(txnBody).when(subject).getTransactionBody(schedule);
        given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY))
                .willReturn(keysForOtherParties);
        given(keysForOtherParties.hasErrorReport()).willReturn(false);
        given(keysForOtherParties.getOrderedKeys())
                .willReturn(ImmutableList.of(key1, key2, key3, key4));
        given(characteristics.inferredFor(txnBody)).willReturn(inferredCharacteristics);
        given(key1.isForScheduledTxn()).willReturn(true);
        given(key2.isForScheduledTxn()).willReturn(false);
        given(key3.isForScheduledTxn()).willReturn(true);
        given(key4.isForScheduledTxn()).willReturn(true);
        given(key1.primitiveKeyIfPresent()).willReturn(key1Bytes);
        given(key3.primitiveKeyIfPresent()).willReturn(key3Bytes);
        given(key4.primitiveKeyIfPresent()).willReturn(key4Bytes);
        given(schedule.hasValidSignatureFor(key1Bytes)).willReturn(true);
        given(schedule.hasValidSignatureFor(key3Bytes)).willReturn(true);
        given(schedule.hasValidSignatureFor(key4Bytes)).willReturn(true);

        subject.activation =
                (key, sigsFn, tests, characteristics) -> {
                    assertEquals(INVALID_MISSING_SIG, sigsFn.apply(null));
                    assertEquals(characteristics, inferredCharacteristics);
                    assertTrue(key == key1 || key == key2 || key == key3 || key == key4);
                    return tests.test(key, null);
                };

        assertTrue(subject.areAllKeysActive(schedule));

        verify(keysForOtherParties).getOrderedKeys();
        verify(schedule).hasValidSignatureFor(key1Bytes);
        verify(schedule).hasValidSignatureFor(key3Bytes);
        verify(schedule).hasValidSignatureFor(key4Bytes);

        verify(key1).primitiveKeyIfPresent();
        verify(key2, never()).primitiveKeyIfPresent();
        verify(key3).primitiveKeyIfPresent();
        verify(key4).primitiveKeyIfPresent();
    }

    @Test
    void rejectsOnOneMissingKey() {
        subject = spy(subject);
        doReturn(txnBody).when(subject).getTransactionBody(schedule);
        given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY))
                .willReturn(keysForOtherParties);
        given(keysForOtherParties.hasErrorReport()).willReturn(false);
        given(keysForOtherParties.getOrderedKeys())
                .willReturn(ImmutableList.of(key1, key2, key3, key4));
        given(characteristics.inferredFor(txnBody)).willReturn(inferredCharacteristics);
        given(key1.isForScheduledTxn()).willReturn(true);
        given(key2.isForScheduledTxn()).willReturn(false);
        given(key3.isForScheduledTxn()).willReturn(true);
        given(key1.primitiveKeyIfPresent()).willReturn(key1Bytes);
        given(key3.primitiveKeyIfPresent()).willReturn(key3Bytes);
        given(schedule.hasValidSignatureFor(key1Bytes)).willReturn(true);
        given(schedule.hasValidSignatureFor(key3Bytes)).willReturn(true);

        subject.activation =
                (key, sigsFn, tests, characteristics) -> {
                    assertEquals(INVALID_MISSING_SIG, sigsFn.apply(null));
                    assertEquals(characteristics, inferredCharacteristics);
                    assertTrue(key == key1 || key == key2 || key == key3);
                    return tests.test(key, null);
                };

        given(schedule.hasValidSignatureFor(key3Bytes)).willReturn(false);

        assertFalse(subject.areAllKeysActive(schedule));

        verify(keysForOtherParties).getOrderedKeys();
        verify(schedule).hasValidSignatureFor(key1Bytes);
        verify(schedule).hasValidSignatureFor(key3Bytes);
        verify(schedule, never()).hasValidSignatureFor(key4Bytes);

        verify(key1).primitiveKeyIfPresent();
        verify(key2, never()).primitiveKeyIfPresent();
        verify(key3).primitiveKeyIfPresent();
        verify(key4, never()).primitiveKeyIfPresent();
    }

    @Test
    void rejectsOnHasErrorReport() {
        subject = spy(subject);
        doReturn(txnBody).when(subject).getTransactionBody(schedule);
        given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY))
                .willReturn(keysForOtherParties);
        given(keysForOtherParties.hasErrorReport()).willReturn(true);

        assertFalse(subject.areAllKeysActive(schedule));

        verify(keysForOtherParties, never()).getOrderedKeys();
        verify(schedule, never()).hasValidSignatureFor(any());
    }

    @Test
    void passesOnNoRequiredKeys() {
        subject = spy(subject);
        doReturn(txnBody).when(subject).getTransactionBody(schedule);
        given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY))
                .willReturn(keysForOtherParties);
        given(keysForOtherParties.hasErrorReport()).willReturn(false);
        given(keysForOtherParties.getOrderedKeys()).willReturn(ImmutableList.of());
        given(characteristics.inferredFor(txnBody)).willReturn(inferredCharacteristics);

        subject.activation =
                (key, sigsFn, tests, characteristics) -> {
                    throw new IllegalStateException();
                };

        assertTrue(subject.areAllKeysActive(schedule));

        verify(keysForOtherParties).getOrderedKeys();
        verify(schedule, never()).hasValidSignatureFor(any());
    }

    @Test
    void rejectsOnGetTransactionBodyFailure() {
        subject = spy(subject);
        doReturn(null).when(subject).getTransactionBody(schedule);

        assertFalse(subject.areAllKeysActive(schedule));

        verify(workingSigReqs, never()).keysForOtherParties(any(), any());
        verify(keysForOtherParties, never()).getOrderedKeys();
        verify(schedule, never()).hasValidSignatureFor(any());
    }

    @Test
    void getTransactionBodyWorksAsExpected() {
        given(schedule.bodyBytes()).willReturn(TransactionBody.newBuilder().build().toByteArray());

        assertEquals(subject.getTransactionBody(schedule), TransactionBody.newBuilder().build());
    }

    @Test
    void getTransactionBodyHandlesInvalidBody() {
        given(schedule.bodyBytes()).willReturn(new byte[] {0});

        assertNull(subject.getTransactionBody(schedule));
    }

    @Test
    void getTransactionBodyHandlesNull() {
        assertNull(subject.getTransactionBody(null));

        given(schedule.bodyBytes()).willReturn(null);

        assertNull(subject.getTransactionBody(schedule));
    }
}
