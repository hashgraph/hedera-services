/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip993;

import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThrottles;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType.SUPPLY_KEY;

import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * Verifies that throttle capacity used during a dispatch that is later reverted does not cause
 * further dispatches to be throttled. Accomplishes this by creating a contract that has two
 * expected call paths in the context of a network with a 1 TPS NFT mint throttle,
 * <ol>
 *     <li>Mints a NFT in a child dispatch, commits that dispatch, and then receives
 *     {@link ResponseCodeEnum#THROTTLED_AT_CONSENSUS} attempting a later mint.</li>
 *     <li>Mints a NFT in a child dispatch, reverts that dispatch, and then receives
 *     {@link ResponseCodeEnum#SUCCESS} attempting a later mint.</li>
 * </ol>
 */
@Tag(TOKEN)
public class ThrottleOnDispatchTest {
    @LeakyHapiTest(
            requirement = {PROPERTY_OVERRIDES, THROTTLE_OVERRIDES},
            overrides = {"tokens.nfts.mintThrottleScaleFactor", "contracts.throttle.throttleByGas"},
            throttles = "testSystemFiles/one-tps-nft-mint.json")
    final Stream<DynamicTest> throttledChildDispatchCapacityOnlyCommitsOnSuccess(
            @NonFungibleToken SpecNonFungibleToken nft,
            @Contract(contract = "ConsensusMintCheck") SpecContract consensusMintCheck) {
        return hapiTest(
                overridingTwo(
                        "tokens.nfts.mintThrottleScaleFactor", "1:1",
                        "contracts.throttle.throttleByGas", "false"),
                nft.authorizeContracts(consensusMintCheck).alsoAuthorizing(SUPPLY_KEY),
                consensusMintCheck
                        .call("mintInnerAndOuter", nft, Boolean.TRUE, new byte[][] {{(byte) 0xAB}}, new byte[][] {
                            {(byte) 0xBC}
                        })
                        .gas(2_000_000L),
                // Overriding the throttles recreates all buckets with no usage
                overridingThrottles("testSystemFiles/one-tps-nft-mint.json"),
                consensusMintCheck
                        .call("mintInnerAndOuter", nft, Boolean.FALSE, new byte[][] {{(byte) 0xCD}}, new byte[][] {
                            {(byte) 0xDE}
                        })
                        .gas(2_000_000L));
    }
}
