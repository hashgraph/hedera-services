package com.hedera.services.usage.token;

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
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.test.AdapterUtils.feeDataFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenOpsUsageTest {
	private final TokenOpsUsage subject = new TokenOpsUsage();

	@Test
	void knowsBytesNeededToReprCustomFeeSchedule() {
		// setup:
		final var expectedHbarFixed = FeeBuilder.LONG_SIZE + FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedHtsFixed = FeeBuilder.LONG_SIZE + 2 * FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedFractional = 4 * FeeBuilder.LONG_SIZE;
		// given:
		final var perHbarFixedFee = subject.bytesNeededToRepr(1, 0, 0);
		final var perHtsFixedFee = subject.bytesNeededToRepr(0, 1, 0);
		final var perFracFee = subject.bytesNeededToRepr(0, 0, 1);
		final var oneOfEach = subject.bytesNeededToRepr(1, 1, 1);

		// expect:
		assertEquals(expectedHbarFixed, perHbarFixedFee);
		assertEquals(expectedHtsFixed, perHtsFixedFee);
		assertEquals(expectedFractional, perFracFee);
		assertEquals(expectedHbarFixed + expectedHtsFixed + expectedFractional, oneOfEach);
	}

	@Test
	void canCountFeeTypes() {
		// setup:
		final List<CustomFee> aSchedule = new ArrayList<>();
		aSchedule.add(CustomFee.newBuilder().setFixedFee(FixedFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
				.setDenominatingTokenId(IdUtils.asToken("1.2.3")))
				.build());
		aSchedule.add(CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
				.setDenominatingTokenId(IdUtils.asToken("1.2.3")))
				.build());
		aSchedule.add(CustomFee.newBuilder().setFractionalFee(FractionalFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setFractionalFee(FractionalFee.getDefaultInstance()).build());
		aSchedule.add(CustomFee.newBuilder().setFractionalFee(FractionalFee.getDefaultInstance()).build());

		// given:
		final var expected = subject.bytesNeededToRepr(1, 2, 3);

		// when:
		final var actual = subject.bytesNeededToRepr(aSchedule);

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	void accumulatesBptAndRbhAsExpected() {
		// setup:
		final var now = 1_234_567L;
		final var lifetime = 7776000L;
		final var expiry = now + lifetime;
		final var curSize = subject.bytesNeededToRepr(1, 0 ,1);
		final var newSize = subject.bytesNeededToRepr(2, 1 ,0);
		final var serSize = newSize / 2;
		final var ctx = new ExtantFeeScheduleContext(expiry, curSize);
		final var opMeta = new FeeScheduleUpdateMeta(now, newSize, serSize);
		// and:
		final var sigUsage = new SigUsage(1, 2, 3);
		final var baseMeta = new BaseTransactionMeta(50, 0);
		// and:
		final var exp = new UsageAccumulator();
		exp.resetForTransaction(baseMeta, sigUsage);
		exp.addBpt(serSize + FeeBuilder.BASIC_ENTITY_ID_SIZE);
		exp.addRbs((newSize - curSize) * lifetime);

		// given:
		final var ans = new UsageAccumulator();

		// when:
		subject.feeScheduleUpdateUsage(sigUsage, baseMeta, opMeta, ctx, ans);

		// then:
		assertEquals(feeDataFrom(exp), feeDataFrom(ans));
	}

}
