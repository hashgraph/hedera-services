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

package com.hedera.node.app.fees;

import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses the exchange rate information and makes it available to the workflows.
 *
 * <p>All fees in Hedera are based on the exchange rate between HBAR and USD. Fees are paid in HBAR, but based on the
 * current USD price of the HBAR. The "ERT", exchange rate tool, is responsible for tracking the exchange rate of
 * various exchanges, and updating the {@link ExchangeRateSet} on a periodic basis. Currently, this is in a special
 * file, but could be from any other source. The encoded {@link Bytes} are passed to the {@link #update(Bytes)} method.
 * This <strong>MUST</strong> be done on the same thread that this manager is used by -- the manager is not threadsafe.
 *
 * <p>The {@link ExchangeRateSet} has two rates -- a "current" rate and the "next" rate. Each rate has an expiration
 * time, in <strong>consensus</strong> seconds since the epoch. During "handle", we know the consensus time. We will
 * ask the manager for the "active" rate, which is the "current" rate if the consensus time is before the expiration
 * time of the current rate, or the "next" rate if the consensus time is after the expiration time of the current rate.
 *
 * <p>If the consensus time is after the expiration time of the "next" rate, then we simply continue to use the "next"
 * rate, since we have nothing more recent to rely on.
 */
@Singleton
public final class ExchangeRateManager {
    private static final Logger logger = LogManager.getLogger(ExchangeRateManager.class);
    private static final ExchangeRateSet DEFAULT_EXCHANGE_RATES = ExchangeRateSet.DEFAULT;

    /** The parsed {@link ExchangeRateSet}. This is placed, in its entirety, into the receipt for a transaction. */
    private ExchangeRateSet exchangeRates;
    /** The "current" rate. This is from the {@link #exchangeRates}, but sanitized in case of bad input. */
    private ExchangeRate currentRate;
    /** The "next" rate. This is from the {@link #exchangeRates}, but sanitized in case of bad input. */
    private ExchangeRate nextRate;
    /** The expiration time of the "current" rate, in consensus seconds since the epoch, but cached for speed. */
    private long currentRateExpirationSeconds;

    @Inject
    public ExchangeRateManager() {
        // Initialize the exchange rate set. The default is not particularly useful, but isn't null.
        this.exchangeRates = DEFAULT_EXCHANGE_RATES;
        this.currentRate = exchangeRates.currentRateOrElse(ExchangeRate.DEFAULT);
        this.nextRate = exchangeRates.nextRateOrElse(currentRate);
        this.currentRateExpirationSeconds = 0;
    }

    /**
     * Updates the exchange rate information. MUST BE CALLED on the handle thread!
     *
     * @param bytes The protobuf encoded {@link ExchangeRateSet}.
     */
    public void update(@NonNull final Bytes bytes) {
        // Parse the exchange rate file. If we cannot parse it, we just continue with whatever our previous rate was.
        try {
            this.exchangeRates = ExchangeRateSet.PROTOBUF.parse(bytes.toReadableSequentialData());
        } catch (final Exception e) {
            // Not being able to parse the exchange rate file is not fatal, and may happen if the exchange rate file
            // was too big for a single file update for example.
            logger.warn("Unable to parse exchange rate file", e);
        }

        // The current rate should have been specified, but if for any reason it is not, we must log a warning and then
        // use some reasonable non-null default.
        final var rawCurrentRate = exchangeRates.currentRate();
        if (rawCurrentRate == null) {
            logger.warn("Exchange rate file did not contain a current rate!");
            this.currentRate = ExchangeRate.DEFAULT;
        } else {
            this.currentRate = rawCurrentRate;
        }

        // The current rate should have also specified an expiration time. If it did not, we will just set it to 0,
        // which effectively disables the current rate and moves on to the next rate.
        final var rawExpirationTime = currentRate.expirationTime();
        if (rawExpirationTime == null) {
            logger.warn("Exchange rate current rate did not contain an expiration time! Defaulting to 0");
            this.currentRateExpirationSeconds = 0;
        } else {
            this.currentRateExpirationSeconds = rawExpirationTime.seconds();
        }

        // The next rate should also have been specified. But if it wasn't, default to the "current time".
        final var rawNextRate = exchangeRates.nextRate();
        if (rawNextRate == null) {
            logger.warn("Exchange rate file did not contain a next rate! Will default to the current rate");
            this.nextRate = currentRate;
        } else {
            this.nextRate = rawNextRate;
        }
    }

    /**
     * Gets the current {@link ExchangeRateSet}. MUST BE CALLED ON THE HANDLE THREAD!!
     * @return The current {@link ExchangeRateSet}.
     */
    @NonNull
    public ExchangeRateSet exchangeRates() {
        return exchangeRates;
    }

    /**
     * Gets the {@link ExchangeRate} that should be used as of the given consensus time. MUST BE CALLED ON THE HANDLE
     * THREAD!!
     *
     * @param consensusTime The consensus time. If after the expiration time of the current rate, the next rate will
     *                      be returned. Otherwise, the current rate will be returned.
     * @return The {@link ExchangeRate} that should be used as of the given consensus time.
     */
    @NonNull
    public ExchangeRate activeRate(@NonNull final Instant consensusTime) {
        return consensusTime.getEpochSecond() > currentRateExpirationSeconds ? nextRate : currentRate;
    }
}
