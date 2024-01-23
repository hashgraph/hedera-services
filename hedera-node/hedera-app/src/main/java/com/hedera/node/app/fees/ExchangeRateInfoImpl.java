/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Default implementation of {@link ExchangeRateInfo}.
 */
public class ExchangeRateInfoImpl implements ExchangeRateInfo {

    private final ExchangeRateSet exchangeRateSet;
    private final ExchangeRate currentRate;
    private final ExchangeRate nextRate;
    private final long expirationSeconds;

    public ExchangeRateInfoImpl(@NonNull final ExchangeRateSet exchangeRateSet) {
        this.exchangeRateSet = requireNonNull(exchangeRateSet, "exchangeRateSet must not be null");
        this.currentRate = exchangeRateSet.currentRateOrThrow();
        this.nextRate = exchangeRateSet.nextRateOrThrow();
        this.expirationSeconds = currentRate.expirationTimeOrThrow().seconds();
    }

    @NonNull
    @Override
    public ExchangeRateSet exchangeRates() {
        return exchangeRateSet;
    }

    @NonNull
    @Override
    public ExchangeRate activeRate(@NonNull Instant consensusTime) {
        return consensusTime.getEpochSecond() >= expirationSeconds ? nextRate : currentRate;
    }
}
