package com.hedera.services.fees.calculation;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

/**
 * Implements a {@link UsagePricesProvider} by loading the required fee schedules from the Hedera "file system".
 */
public class AwareFcfsUsagePrices implements UsagePricesProvider {
	private static final Logger log = LogManager.getLogger(AwareFcfsUsagePrices.class);

	private static final EnumSet<SubType> ONLY_DEFAULT = EnumSet.of(DEFAULT);
	static final Map<HederaFunctionality, EnumSet<SubType>> FUNCTIONS_WITH_REQUIRED_SUBTYPES;
	static {
		FUNCTIONS_WITH_REQUIRED_SUBTYPES = new EnumMap<>(HederaFunctionality.class);
		/* The functions with non-DEFAULT prices in hapi-fees/src/main/resources/canonical-prices.json */
		List.of(TokenMint, TokenBurn, TokenAccountWipe).forEach(function ->
				FUNCTIONS_WITH_REQUIRED_SUBTYPES.put(function, EnumSet.of(
						TOKEN_FUNGIBLE_COMMON, TOKEN_NON_FUNGIBLE_UNIQUE)));
		FUNCTIONS_WITH_REQUIRED_SUBTYPES.put(TokenCreate, EnumSet.of(
				TOKEN_FUNGIBLE_COMMON, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
				TOKEN_NON_FUNGIBLE_UNIQUE, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES));
		FUNCTIONS_WITH_REQUIRED_SUBTYPES.put(CryptoTransfer, EnumSet.of(
				DEFAULT,
				TOKEN_FUNGIBLE_COMMON, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
				TOKEN_NON_FUNGIBLE_UNIQUE, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES));
	}

