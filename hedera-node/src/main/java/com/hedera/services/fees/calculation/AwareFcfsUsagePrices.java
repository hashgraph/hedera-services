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
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

/**
 * Implements a {@link UsagePricesProvider} by loading the required
 * fee schedules from the Hedera FileSystem.
 *
 * @author Michael Tinker
 */
public class AwareFcfsUsagePrices implements UsagePricesProvider {
	private static final Logger log = LogManager.getLogger(AwareFcfsUsagePrices.class);

	private static EnumSet<HederaFunctionality> FUNCTIONS_WITH_TOKEN_TYPE_SPECIALIZATIONS = EnumSet.of(
			CryptoTransfer,
			TokenMint,
			TokenBurn,
			TokenAccountWipe
	);

	public static long DEFAULT_FEE = 100_000L;

	public static final FeeComponents DEFAULT_RESOURCE_USAGE_PRICES = FeeComponents.newBuilder()
			.setMin(DEFAULT_FEE)
			.setMax(DEFAULT_FEE)
			.setConstant(0).setBpt(0).setVpt(0).setRbh(0).setSbh(0).setGas(0).setTv(0).setBpr(0).setSbpr(0)
			.build();
	public static final Map<SubType, FeeData> DEFAULT_USAGE_PRICES = Map.of(SubType.DEFAULT, FeeData.newBuilder()
			.setNetworkdata(DEFAULT_RESOURCE_USAGE_PRICES)
			.setNodedata(DEFAULT_RESOURCE_USAGE_PRICES)
			.setServicedata(DEFAULT_RESOURCE_USAGE_PRICES)
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
		return DEFAULT_USAGE_PRICES.get(SubType.DEFAULT);
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
		return DEFAULT_USAGE_PRICES;
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
		return DEFAULT_USAGE_PRICES;
	}

	@Override
	public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
		return pricesGiven(function, at).get(SubType.DEFAULT);
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
		EnumMap<HederaFunctionality, Map<SubType, FeeData>> feeScheduleMap = new EnumMap<>(HederaFunctionality.class);
		for (var txnFeeSchedule : feeSchedule.getTransactionFeeScheduleList()) {
			final var function = txnFeeSchedule.getHederaFunctionality();

			Map<SubType, FeeData> map = feeScheduleMap.get(function);
			if (map == null) {
				map = new HashMap<>();
			}

			if (txnFeeSchedule.hasFeeData()) {
				final var untypedPrices = txnFeeSchedule.getFeeData();
				/* Must be from a pre-0.16.0 signed state when there were no fee sub-types */
				if (FUNCTIONS_WITH_TOKEN_TYPE_SPECIALIZATIONS.contains(function)) {
					map.put(SubType.TOKEN_FUNGIBLE_COMMON, untypedPrices);
					map.put(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, untypedPrices);
					if(function == CryptoTransfer) {
						map.put(SubType.DEFAULT, untypedPrices);
						map.put(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, untypedPrices);
						map.put(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, untypedPrices);
					}
				} else {
					map.put(SubType.DEFAULT, untypedPrices);
				}
			}

			for (var feeData : txnFeeSchedule.getFeesList()) {
				map.put(feeData.getSubType(), feeData);
			}
			feeScheduleMap.put(txnFeeSchedule.getHederaFunctionality(), map);
		}
		return feeScheduleMap;
	}
}
