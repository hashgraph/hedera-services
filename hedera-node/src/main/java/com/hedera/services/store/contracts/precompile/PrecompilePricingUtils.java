package com.hedera.services.store.contracts.precompile;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.pricing.AssetsLoader;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static com.hedera.services.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

public class PrecompilePricingUtils {

	/**
	 * If we lack an entry (because of a bad data load), return a value that cannot reasonably be paid.
	 * In this case $1 Million Dollars.
	 */
	static final long COST_PROHIBITIVE = 1_000_000L * 10_000_000_000L;
	private final UsagePricesProvider usagePricesProvider;
	private final HbarCentExchange exchange;
	Map<GasCostType, Long> canonicalOperationCostsInTinyCents;

	@Inject
	public PrecompilePricingUtils(
			AssetsLoader assetsLoader,
			final UsagePricesProvider usagePricesProvider,
			final HbarCentExchange exchange) {
		this.usagePricesProvider = usagePricesProvider;
		this.exchange = exchange;

		canonicalOperationCostsInTinyCents = new EnumMap<>(GasCostType.class);
		try {
			var canonicalPrices = assetsLoader.loadCanonicalPrices();
			for (var costType : GasCostType.values()) {
				if (canonicalPrices.containsKey(costType.functionality)) {
					BigDecimal costInUSD = canonicalPrices.get(costType.functionality).get(costType.subtype);
					if (costInUSD != null) {
						canonicalOperationCostsInTinyCents.put(costType,
								costInUSD.multiply(USD_TO_TINYCENTS).longValue());
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private long getCanonicalPriceInTinyCents(GasCostType gasCostType) {
		return canonicalOperationCostsInTinyCents.getOrDefault(gasCostType, COST_PROHIBITIVE);
	}

	private long getMinimumGasPriceInTinyCents(GasCostType gasCostType, Timestamp timestamp) {
		var priceInTinyCents = getCanonicalPriceInTinyCents(gasCostType);
		long tinyCentsPerGas =
				usagePricesProvider.defaultPricesGiven(ContractCall, timestamp).getServicedata().getGas();
		return (priceInTinyCents + tinyCentsPerGas - 1) / tinyCentsPerGas;
	}

	public long getMinimumPriceInTinybars(GasCostType gasCostType, Timestamp timestamp) {
		return FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp),
				getCanonicalPriceInTinyCents(gasCostType));
	}

	enum GasCostType {
		UNRECOGNIZED(HederaFunctionality.UNRECOGNIZED, SubType.UNRECOGNIZED),
		TRANSFER_FUNGIBLE(CryptoTransfer, TOKEN_FUNGIBLE_COMMON),
		TRANSFER_NFT(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE),
		TRANSFER_FUNGIBLE_CUSTOM_FEES(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
		TRANSFER_NFT_CUSTOM_FEES(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
		MINT_FUNGIBLE(TokenMint, TOKEN_FUNGIBLE_COMMON),
		MINT_NFT(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE),
		BURN_FUNGIBLE(TokenBurn, TOKEN_FUNGIBLE_COMMON),
		BURN_NFT(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE),
		ASSOCIATE(TokenAssociateToAccount, DEFAULT),
		DISSOCIATE(TokenDissociateFromAccount, DEFAULT);

		final HederaFunctionality functionality;
		final SubType subtype;

		GasCostType(HederaFunctionality functionality, SubType subtype) {
			this.functionality = functionality;
			this.subtype = subtype;
		}
	}
}
