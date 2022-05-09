package com.hedera.services.txns.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleSigsVerifierTest {

	@Mock
	private JKey key1;
	private byte[] key1Bytes = new byte[] { 1 };
	@Mock
	private JKey key2;
	private byte[] key2Bytes = new byte[] { 1, 1 };
	@Mock
	private CharacteristicsFactory characteristics;
	@Mock
	private SigRequirements workingSigReqs;
	@Mock
	private ScheduleVirtualValue schedule;
	@Mock
	private SigningOrderResult keysForOtherParties;
	@Mock
	private KeyActivationCharacteristics inferredCharacteristics;
	@Mock
	private TransactionBody txnBody;

	private ScheduleSigsVerifier subject;

	@BeforeEach
	void setUp() {
		subject = new ScheduleSigsVerifier(workingSigReqs, characteristics);
	}

	@Test
	void happyPathWorks() {
		setupPositiveTest();

		assertTrue(subject.areAllKeysActive(schedule));

		verify(keysForOtherParties).getOrderedKeys();
		verify(schedule).hasValidSignatureFor(key1Bytes);
		verify(schedule).hasValidSignatureFor(key2Bytes);
	}

	@Test
	void rejectsOnOneMissingKey() {
		setupPositiveTest();

		given(schedule.hasValidSignatureFor(key2Bytes)).willReturn(false);

		assertFalse(subject.areAllKeysActive(schedule));

		verify(keysForOtherParties).getOrderedKeys();
		verify(schedule).hasValidSignatureFor(key1Bytes);
		verify(schedule).hasValidSignatureFor(key2Bytes);
	}

	@Test
	void rejectsOnHasErrorReport() {
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(txnBody);
		given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(keysForOtherParties);
		given(keysForOtherParties.hasErrorReport()).willReturn(true);

		assertFalse(subject.areAllKeysActive(schedule));

		verify(keysForOtherParties, never()).getOrderedKeys();
		verify(schedule, never()).hasValidSignatureFor(any());
	}

	@Test
	void passesOnNoRequiredKeys() {
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(txnBody);
		given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(keysForOtherParties);
		given(keysForOtherParties.hasErrorReport()).willReturn(false);
		given(keysForOtherParties.getOrderedKeys()).willReturn(ImmutableList.of());
		given(characteristics.inferredFor(txnBody)).willReturn(inferredCharacteristics);

		subject.activation = (key, sigsFn, tests, characteristics) -> {
			throw new IllegalStateException();
		};

		assertTrue(subject.areAllKeysActive(schedule));

		verify(keysForOtherParties).getOrderedKeys();
		verify(schedule, never()).hasValidSignatureFor(any());
	}

	private void setupPositiveTest() {
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(txnBody);
		given(workingSigReqs.keysForOtherParties(txnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(keysForOtherParties);
		given(keysForOtherParties.hasErrorReport()).willReturn(false);
		given(keysForOtherParties.getOrderedKeys()).willReturn(ImmutableList.of(key1, key2));
		given(characteristics.inferredFor(txnBody)).willReturn(inferredCharacteristics);
		given(key1.primitiveKeyIfPresent()).willReturn(key1Bytes);
		given(key2.primitiveKeyIfPresent()).willReturn(key2Bytes);
		given(schedule.hasValidSignatureFor(key1Bytes)).willReturn(true);
		given(schedule.hasValidSignatureFor(key2Bytes)).willReturn(true);

		subject.activation = (key, sigsFn, tests, characteristics) -> {
			assertEquals(INVALID_MISSING_SIG, sigsFn.apply(null));
			assertEquals(characteristics, inferredCharacteristics);
			assertTrue(key == key1 || key == key2);
			return tests.test(key, null);
		};
	}

}