	public static long DEFAULT_FEE = 100_000L;
	public static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES = FeeComponents.newBuilder()
			.setMin(DEFAULT_FEE)
			.setMax(DEFAULT_FEE)
			.setConstant(0).setBpt(0).setVpt(0).setRbh(0).setSbh(0).setGas(0).setTv(0).setBpr(0).setSbpr(0)
			.build();
	public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES = Map.of(DEFAULT, FeeData.newBuilder()
			.setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES)
			.setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
			.setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
			.build());

	private final HederaFs hfs;
	private final FileNumbers fileNumbers;
	private final TransactionContext txnCtx;

	CurrentAndNextFeeSchedule feeSchedules;

	private Timestamp currFunctionUsagePricesExpiry;
	private Timestamp nextFunctionUsagePricesExpiry;

	private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
	private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;

	public AwareFcfsUsagePrices(HederaFs hfs, FileNumbers fileNumbers, TransactionContext txnCtx) {
		this.hfs = hfs;
		this.txnCtx = txnCtx;
		this.fileNumbers = fileNumbers;
	}

	@Override
	public void loadPriceSchedules() {
		var feeSchedulesId = fileNumbers.toFid(fileNumbers.feeSchedules());
		if (!hfs.exists(feeSchedulesId)) {
			throw new IllegalStateException(
					String.format("No fee schedule available at %s!", readableId(this.feeSchedules)));
		}
		try {
			var schedules = CurrentAndNextFeeSchedule.parseFrom(hfs.cat(feeSchedulesId));
			setFeeSchedules(schedules);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Corrupt fee schedules file at {}, may require remediation!", readableId(this.feeSchedules), e);
			throw new IllegalStateException(
					String.format("Fee schedule %s is corrupt!", readableId(this.feeSchedules)));
		}
	}

	@Override
	public FeeData defaultActivePrices() {
		try {
			var accessor = txnCtx.accessor();
			return defaultPricesGiven(accessor.getFunction(), accessor.getTxnId().getTransactionValidStart());
		} catch (Exception e) {
			log.warn("Using default usage prices to calculate fees for {}!", txnCtx.accessor().getSignedTxnWrapper(),
					e);
		}
		return DEFAULT_RESOURCE_PRICES.get(DEFAULT);
	}

	@Override
	public Map<SubType, FeeData> activePrices() {
		try {
			var accessor = txnCtx.accessor();
			return pricesGiven(accessor.getFunction(), accessor.getTxnId().getTransactionValidStart());
		} catch (Exception e) {
			log.warn("Using default usage prices to calculate fees for {}!", txnCtx.accessor().getSignedTxnWrapper(),
					e);
		}
		return DEFAULT_RESOURCE_PRICES;
	}

	@Override
	public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
		try {
			Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices = applicableUsagePrices(at);
			Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
			Objects.requireNonNull(usagePrices);
			return usagePrices;
		} catch (Exception e) {
			log.debug(
					"Default usage price will be used, no specific usage prices available for function {} @ {}!",
					function, Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
		}
		return DEFAULT_RESOURCE_PRICES;
	}

	@Override
	public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
		return pricesGiven(function, at).get(DEFAULT);
	}

	@Override
	public Triple<Map<SubType, FeeData>, Instant, Map<SubType, FeeData>> activePricingSequence(
			HederaFunctionality function) {
		return Triple.of(
				currFunctionUsagePrices.get(function),
				Instant.ofEpochSecond(
						currFunctionUsagePricesExpiry.getSeconds(),
						currFunctionUsagePricesExpiry.getNanos()),
				nextFunctionUsagePrices.get(function));
	}

	private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(Timestamp at) {
		if (onlyNextScheduleApplies(at)) {
			return nextFunctionUsagePrices;
		} else {
			return currFunctionUsagePrices;
		}
	}

	private boolean onlyNextScheduleApplies(Timestamp at) {
		return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds() &&
				at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
	}

	public void setFeeSchedules(CurrentAndNextFeeSchedule feeSchedules) {
		this.feeSchedules = feeSchedules;

		currFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getCurrentFeeSchedule());
		currFunctionUsagePricesExpiry = asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());

		nextFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getNextFeeSchedule());
		nextFunctionUsagePricesExpiry = asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());
	}

	private Timestamp asTimestamp(TimestampSeconds ts) {
		return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
	}

	EnumMap<HederaFunctionality, Map<SubType, FeeData>> functionUsagePricesFrom(FeeSchedule feeSchedule) {
		final EnumMap<HederaFunctionality, Map<SubType, FeeData>> allPrices = new EnumMap<>(HederaFunctionality.class);
		for (var pricingData : feeSchedule.getTransactionFeeScheduleList()) {
			final var function = pricingData.getHederaFunctionality();
			Map<SubType, FeeData> pricesMap = allPrices.get(function);
			if (pricesMap == null) {
				pricesMap = new EnumMap<>(SubType.class);
			}
			final EnumSet<SubType> requiredTypes = FUNCTIONS_WITH_REQUIRED_SUBTYPES.getOrDefault(function, ONLY_DEFAULT);
			ensurePricesMapHasRequiredTypes(pricingData, pricesMap, requiredTypes);
			System.out.println(function + " -> " + pricesMap);
			allPrices.put(pricingData.getHederaFunctionality(), pricesMap);
		}
		return allPrices;
	}

	void ensurePricesMapHasRequiredTypes(
			TransactionFeeSchedule tfs,
			Map<SubType, FeeData> pricesMap,
			EnumSet<SubType> requiredTypes
	) {
		/* The deprecated prices are the final fallback; if even they are not set, the function will be free */
		final var oldDefaultPrices = tfs.getFeeData();
		FeeData newDefaultPrices = null;
		for (var typedPrices : tfs.getFeesList()) {
			final var type = typedPrices.getSubType();
			if (requiredTypes.contains(type)) {
				pricesMap.put(type, typedPrices);
			}
			if (type == DEFAULT) {
				newDefaultPrices = typedPrices;
			}
		}
		for (var type : requiredTypes) {
			if (!pricesMap.containsKey(type)) {
				if (newDefaultPrices != null) {
					pricesMap.put(type, newDefaultPrices.toBuilder().setSubType(type).build());
				} else {
					pricesMap.put(type, oldDefaultPrices.toBuilder().setSubType(type).build());
				}
			}
		}
	}
}
