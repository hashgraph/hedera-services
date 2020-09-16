package com.hedera.services.fees.calculation.contract.txns;

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

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class ContractCallResourceUsageTest {
	private SigValueObj sigUsage;
	private SmartContractFeeBuilder usageEstimator;
	private ContractCallResourceUsage subject;

	private TransactionBody nonContractCallTxn;
	private TransactionBody contractCallTxn;

	@BeforeEach
	private void setup() throws Throwable {
		contractCallTxn = mock(TransactionBody.class);
		given(contractCallTxn.hasContractCall()).willReturn(true);

		nonContractCallTxn = mock(TransactionBody.class);
		given(nonContractCallTxn.hasContractCall()).willReturn(false);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(SmartContractFeeBuilder.class);

		subject = new ContractCallResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(contractCallTxn));
		assertFalse(subject.applicableTo(nonContractCallTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		subject.usageGiven(contractCallTxn, sigUsage, null);

		// then:
		verify(usageEstimator).getContractCallTxFeeMatrices(contractCallTxn, sigUsage);
	}
}
