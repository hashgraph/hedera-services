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

import static com.hedera.test.utils.IdUtils.asContract;
import static org.junit.jupiter.api.Assertions.*;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.*;

class ContractUpdateResourceUsageTest {
	MerkleEntityId accountKey = new MerkleEntityId(0, 0, 1234);
	ContractID target = asContract("0.0.1234");
	Timestamp expiry = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();
	StateView view;
	MerkleAccount account;
	FCMap<MerkleEntityId, MerkleAccount> accounts;

	private SigValueObj sigUsage;
	private SmartContractFeeBuilder usageEstimator;
	private ContractUpdateResourceUsage subject;

	private TransactionBody nonContractUpdateTxn;
	private TransactionBody contractUpdateTxn;

	@BeforeEach
	private void setup() throws Throwable {
		contractUpdateTxn = mock(TransactionBody.class);
		ContractUpdateTransactionBody update = mock(ContractUpdateTransactionBody.class);
		given(update.getContractID()).willReturn(target);
		given(contractUpdateTxn.hasContractUpdateInstance()).willReturn(true);
		given(contractUpdateTxn.getContractUpdateInstance()).willReturn(update);

		nonContractUpdateTxn = mock(TransactionBody.class);
		given(nonContractUpdateTxn.hasContractUpdateInstance()).willReturn(false);

		account = mock(MerkleAccount.class);
		given(account.getExpiry()).willReturn(Long.MAX_VALUE);
		accounts = mock(FCMap.class);
		given(accounts.get(accountKey)).willReturn(account);
		view = mock(StateView.class);
		given(view.accounts()).willReturn(accounts);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(SmartContractFeeBuilder.class);

		subject = new ContractUpdateResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(contractUpdateTxn));
		assertFalse(subject.applicableTo(nonContractUpdateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		subject.usageGiven(contractUpdateTxn, sigUsage, view);

		// then:
		verify(usageEstimator).getContractUpdateTxFeeMatrices(contractUpdateTxn, expiry, sigUsage);
	}

	@Test
	public void returnsDefaultUsageOnException() throws Exception {
		// when:
		FeeData actual = subject.usageGiven(contractUpdateTxn, sigUsage, null);

		// then:
		assertEquals(FeeData.getDefaultInstance(), actual);
	}
}
