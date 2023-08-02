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

import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static com.hederahashgraph.api.proto.java.ExchangeRateSet.parseFrom;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.config.data.FilesConfig;
import com.swirlds.config.api.Configuration;
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
            throws InvalidProtocolBufferException {
        final var readableStates = hederaState.createReadableStates(FileService.NAME);
        final ReadableKVState<FileID, File> files = readableStates.get(BLOBS_KEY);
        final var fileConfig = configuration.getConfigData(FilesConfig.class);
        final var fileId =
                FileID.newBuilder().fileNum(fileConfig.exchangeRates()).build();
        final var exchangeRateFile = files.get(fileId);

        final var exchangeRateSetProto = parseFrom(exchangeRateFile.contents().toByteArray());

        final var currentRate = exchangeRateSetProto.hasCurrentRate()
                ? convertRatesFromProto(exchangeRateSetProto.getCurrentRate())
                : null;
        final var nextRate = exchangeRateSetProto.hasCurrentRate()
                ? convertRatesFromProto(exchangeRateSetProto.getNextRate())
                : null;
        populateFields(currentRate, nextRate);
        exchangeRateSet = ExchangeRateSet.newBuilder()
                .currentRate(currentRate)
                .nextRate(nextRate)
                .build();
        isInitiated = true;
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

    private void populateFields(ExchangeRate currentRate, ExchangeRate nextRate) {
        if (currentRate != null) {
            currHbarEquiv = currentRate.hbarEquiv();
            currCentEquiv = currentRate.centEquiv();
            currExpiry = currentRate.expirationTime().seconds();
        }
        if (nextRate != null) {
            nextHbarEquiv = nextRate.hbarEquiv();
            nextCentEquiv = nextRate.centEquiv();
            nextExpiry = nextRate.expirationTime().seconds();
        }
    }

    private com.hedera.hapi.node.transaction.ExchangeRate convertRatesFromProto(
            final com.hederahashgraph.api.proto.java.ExchangeRate exchangeRateProto) {
        final var hbarEquiv = exchangeRateProto.getHbarEquiv();
        final var centEquiv = exchangeRateProto.getCentEquiv();
        final var expiry = exchangeRateProto.getExpirationTime().getSeconds();
        final var expirationTime = TimestampSeconds.newBuilder().seconds(expiry).build();

        return com.hedera.hapi.node.transaction.ExchangeRate.newBuilder()
                .hbarEquiv(hbarEquiv)
                .centEquiv(centEquiv)
                .expirationTime(expirationTime)
                .build();
    }
}
