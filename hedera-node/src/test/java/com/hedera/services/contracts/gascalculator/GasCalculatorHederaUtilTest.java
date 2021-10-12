package com.hedera.services.contracts.gascalculator;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GasCalculatorHederaUtilTest {
	@Mock
	private HbarCentExchange hbarCentExchange;
	@Mock
	private UsagePricesProvider usagePricesProvider;

	@Test
	void assertRamByteHoursTinyBarsGiven() {
		var hbarEquiv = 1000;
		var centEquiv = 100;
		var expectedRamResult = hbarEquiv / centEquiv;
		var consensusTime = Instant.now().getEpochSecond();
		final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
		var feeData = mock(FeeData.class);
		var exchangeRate = mock(ExchangeRate.class);
		given(feeData.getServicedata()).willReturn(mock(FeeComponents.class));
		given(feeData.getServicedata().getRbh()).willReturn(1000L);
		given(usagePricesProvider.defaultPricesGiven(HederaFunctionality.ContractCall, timestamp)).willReturn(feeData);
		given(hbarCentExchange.rate(timestamp)).willReturn(exchangeRate);
		given(exchangeRate.getHbarEquiv()).willReturn(hbarEquiv);
		given(exchangeRate.getCentEquiv()).willReturn(centEquiv);

		assertEquals(expectedRamResult, GasCalculatorHederaUtil.ramByteHoursTinyBarsGiven(usagePricesProvider, hbarCentExchange, consensusTime, HederaFunctionality.ContractCall));
		verify(hbarCentExchange).rate(timestamp);
		verify(usagePricesProvider).defaultPricesGiven(HederaFunctionality.ContractCall, timestamp);
	}
}
