/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.config;

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;

import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.FileID;
import javax.inject.Inject;
import javax.inject.Singleton;

/** FUTURE: This class will be moved to hedera-app-spi module in future PRs */
@Singleton
public class FileNumbers implements HederaFileNumbers {
    private final HederaNumbers hederaNums;
    private final PropertySource properties;

    private long addressBook = UNKNOWN_NUMBER;
    private long nodeDetails = UNKNOWN_NUMBER;
    private long feeSchedules = UNKNOWN_NUMBER;
    private long exchangeRates = UNKNOWN_NUMBER;
    private long apiPermissions = UNKNOWN_NUMBER;
    private long applicationProperties = UNKNOWN_NUMBER;
    private long firstUpdateFile = UNKNOWN_NUMBER;
    private long lastUpdateFile = UNKNOWN_NUMBER;
    private long throttleDefinitions = UNKNOWN_NUMBER;

    @Inject
    public FileNumbers(HederaNumbers hederaNums, @CompositeProps PropertySource properties) {
        this.hederaNums = hederaNums;
        this.properties = properties;
    }

    @Override
    public long addressBook() {
        if (addressBook == UNKNOWN_NUMBER) {
            addressBook = properties.getLongProperty("files.addressBook");
        }
        return addressBook;
    }

    @Override
    public long nodeDetails() {
        if (nodeDetails == UNKNOWN_NUMBER) {
            nodeDetails = properties.getLongProperty("files.nodeDetails");
        }
        return nodeDetails;
    }

    @Override
    public long feeSchedules() {
        if (feeSchedules == UNKNOWN_NUMBER) {
            feeSchedules = properties.getLongProperty("files.feeSchedules");
        }
        return feeSchedules;
    }

    @Override
    public long exchangeRates() {
        if (exchangeRates == UNKNOWN_NUMBER) {
            exchangeRates = properties.getLongProperty("files.exchangeRates");
        }
        return exchangeRates;
    }

    @Override
    public long applicationProperties() {
        if (applicationProperties == UNKNOWN_NUMBER) {
            applicationProperties = properties.getLongProperty("files.networkProperties");
        }
        return applicationProperties;
    }

    @Override
    public long apiPermissions() {
        if (apiPermissions == UNKNOWN_NUMBER) {
            apiPermissions = properties.getLongProperty("files.hapiPermissions");
        }
        return apiPermissions;
    }

    @Override
    public long firstSoftwareUpdateFile() {
        if (firstUpdateFile == UNKNOWN_NUMBER) {
            firstUpdateFile = properties.getEntityNumRange("files.softwareUpdateRange").getLeft();
        }
        return firstUpdateFile;
    }

    @Override
    public long lastSoftwareUpdateFile() {
        if (lastUpdateFile == UNKNOWN_NUMBER) {
            lastUpdateFile = properties.getEntityNumRange("files.softwareUpdateRange").getRight();
        }
        return lastUpdateFile;
    }

    @Override
    public boolean isSoftwareUpdateFile(final long num) {
        return firstSoftwareUpdateFile() <= num && num <= lastSoftwareUpdateFile();
    }

    @Override
    public long throttleDefinitions() {
        if (throttleDefinitions == UNKNOWN_NUMBER) {
            throttleDefinitions = properties.getLongProperty("files.throttleDefinitions");
        }
        return throttleDefinitions;
    }

    public FileID toFid(long num) {
        return FileID.newBuilder()
                .setShardNum(hederaNums.shard())
                .setRealmNum(hederaNums.realm())
                .setFileNum(num)
                .build();
    }
}
