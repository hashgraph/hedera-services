package com.hedera.services.fees.calculation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;

import static com.hedera.services.legacy.logic.ApplicationConstants.DEFAULT_FEE;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static java.util.stream.Collectors.toMap;

/**
 * Implements a {@link UsagePricesProvider} by loading the required
 * fee schedules from the Hedera FileSystem.
 *
 * @author Michael Tinker
 */
public class AwareFcfsUsagePrices implements UsagePricesProvider {
	private static final Logger log = LogManager.getLogger(AwareFcfsUsagePrices.class);

	public static final FeeComponents DEFAULT_RESOURCE_USAGE_PRICES = FeeComponents.newBuilder()
			.setMin(DEFAULT_FEE)
			.setMax(DEFAULT_FEE)
			.setConstant(0).setBpt(0).setVpt(0).setRbh(0).setSbh(0).setGas(0).setTv(0).setBpr(0).setSbpr(0)
			.build();
	public static final FeeData DEFAULT_USAGE_PRICES = FeeData.newBuilder()
			.setNetworkdata(DEFAULT_RESOURCE_USAGE_PRICES)
			.setNodedata(DEFAULT_RESOURCE_USAGE_PRICES)
			.setServicedata(DEFAULT_RESOURCE_USAGE_PRICES)
			.build();

	private final HederaFs hfs;
	private final FileNumbers fileNumbers;
	private final TransactionContext txnCtx;

	CurrentAndNextFeeSchedule feeSchedules;

	Timestamp currFunctionUsagePricesExpiry;
	Timestamp nextFunctionUsagePricesExpiry;

	Map<HederaFunctionality, FeeData> currFunctionUsagePrices;
	Map<HederaFunctionality, FeeData> nextFunctionUsagePrices;

	public AwareFcfsUsagePrices(HederaFs hfs, FileNumbers fileNumbers, TransactionContext txnCtx) {
		this.hfs = hfs;
		this.txnCtx = txnCtx;
		this.fileNumbers = fileNumbers;
	}

	@Override
	public void loadPriceSchedules() {
		var feeSchedules_fileID = fileNumbers.toFid(fileNumbers.feeSchedules());
		if (!hfs.exists(feeSchedules_fileID)) {
			throw new IllegalStateException(
					String.format("No fee schedule available at %s!", readableId(feeSchedules)));
		}
		try {
			setFeeSchedules(CurrentAndNextFeeSchedule.parseFrom(hfs.cat(feeSchedules_fileID)));
		} catch (InvalidProtocolBufferException e) {
			log.warn("Corrupt fee schedules file at {}, may require remediation!", readableId(feeSchedules), e);
			throw new IllegalStateException(
					String.format("Fee schedule %s is corrupt!", readableId(feeSchedules)));
		}
	}

	@Override
	public FeeData activePrices() {
		try {
			var accessor = txnCtx.accessor();
			return pricesGiven(accessor.getFunction(), accessor.getTxnId().getTransactionValidStart());
		} catch (Exception e) {
			log.warn("Using default usage prices to calculate fees for {}!", txnCtx.accessor().getSignedTxn4Log(), e);
		}
		return DEFAULT_USAGE_PRICES;
	}

	@Override
	public FeeData pricesGiven(HederaFunctionality function, Timestamp at) {
		try {
			Map<HederaFunctionality, FeeData> functionUsagePrices = applicableUsagePrices(at);
			FeeData usagePrices = functionUsagePrices.get(function);
			Objects.requireNonNull(usagePrices);
			return usagePrices;
		} catch (Exception ignore) {
			log.warn(
					"Only default usage prices available for function {} @ {}.{}!",
					function,
					at.getSeconds(),
					at.getNanos());
		}
		return DEFAULT_USAGE_PRICES;
	}

	private Map<HederaFunctionality, FeeData> applicableUsagePrices(Timestamp at) {
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

	private Map<HederaFunctionality, FeeData> functionUsagePricesFrom(FeeSchedule feeSchedule) {
		return feeSchedule.getTransactionFeeScheduleList()
				.stream()
				.collect(toMap(TransactionFeeSchedule::getHederaFunctionality, TransactionFeeSchedule::getFeeData));
	}
}
