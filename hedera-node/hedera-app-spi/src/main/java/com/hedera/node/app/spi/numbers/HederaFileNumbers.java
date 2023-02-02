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
package com.hedera.node.app.spi.numbers;

/**
 * Represents different types of special files used in the ledger. FUTURE : Implementations of these
 * classes will be moved to this module in future PRs
 */
public interface HederaFileNumbers {
    /**
     * File Number representing address book file
     *
     * @return address book file number
     */
    long addressBook();
    /**
     * File Number representing node details file
     *
     * @return node details file number
     */
    long nodeDetails();
    /**
     * File Number representing fee schedules file
     *
     * @return fee schedules file number
     */
    long feeSchedules();
    /**
     * File Number representing exchange rates file
     *
     * @return exchange rates file number
     */
    long exchangeRates();
    /**
     * File Number representing application properties file
     *
     * @return application properties file number
     */
    long applicationProperties();
    /**
     * File Number representing api permissions file
     *
     * @return api permissions file number
     */
    long apiPermissions();
    /**
     * File Number representing first software update file
     *
     * @return first software update file number
     */
    long firstSoftwareUpdateFile();
    /**
     * File Number representing last software update file
     *
     * @return last software update file number
     */
    long lastSoftwareUpdateFile();
    /**
     * File Number representing throttle definitions file
     *
     * @return throttle definitions file number
     */
    long throttleDefinitions();
    /**
     * Checks if the file is a software update file
     *
     * @return true if it is a software update file, else false
     */
    default boolean isSoftwareUpdateFile(final long num) {
        return firstSoftwareUpdateFile() <= num && num <= lastSoftwareUpdateFile();
    }
}
