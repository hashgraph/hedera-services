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

package com.hedera.node.app.info;

import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.FilesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HederaFileNumbersImpl implements HederaFileNumbers {

    private FilesConfig filesConfig;

    @Inject
    public HederaFileNumbersImpl(@NonNull final ConfigProvider configProvider) {
        final var config = configProvider.getConfiguration();
        this.filesConfig = config.getConfigData(FilesConfig.class);
    }

    @Override
    public long addressBook() {
        return filesConfig.addressBook();
    }

    @Override
    public long nodeDetails() {
        return filesConfig.nodeDetails();
    }

    @Override
    public long feeSchedules() {
        return filesConfig.feeSchedules();
    }

    @Override
    public long exchangeRates() {
        return filesConfig.exchangeRates();
    }

    @Override
    public long applicationProperties() {
        return filesConfig.networkProperties();
    }

    @Override
    public long apiPermissions() {
        return filesConfig.hapiPermissions();
    }

    @Override
    public long firstSoftwareUpdateFile() {
        return filesConfig.softwareUpdateRange().left();
    }

    @Override
    public long lastSoftwareUpdateFile() {
        return filesConfig.softwareUpdateRange().right();
    }

    @Override
    public long throttleDefinitions() {
        return filesConfig.throttleDefinitions();
    }
}
