package com.hedera.services.store.contracts.precompile.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.pricing.AssetsLoader;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Provider;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static com.hedera.services.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PrecompilePricingUtilsTest {

	private static final long COST = 36;
	private static final int CENTS_RATE = 12;
	private static final int HBAR_RATE = 1;
	@Mock
	private AssetsLoader assetLoader;
	@Mock
	private HbarCentExchange exchange;
	@Mock
	private ExchangeRate exchangeRate;
	@Mock
	private Provider<FeeCalculator> feeCalculator;
	@Mock
	private UsagePricesProvider resourceCosts;
	@Mock
	private StateView stateView;


	@Test
	void failsToLoadCanonicalPrices() throws IOException {
		given(assetLoader.loadCanonicalPrices()).willThrow(IOException.class);
		assertThrows(PrecompilePricingUtils.CanonicalOperationsUnloadableException.class,
				() -> new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, stateView));
	}

	@Test
	void calculatesMinimumPrice() throws IOException {
		Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
		given(exchange.rate(timestamp)).willReturn(exchangeRate);
		given(assetLoader.loadCanonicalPrices()).willReturn(Map.of(HederaFunctionality.TokenAssociateToAccount, Map.of(
				SubType.DEFAULT, BigDecimal.valueOf(COST))));
		given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
		given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);

		PrecompilePricingUtils subject = new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, stateView);

		long price = subject.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.ASSOCIATE,
				timestamp);

		assertEquals(USD_TO_TINYCENTS.multiply(BigDecimal.valueOf(COST * HBAR_RATE / CENTS_RATE)).longValue(), price);
	}
}
