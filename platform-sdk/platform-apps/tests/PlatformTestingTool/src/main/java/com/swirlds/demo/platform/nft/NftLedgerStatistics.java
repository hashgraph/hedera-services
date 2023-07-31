/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.nft;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.system.Platform;
import com.swirlds.config.api.Configuration;

/**
 * Statistics for operations on {@link NftLedger}.
 */
public class NftLedgerStatistics {

    private static final String NFT_CATEGORY = "NFT";

    /**
     * default half-life for statistics
     */
    private static final double DEFAULT_HALF_LIFE = 10;

    private static RunningAverageMetric mintTokenMicroSec;

    private static RunningAverageMetric transferTokenMicroSec;

    private static RunningAverageMetric burnTokenMicroSec;

    private static SpeedometerMetric mintedTokensPerSecond;

    private static SpeedometerMetric transferredTokensPerSecond;

    private static SpeedometerMetric burnedTokensPerSecond;

    private static boolean registered;

    private NftLedgerStatistics() {}

    protected static void recordBurnTokenDuration(final long value) {
        if (registered) {
            burnTokenMicroSec.update(value);
        }
    }

    protected static void recordMintTokenDuration(final long value) {
        if (registered) {
            mintTokenMicroSec.update(value);
        }
    }

    protected static void recordTransferTokenDuration(final long value) {
        if (registered) {
            transferTokenMicroSec.update(value);
        }
    }

    protected static void addBurnTokenOperation() {
        if (registered) {
            burnedTokensPerSecond.update(1);
        }
    }

    protected static void addTransferTokenOperation() {
        if (registered) {
            transferredTokensPerSecond.update(1);
        }
    }

    protected static void addMintTokenOperation() {
        if (registered) {
            mintedTokensPerSecond.update(1);
        }
    }

    /**
     * Registers the {@link NftLedger} statistics with the specified {@link Platform} instance.
     *
     * @param platform
     * 		the platform instance
     */
    public static void register(final Platform platform) {
        final Metrics metrics = platform.getContext().getMetrics();
        final Configuration configuration = platform.getContext().getConfiguration();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        mintTokenMicroSec = metrics.getOrCreate(
                new RunningAverageMetric.Config(metricsConfig, NFT_CATEGORY, "nftMintedTokenMicroSec")
                        .withDescription("avg time taken to execute the NftLedger mintToken method (in microseconds)")
                        .withHalfLife(DEFAULT_HALF_LIFE));
        transferTokenMicroSec = metrics.getOrCreate(new RunningAverageMetric.Config(
                        metricsConfig, NFT_CATEGORY, "nftTransferTokenMicroSec")
                .withDescription("avg time taken to execute the NftLedger transferToken method (in microseconds)")
                .withHalfLife(DEFAULT_HALF_LIFE));
        burnTokenMicroSec =
                metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig, NFT_CATEGORY, "nftBurnTokenMicroSec")
                        .withDescription("avg time taken to execute the NftLedger burnToken method (in microseconds)")
                        .withHalfLife(DEFAULT_HALF_LIFE));
        mintedTokensPerSecond =
                metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, NFT_CATEGORY, "mintedTokensPerSecond")
                        .withDescription("number of NFTs minted per second")
                        .withFormat(FORMAT_9_6));
        transferredTokensPerSecond = metrics.getOrCreate(
                new SpeedometerMetric.Config(metricsConfig, NFT_CATEGORY, "transferredTokensPerSecond")
                        .withDescription("number of NFTs transferred per second")
                        .withFormat(FORMAT_9_6));
        burnedTokensPerSecond =
                metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, NFT_CATEGORY, "burnedTokensPerSecond")
                        .withDescription("number of NFTs burned per second")
                        .withFormat(FORMAT_9_6));

        registered = true;
    }

    /**
     * Intended for unit testing purposes only
     */
    public static void unregister() {
        registered = false;
    }
}
