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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.config.data.FilesConfig;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.swirlds.config.api.Configuration;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExchangeRateManager {
    private com.hedera.hapi.node.transaction.ExchangeRate currentRate;
    private com.hedera.hapi.node.transaction.ExchangeRate nextRate;

    @Inject
    public ExchangeRateManager() {
        // For dagger
    }

    public com.hedera.hapi.node.transaction.ExchangeRate getCurrentRate() {
        return currentRate;
    }

    public com.hedera.hapi.node.transaction.ExchangeRate getNextRate() {
        return nextRate;
    }

    public com.hedera.hapi.node.transaction.ExchangeRateSet createUpdateExchangeRates(
            HederaState hederaState, Configuration configuration) throws InvalidProtocolBufferException {

        final var readableStates = hederaState.createReadableStates(FileService.NAME);
        final ReadableKVState<FileID, File> files = readableStates.get(BLOBS_KEY);
        final var fileConfig = configuration.getConfigData(FilesConfig.class);
        final var fileId =
                FileID.newBuilder().fileNum(fileConfig.exchangeRates()).build();

        final var exchangeRateFile = files.get(fileId);

        final var exchangeRateSet =
                ExchangeRateSet.parseFrom(exchangeRateFile.contents().toByteArray());
        currentRate = exchangeRateSet.hasCurrentRate() ? convertRatesFromProto(exchangeRateSet.getCurrentRate()) : null;
        nextRate = exchangeRateSet.hasCurrentRate() ? convertRatesFromProto(exchangeRateSet.getNextRate()) : null;

        return new com.hedera.hapi.node.transaction.ExchangeRateSet(currentRate, nextRate);
    }

    private com.hedera.hapi.node.transaction.ExchangeRate convertRatesFromProto(ExchangeRate exchangeRateProto) {
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
