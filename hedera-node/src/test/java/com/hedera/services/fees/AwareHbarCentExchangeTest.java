package com.hedera.services.fees;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AwareHbarCentExchangeTest {
	private long crossoverTime = 1_234_567L;
	private ExchangeRateSet rates = ExchangeRateSet.newBuilder()
			.setCurrentRate(ExchangeRate.newBuilder()
					.setHbarEquiv(1).setCentEquiv(12)
					.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(crossoverTime)))
			.setNextRate(ExchangeRate.newBuilder()
					.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(crossoverTime * 2))
					.setHbarEquiv(1).setCentEquiv(24))
			.build();

	@Mock
	private TxnAccessor accessor;
	@Mock
	private TransactionContext txnCtx;

	private AwareHbarCentExchange subject;

	@BeforeEach
	void setUp() throws Exception {
		subject = new AwareHbarCentExchange();
		subject.setTxnCtx(txnCtx);
	}

	@Test
	void updatesWorkWithCurrentRate() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(beforeTxn);

		// when:
		subject.updateRates(rates);

		// expect:
		assertEquals(rates, subject.activeRates());
		assertEquals(rates.getCurrentRate(), subject.activeRate());
		assertEquals(rates.getCurrentRate(), subject.rate(beforeCrossTime));
		// and:
		assertEquals(rates, subject.fcActiveRates().toGrpc());
	}

	@Test
	void updatesWorkWithNextRate() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(afterTxn);

		// when:
		subject.updateRates(rates);

		// expect:
		assertEquals(rates.getNextRate(), subject.activeRate());
		assertEquals(rates.getNextRate(), subject.rate(afterCrossTime));
		// and:
		assertEquals(rates, subject.fcActiveRates().toGrpc());
	}

	private Timestamp beforeCrossTime = Timestamp.newBuilder()
			.setSeconds(crossoverTime - 1).build();
	private Timestamp afterCrossTime = Timestamp.newBuilder()
			.setSeconds(crossoverTime).build();
	private TransactionBody beforeTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(beforeCrossTime)
					.build())
			.build();
	private TransactionBody afterTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(afterCrossTime)
					.build())
			.build();
}
