package com.hedera.services.fees.calculation.contract.txns;

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

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.*;

class ContractDeleteResourceUsageTest {
	private SigValueObj sigUsage;
	private SmartContractFeeBuilder usageEstimator;
	private ContractDeleteResourceUsage subject;

	private TransactionBody nonContractDeleteTxn;
	private TransactionBody contractDeleteTxn;

	@BeforeEach
	private void setup() throws Throwable {
		contractDeleteTxn = mock(TransactionBody.class);
		given(contractDeleteTxn.hasContractDeleteInstance()).willReturn(true);

		nonContractDeleteTxn = mock(TransactionBody.class);
		given(nonContractDeleteTxn.hasContractDeleteInstance()).willReturn(false);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(SmartContractFeeBuilder.class);

		subject = new ContractDeleteResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(contractDeleteTxn));
		assertFalse(subject.applicableTo(nonContractDeleteTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		subject.usageGiven(contractDeleteTxn, sigUsage, null);

		// then:
		verify(usageEstimator).getContractDeleteTxFeeMatrices(contractDeleteTxn, sigUsage);
	}
}
