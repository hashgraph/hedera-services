/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("ledger")
public record LedgerConfig(@ConfigProperty int maxAutoAssociations,
                           @ConfigProperty int numSystemAccounts,
                           @ConfigProperty long totalTinyBarFloat,
                           @ConfigProperty String id,
                           @ConfigProperty("changeHistorian.memorySecs") int changeHistorianMemorySecs,
                           @ConfigProperty("autoRenewPeriod.maxDuration") long autoRenewPeriodMaxDuration,
                           @ConfigProperty("autoRenewPeriod.minDuration") long autoRenewPeriodMinDuration,
                           @ConfigProperty("xferBalanceChanges.maxLen") int xferBalanceChangesMaxLen,
                           @ConfigProperty long fundingAccount,
                           @ConfigProperty("transfers.maxLen") int transfersMaxLen,
                           @ConfigProperty("tokenTransfers.maxLen") int tokenTransfersMaxLen,
                           @ConfigProperty("nftTransfers.maxLen") int nftTransfersMaxLen,
                           @ConfigProperty("records.maxQueryableByAccount") int recordsMaxQueryableByAccount,
                           @ConfigProperty("schedule.txExpiryTimeSecs") int scheduleTxExpiryTimeSecs) {

}
