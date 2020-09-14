package com.hedera.services.fees.charging;

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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.EnumMap;
import java.util.EnumSet;

import static org.mockito.BDDMockito.*;
import com.hedera.services.fees.TxnFeeType;
import static com.hedera.services.fees.TxnFeeType.*;

@RunWith(JUnitPlatform.class)
class FieldSourcedFeeScreeningTest {
	long willingness = 1_000L;
	long network = 500L, service = 200L, node = 100L, stateRecord = 150L, cacheRecord = 50L;
	AccountID payer = IdUtils.asAccount("0.0.1001");
	AccountID master = IdUtils.asAccount("0.0.50");
	AccountID participant = IdUtils.asAccount("0.0.2002");

	BalanceCheck check;
	FeeExemptions exemptions;
	TransactionBody txn;
	TransactionBody systemFileUpdateTxn;
	SignedTxnAccessor accessor;

	FieldSourcedFeeScreening subject;

	@BeforeEach
	private void setup() {
		txn = mock(TransactionBody.class);
		check = mock(BalanceCheck.class);
		accessor = mock(SignedTxnAccessor.class);
		exemptions = mock(FeeExemptions.class);
		systemFileUpdateTxn = mock(TransactionBody.class);

		given(txn.getTransactionFee()).willReturn(willingness);
		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getPayer()).willReturn(payer);

		given(exemptions.isExemptFromRecordFees(master)).willReturn(true);

		subject = new FieldSourcedFeeScreening(exemptions);
		subject.setBalanceCheck(check);
		subject.resetFor(accessor);
	}

	@Test
	public void exemptsMasterFromRecordFees() {
		// setup:
		EnumSet<TxnFeeType> thresholdRecordFee = EnumSet.of(THRESHOLD_RECORD);

		givenKnownFeeAmounts();
		given(check.canAfford(master, 0)).willReturn(true);
		given(check.canAfford(argThat(master::equals), longThat(l -> l > 0))).willReturn(false);

		// when:
		boolean viability = subject.canParticipantAfford(master, thresholdRecordFee);

		// then:
		assertTrue(viability);
	}

	@Test
	public void exemptsPayerWhenExpected() {
		// setup:
		EnumSet<TxnFeeType> allPossibleFees = EnumSet.of(NETWORK, NODE, SERVICE, CACHE_RECORD, THRESHOLD_RECORD);

		givenKnownFeeAmounts();
		given(exemptions.hasExemptPayer(accessor)).willReturn(true);
		given(accessor.getTxn()).willReturn(systemFileUpdateTxn);
		given(systemFileUpdateTxn.getTransactionFee()).willReturn(Long.MAX_VALUE);
		given(check.canAfford(argThat(payer::equals), anyLong())).willReturn(false);
		// and:
		subject.resetFor(accessor);

		// when:
		boolean viability = subject.isPayerWillingnessCredible() &&
				subject.isPayerWillingToCover(allPossibleFees) &&
				subject.canPayerAfford(allPossibleFees);

		// then:
		assertTrue(viability);
	}

	@Test
	public void detectsPayerWillingness() {
		givenKnownFeeAmounts();
		given(txn.getTransactionFee()).willReturn(network + service);

		// when:
		boolean isWillingForEverything = subject.isPayerWillingToCover(EnumSet.of(NETWORK, NODE, SERVICE));
		boolean isWillingForSomething = subject.isPayerWillingToCover(EnumSet.of(NETWORK, SERVICE));

		// then:
		assertFalse(isWillingForEverything);
		assertTrue(isWillingForSomething);
	}

	@Test
	public void detectsParticipantSolvency() {
		givenKnownFeeAmounts();
		given(check.canAfford(participant, network + service + node)).willReturn(false);
		given(check.canAfford(participant, network + service)).willReturn(true);

		// when:
		boolean canAffordEverything = subject.canParticipantAfford(participant, EnumSet.of(NETWORK, NODE, SERVICE));
		boolean canAffordSomething = subject.canParticipantAfford(participant, EnumSet.of(NETWORK, SERVICE));

		// then:
		assertFalse(canAffordEverything);
		assertTrue(canAffordSomething);
	}

	@Test
	public void detectsPayerSolvency() {
		givenKnownFeeAmounts();
		given(check.canAfford(payer, network + service + node)).willReturn(false);
		given(check.canAfford(payer, network + service)).willReturn(true);

		// when:
		boolean canAffordEverything = subject.canPayerAfford(EnumSet.of(NETWORK, NODE, SERVICE));
		boolean canAffordSomething = subject.canPayerAfford(EnumSet.of(NETWORK, SERVICE));

		// then:
		assertFalse(canAffordEverything);
		assertTrue(canAffordSomething);
	}

	@Test
	public void incorporatesFeeAmounts() {
		// setup:
		EnumMap<TxnFeeType, Long> amounts = mock(EnumMap.class);

		// given:
		subject.feeAmounts = amounts;

		// when:
		subject.setFor(THRESHOLD_RECORD, stateRecord);

		// then:
		verify(amounts).put(THRESHOLD_RECORD, stateRecord);
	}

	@Test
	public void approvesCredibleWillingness() {
		given(check.canAfford(payer, willingness)).willReturn(true);

		// when:
		boolean isCredible = subject.isPayerWillingnessCredible();

		// then:
		assertTrue(isCredible);
		verify(check).canAfford(payer, willingness);
	}

	@Test
	public void feeTest() {
		// setup:
		EnumSet<TxnFeeType> thresholdRecordFee = EnumSet.of(THRESHOLD_RECORD);
		subject.setFor(NETWORK, network);
		subject.setFor(SERVICE, service);
		subject.setFor(NODE, node);
		subject.setFor(CACHE_RECORD, cacheRecord);
		// when:
		boolean viability = subject.canParticipantAfford(master, thresholdRecordFee);
		// then:
		assertFalse(viability);
	}

	private void givenKnownFeeAmounts() {
		subject.setFor(NETWORK, network);
		subject.setFor(SERVICE, service);
		subject.setFor(NODE, node);
		subject.setFor(CACHE_RECORD, cacheRecord);
		subject.setFor(THRESHOLD_RECORD, stateRecord);
	}
}
