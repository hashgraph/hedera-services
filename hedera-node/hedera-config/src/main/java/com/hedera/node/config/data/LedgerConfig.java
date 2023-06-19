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

package com.hedera.node.config.data;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Ledger configuration properties.
 * @param numReservedSystemEntities the max file number of reserved system special files.
 */
@ConfigData("ledger")
public record LedgerConfig(
        @ConfigProperty(defaultValue = "5000") int maxAutoAssociations,
        @ConfigProperty(defaultValue = "100") int numSystemAccounts,
        @ConfigProperty(defaultValue = "5000000000000000000") long totalTinyBarFloat,
        @ConfigProperty(defaultValue = "0x03") Bytes id,
        @ConfigProperty(value = "changeHistorian.memorySecs", defaultValue = "20") int changeHistorianMemorySecs,
        @ConfigProperty(value = "autoRenewPeriod.maxDuration", defaultValue = "8000001")
                long autoRenewPeriodMaxDuration,
        @ConfigProperty(value = "autoRenewPeriod.minDuration", defaultValue = "2592000")
                long autoRenewPeriodMinDuration,
        @ConfigProperty(value = "xferBalanceChanges.maxLen", defaultValue = "20") int xferBalanceChangesMaxLen,
        @ConfigProperty(defaultValue = "98") long fundingAccount,
        @ConfigProperty(value = "transfers.maxLen", defaultValue = "10") int transfersMaxLen,
        @ConfigProperty(value = "tokenTransfers.maxLen", defaultValue = "10") int tokenTransfersMaxLen,
        @ConfigProperty(value = "nftTransfers.maxLen", defaultValue = "10") int nftTransfersMaxLen,
        @ConfigProperty(value = "records.maxQueryableByAccount", defaultValue = "180") int recordsMaxQueryableByAccount,
        @ConfigProperty(value = "schedule.txExpiryTimeSecs", defaultValue = "1800") int scheduleTxExpiryTimeSecs,
        @ConfigProperty(defaultValue = "750") long numReservedSystemEntities) {}
