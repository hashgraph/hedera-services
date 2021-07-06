package com.hedera.services.pricing;

import com.hedera.services.usage.state.UsageAccumulator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

class FeeSchedulesTest {
	private final FeeSchedules subject = new FeeSchedules();
	private final AssetsLoader assetsLoader = new AssetsLoader();

	@Test
	void computesExpectedPriceForSubmitMessage() throws IOException {
		// setup:
		final var canonicalPrices = assetsLoader.loadCanonicalPrices();
		final var canonicalSubmitMessagePrice = canonicalPrices.get(ConsensusSubmitMessage).get(DEFAULT);

		// given:
		Map<ResourceProvider, Map<UsableResource, Long>> computedPrices =
				subject.canonicalPricesFor(ConsensusSubmitMessage, DEFAULT);

		// when:

	}

	private long feeInUsd(Map<ResourceProvider, Map<UsableResource, Long>> prices, UsageAccumulator usage) {
		var sum = BigInteger.ZERO;
		for (var provider : ResourceProvider.class.getEnumConstants()) {
			final var providerPrices = prices.get(provider);
			for (var resource : UsableResource.class.getEnumConstants()) {
				final var biPrice = BigInteger.valueOf(providerPrices.get(resource));
				final var biUsage = BigInteger.valueOf(usage.get(provider, resource));
				sum = sum.add(biPrice.multiply(biUsage));
			}
		}
		return sum.longValueExact();
	}
}