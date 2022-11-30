/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees.congestion;

import static com.hedera.node.app.service.mono.context.properties.EntityType.*;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HandleThrottle;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TxnRateFeeMultiplierSource extends DelegatingMultiplierSource {
    private static final Logger log = LogManager.getLogger(TxnRateFeeMultiplierSource.class);

    private final UsageLimits usageLimits;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public TxnRateFeeMultiplierSource(
            final GlobalDynamicProperties dynamicProperties,
            final @HandleThrottle FunctionalityThrottling throttling,
            final UsageLimits usageLimits) {
        super(
                new ThrottleMultiplierSource(
                        "logical TPS",
                        "TPS",
                        "CryptoTransfer throughput",
                        log,
                        dynamicProperties::feesMinCongestionPeriod,
                        dynamicProperties::congestionMultipliers,
                        () -> throttling.activeThrottlesFor(CryptoTransfer)));
        this.dynamicProperties = dynamicProperties;
        this.usageLimits = usageLimits;
    }

    @Override
    public long currentMultiplier(final TxnAccessor accessor) {
        final var throttleMultiplier = super.currentMultiplier(accessor);
        return switch (accessor.getFunction()) {
            case CryptoCreate -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(ACCOUNT, usageLimits.roundedAccountPercentUtil())
                    .scaling((int) throttleMultiplier);
            case ContractCreate -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(CONTRACT, usageLimits.roundedContractPercentUtil())
                    .scaling((int) throttleMultiplier);
            case FileCreate -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(FILE, usageLimits.roundedFilePercentUtil())
                    .scaling((int) throttleMultiplier);
            case TokenMint -> accessor.mintsWithMetadata()
                    ? dynamicProperties
                            .entityScaleFactors()
                            .scaleForNew(NFT, usageLimits.roundedNftPercentUtil())
                            .scaling((int) throttleMultiplier)
                    : throttleMultiplier;
            case ScheduleCreate -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(SCHEDULE, usageLimits.roundedSchedulePercentUtil())
                    .scaling((int) throttleMultiplier);
            case TokenCreate -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(TOKEN, usageLimits.roundedTokenPercentUtil())
                    .scaling((int) throttleMultiplier);
            case TokenAssociateToAccount -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(TOKEN_ASSOCIATION, usageLimits.roundedTokenRelPercentUtil())
                    .scaling((int) throttleMultiplier);
            case ConsensusCreateTopic -> dynamicProperties
                    .entityScaleFactors()
                    .scaleForNew(TOPIC, usageLimits.roundedTopicPercentUtil())
                    .scaling((int) throttleMultiplier);
            default -> throttleMultiplier;
        };
    }
}
