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

import com.hedera.node.config.NetworkProperty;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * Ledger configuration properties.
 * @param numReservedSystemEntities the max file number of reserved system special files.
 */
@ConfigData("ledger")
public record LedgerConfig(
        @ConfigProperty(defaultValue = "5000") @NetworkProperty int maxAutoAssociations,
        @ConfigProperty(defaultValue = "100") int numSystemAccounts,
        @ConfigProperty(defaultValue = "5000000000000000000") @Min(0) long totalTinyBarFloat,
        @ConfigProperty(defaultValue = "0x00") Bytes id,
        @ConfigProperty(value = "changeHistorian.memorySecs", defaultValue = "20") @NetworkProperty
                int changeHistorianMemorySecs,
        @ConfigProperty(value = "autoRenewPeriod.maxDuration", defaultValue = "8000001") @NetworkProperty
                long autoRenewPeriodMaxDuration,
        @ConfigProperty(value = "autoRenewPeriod.minDuration", defaultValue = "2592000") @NetworkProperty
                long autoRenewPeriodMinDuration,
        @ConfigProperty(value = "xferBalanceChanges.maxLen", defaultValue = "20") @NetworkProperty
                int xferBalanceChangesMaxLen,
        @ConfigProperty(defaultValue = "98") @NetworkProperty long fundingAccount,
        @ConfigProperty(value = "transfers.maxLen", defaultValue = "10") @NetworkProperty int transfersMaxLen,
        @ConfigProperty(value = "tokenTransfers.maxLen", defaultValue = "10") @NetworkProperty int tokenTransfersMaxLen,
        @ConfigProperty(value = "nftTransfers.maxLen", defaultValue = "10") @NetworkProperty int nftTransfersMaxLen,
        @ConfigProperty(value = "records.maxQueryableByAccount", defaultValue = "180") @NetworkProperty
                int recordsMaxQueryableByAccount,
        @ConfigProperty(value = "schedule.txExpiryTimeSecs", defaultValue = "1800") @NetworkProperty
                int scheduleTxExpiryTimeSecs,
        @ConfigProperty(defaultValue = "750") @NetworkProperty long numReservedSystemEntities) {}
