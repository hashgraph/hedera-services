package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.TxnUsage;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

public class CryptoTransferUsageTest {
	long now = 1_234_567L;
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

	AccountID a = asAccount("1.2.3");
	AccountID b = asAccount("2.3.4");
	AccountID c = asAccount("3.4.5");
	TokenID anId = IdUtils.asToken("0.0.75231");
	TokenID anotherId = IdUtils.asToken("0.0.75232");

	CryptoTransferTransactionBody op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	CryptoTransferUsage subject;

	@BeforeEach
	public void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TxnUsage.estimatorFactory = factory;
	}

	@Test
	public void createsExpectedDeltaForTransferLists() {
		// setup:
		int M = 60;

		givenOp();
		// and:
		subject = CryptoTransferUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.givenTokenMultiplier(M).get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(M * FeeBuilder.BASIC_ENTITY_ID_SIZE
				+ 3 * M * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8)
				+ M * FeeBuilder.BASIC_ENTITY_ID_SIZE
				+ 2 * M * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8)
				+ M * FeeBuilder.BASIC_ENTITY_ID_SIZE
				+ 2 * M * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8)
				+ 3 * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8));
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(3 * M, 7 * M) *
						USAGE_PROPERTIES.legacyReceiptStorageSecs());
		verify(base).addRbs((3 * USAGE_PROPERTIES.accountAmountBytes()) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private void givenOp() {
		var hbarAdjusts = TransferList.newBuilder()
				.addAccountAmounts(adjustFrom(a, -100))
				.addAccountAmounts(adjustFrom(b, 50))
				.addAccountAmounts(adjustFrom(c, 50))
				.build();
		op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(hbarAdjusts)
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anId)
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();

		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setCryptoTransfer(op)
				.build();
	}

	private AccountAmount adjustFrom(AccountID account, long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(account)
				.build();
	}
}
