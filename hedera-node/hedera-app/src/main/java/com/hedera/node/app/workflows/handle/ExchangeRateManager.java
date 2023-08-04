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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.transaction.ExchangeRateSet.PROTOBUF;

import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExchangeRateManager {
    private int currHbarEquiv;
    private int currCentEquiv;
    private long currExpiry;
    private int nextHbarEquiv;
    private int nextCentEquiv;
    private long nextExpiry;
    private ExchangeRateSet exchangeRateSet;

    @Inject
    public ExchangeRateManager() {
        // For dagger
    }

    public void createUpdateExchangeRates(final Bytes contents) throws IOException {
        exchangeRateSet = PROTOBUF.parse(contents.toReadableSequentialData());
        populateExchangeRateFields();
    }

    private void populateExchangeRateFields() {
        if (exchangeRateSet.hasCurrentRate()) {
            final var currentRate = exchangeRateSet.currentRate();
            currHbarEquiv = currentRate.hbarEquiv();
            currCentEquiv = currentRate.centEquiv();
            currExpiry = currentRate.expirationTime().seconds();
        }
        if (exchangeRateSet.hasNextRate()) {
            final var nextRate = exchangeRateSet.nextRate();
            nextHbarEquiv = nextRate.hbarEquiv();
            nextCentEquiv = nextRate.centEquiv();
            nextExpiry = nextRate.expirationTime().seconds();
        }
    }

    public int getCurrHbarEquiv() {
        return currHbarEquiv;
    }

    public int getCurrCentEquiv() {
        return currCentEquiv;
    }

    public long getCurrExpiry() {
        return currExpiry;
    }

    public int getNextHbarEquiv() {
        return nextHbarEquiv;
    }

    public int getNextCentEquiv() {
        return nextCentEquiv;
    }

    public long getNextExpiry() {
        return nextExpiry;
    }

    public ExchangeRateSet getExchangeRateSet() {
        return exchangeRateSet;
    }
}
