/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.gas;

import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

@SuppressWarnings("java:S110") // suppress the warning that the class inheritance shouldn't be too deep
public class CustomGasCalculator extends LondonGasCalculator {

    private static final long TX_DATA_ZERO_COST = 4L;
    private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
    private static final long TX_BASE_COST = 21_000L;

    @Inject
    public CustomGasCalculator() {
        // Dagger2
    }

    @Override
    public long transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreate) {
        int zeros = 0;
        for (int i = 0; i < payload.size(); i++) {
            if (payload.get(i) == 0) {
                ++zeros;
            }
        }
        final int nonZeros = payload.size() - zeros;

        long cost = TX_BASE_COST + TX_DATA_ZERO_COST * zeros + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;

        return isContractCreate ? (cost + txCreateExtraGasCost()) : cost;
    }

    @Override
    public long logOperationGasCost(
            final MessageFrame frame, final long dataOffset, final long dataLength, final int numTopics) {
        return 1L;

        //        final var exchange = proxyUpdaterFor(frame).currentExchangeRate();
        //        long getLogStorageDuration = dynamicProperties.cacheRecordsTtl();
        //        final var gasCost = GasCalculatorHederaUtil.logOperationGasCost(
        //                usagePrices, exchange, frame, getLogStorageDuration, dataOffset, dataLength, numTopics);
        //
        //        return Math.max(super.logOperationGasCost(frame, dataOffset, dataLength, numTopics), gasCost);
    }

    @Override
    public long codeDepositGasCost(final int codeSize) {
        return 0L;
    }
}
