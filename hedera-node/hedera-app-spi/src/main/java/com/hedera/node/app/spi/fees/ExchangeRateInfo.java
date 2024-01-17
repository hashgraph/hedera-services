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

package com.hedera.node.app.spi.fees;

import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public interface ExchangeRateInfo {

    /**
     * Gets the current {@link ExchangeRateSet}.
     *
     * @return The current {@link ExchangeRateSet}.
     */
    @NonNull
    ExchangeRateSet exchangeRates();

    /**
     * Gets the {@link ExchangeRate} that should be used as of the given consensus time.
     *
     * @param consensusTime The consensus time. If after the expiration time of the current rate, the next rate will
     *                      be returned. Otherwise, the current rate will be returned.
     * @return The {@link ExchangeRate} that should be used as of the given consensus time.
     */
    @NonNull
    ExchangeRate activeRate(@NonNull final Instant consensusTime);
}
