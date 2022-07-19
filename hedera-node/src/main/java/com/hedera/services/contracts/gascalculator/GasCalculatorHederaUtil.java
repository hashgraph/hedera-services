/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
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
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Utility methods used by Hedera adapted {@link
 * org.hyperledger.besu.evm.gascalculator.GasCalculator}
 */
public final class GasCalculatorHederaUtil {
    private static final int LOG_CONTRACT_ID_SIZE = 24;
    private static final int LOG_TOPIC_SIZE = 32;
    private static final int LOG_BLOOM_SIZE = 256;

    private GasCalculatorHederaUtil() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static long ramByteHoursTinyBarsGiven(
            final UsagePricesProvider usagePrices,
            final HbarCentExchange exchange,
            long consensusTime,
            HederaFunctionality functionType) {
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
        long feeInTinyCents = prices.getServicedata().getRbh() / 1000;
        long feeInTinyBars =
                FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
        return Math.max(1L, feeInTinyBars);
    }

    public static long calculateLogSize(int numberOfTopics, long dataSize) {
        return LOG_CONTRACT_ID_SIZE
                + LOG_BLOOM_SIZE
                + LOG_TOPIC_SIZE * (long) numberOfTopics
                + dataSize;
    }

    @SuppressWarnings("unused")
    public static long calculateStorageGasNeeded(
            long numberOfBytes,
            long durationInSeconds,
            long byteHourCostInTinyBars,
            long gasPrice) {
        long storageCostTinyBars = (durationInSeconds * byteHourCostInTinyBars) / 3600;
        return Math.round((double) storageCostTinyBars / (double) gasPrice);
    }

    public static HederaFunctionality getFunctionType(MessageFrame frame) {
        MessageFrame rootFrame = frame.getMessageFrameStack().getLast();
        return rootFrame.getContextVariable("HederaFunctionality");
    }

    @SuppressWarnings("unused")
    public static long logOperationGasCost(
            final UsagePricesProvider usagePrices,
            final HbarCentExchange exchange,
            final MessageFrame frame,
            final long storageDuration,
            final long dataOffset,
            final long dataLength,
            final int numTopics) {
        long gasPrice = frame.getGasPrice().toLong();
        long timestamp = frame.getBlockValues().getTimestamp();
        long logStorageTotalSize = GasCalculatorHederaUtil.calculateLogSize(numTopics, dataLength);
        HederaFunctionality functionType = GasCalculatorHederaUtil.getFunctionType(frame);

        return GasCalculatorHederaUtil.calculateStorageGasNeeded(
                logStorageTotalSize,
                storageDuration,
                GasCalculatorHederaUtil.ramByteHoursTinyBarsGiven(
                        usagePrices, exchange, timestamp, functionType),
                gasPrice);
    }
}
