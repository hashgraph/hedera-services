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
import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.config.data.FilesConfig;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ExchangeRateManager {
    private static final Logger log = LogManager.getLogger(ExchangeRateManager.class);

    private int currHbarEquiv;
    private int currCentEquiv;
    private long currExpiry;
    private int nextHbarEquiv;
    private int nextCentEquiv;
    private long nextExpiry;
    private boolean isInitiated;
    private ExchangeRateSet exchangeRateSet;

    @Inject
    public ExchangeRateManager() {
        // For dagger
    }

    public void createUpdateExchangeRates(final HederaState hederaState, final Configuration configuration)
            throws IOException {
        final var readableStates = hederaState.createReadableStates(FileService.NAME);
        final ReadableKVState<FileID, File> files = readableStates.get(BLOBS_KEY);
        final var fileConfig = configuration.getConfigData(FilesConfig.class);
        final var fileId =
                FileID.newBuilder().fileNum(fileConfig.exchangeRates()).build();
        final var exchangeRateFile = files.get(fileId);

        exchangeRateSet = PROTOBUF.parse(exchangeRateFile.contents().toReadableSequentialData());
        isInitiated = true;
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

    public boolean isInitiated() {
        return isInitiated;
    }

    public ExchangeRateSet getExchangeRateSet() {
        return exchangeRateSet;
    }
}
