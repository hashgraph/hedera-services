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
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.test.AdapterUtils.feeDataFrom;
import static com.hedera.services.test.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsTransferUsageTest {
	private CryptoOpsUsage subject = new CryptoOpsUsage();

	@Test
	void matchesWithLegacyEstimate() {
		givenOp();
		// and given legacy estimate:
		final var expected = FeeData.newBuilder()
				.setNetworkdata(FeeComponents.newBuilder()
						.setConstant(1)
						.setBpt(18047)
						.setVpt(3)
						.setRbh(1))
				.setNodedata(FeeComponents.newBuilder()
						.setConstant(1)
						.setBpt(18047)
						.setVpt(1)
						.setBpr(4))
				.setServicedata(FeeComponents.newBuilder()
						.setConstant(1)
						.setRbh(904))
				.build();

		// when:
		final var accum = new UsageAccumulator();
		subject.cryptoTransferUsage(
				sigUsage,
				new CryptoTransferMeta(tokenMultiplier, 3, 7, 0),
				new BaseTransactionMeta(memo.getBytes().length, 3),
				accum);

		// then:
		assertEquals(expected, feeDataFrom(accum));
	}

	private final int tokenMultiplier = 60;
	private final int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	private final String memo = "Yikes who knows";
	private final SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	private final long now = 1_234_567L;
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");
	private final TokenID anId = IdUtils.asToken("0.0.75231");
	private final TokenID anotherId = IdUtils.asToken("0.0.75232");
	private final TokenID yetAnotherId = IdUtils.asToken("0.0.75233");

	private TransactionBody txn;
	private CryptoTransferTransactionBody op;

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
						.setToken(yetAnotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();

		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setMemo(memo)
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
