package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;

import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.TxnUsage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.usage.token.TokenTxnUsage.tokenEntitySizes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class TokenAssociateUsageTest {
	long now = 1_234_567L, expiry = now + 1_000_000L;
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	TokenID firstId = IdUtils.asToken("0.0.75231");
	TokenID secondId = IdUtils.asToken("0.0.75232");
	AccountID id = IdUtils.asAccount("1.2.3");

	TokenAssociateTransactionBody op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	TokenAssociateUsage subject;

	@BeforeEach
	public void setup() {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TxnUsage.estimatorFactory = factory;
	}

	@Test
	public void assessesEverything() {
		givenOpWithTwoAssociations();
		// and:
		subject = TokenAssociateUsage.newEstimate(txn, sigUsage);

		// when:
		var usage = subject.givenCurrentExpiry(expiry).get();

		// then:
		assertEquals(A_USAGES_MATRIX, usage);
		// and:
		verify(base, times(3)).addBpt(FeeBuilder.BASIC_ENTITY_ID_SIZE);
		// and:
		verify(base).addRbs(2 * tokenEntitySizes.bytesUsedPerAccountRelationship() * (expiry - now));
	}

	private void givenOpWithTwoAssociations() {
		op = TokenAssociateTransactionBody.newBuilder()
				.setAccount(id)
				.addTokens(firstId)
				.addTokens(secondId)
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenAssociate(op)
				.build();
	}
}
