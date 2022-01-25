package com.hedera.services.store.contracts.precompile;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.pricing.AssetsLoader;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static com.hedera.services.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class PrecompilePricingUtilsTest {

	private static final long COST = 36;
	private static final int CENTS_RATE = 12;
	private static final int HBAR_RATE = 1;
	@Mock
	private AssetsLoader assetLoader;
	@Mock
	private HbarCentExchange exchange;
	@Mock
	private ExchangeRate exchangeRate;


	@Test
	void failsToLoadCanonicalPrices() throws IOException {
		given(assetLoader.loadCanonicalPrices()).willThrow(IOException.class);
		assertThrows(PrecompilePricingUtils.CanonicalOperationsUnloadbleException.class,
				() -> new PrecompilePricingUtils(assetLoader, exchange));
	}

	@Test
	void calculatesMinimumPrice() throws IOException {
		Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
		given(exchange.rate(timestamp)).willReturn(exchangeRate);
		given(assetLoader.loadCanonicalPrices()).willReturn(Map.of(HederaFunctionality.TokenAssociateToAccount, Map.of(
				SubType.DEFAULT, BigDecimal.valueOf(COST))));
		given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
		given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);

		PrecompilePricingUtils subject = new PrecompilePricingUtils(assetLoader, exchange);

		long price = subject.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.ASSOCIATE,
				timestamp);

		assertEquals(USD_TO_TINYCENTS.multiply(BigDecimal.valueOf(COST * HBAR_RATE / CENTS_RATE)).longValue(), price);
	}
}
