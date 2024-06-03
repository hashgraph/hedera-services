/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile.token;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.dsl.annotations.AccountSpec;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("updateToken")
public class UpdateTokenPrecompileTest {
    @ContractSpec(contract = "UpdateTokenInfoContract", creationGas = 4_000_000L)
    static SpecContract updateTokenInfoContract;

    @HapiTest
    @DisplayName("can only update mutable token treasury once authorized")
    public Stream<DynamicTest> canUpdateMutableTokenTreasuryOnceAuthorized(
            @AccountSpec final SpecAccount newTreasury,
            @NonFungibleTokenSpec(numPreMints = 1) final SpecNonFungibleToken mutableToken) {
        return hapiTest(
                // First confirm we CANNOT update the treasury without authorization
                updateTokenInfoContract
                        .call("updateTokenTreasury", mutableToken, newTreasury)
                        .andAssert(txn -> txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INVALID_SIGNATURE)),

                // So authorize the contract to manage both the token and the new treasury
                newTreasury.authorizeContract(updateTokenInfoContract),
                mutableToken.authorizeContract(updateTokenInfoContract),
                // Also need to associate the token with the new treasury
                newTreasury.associateTokens(mutableToken),

                // Now do a contract-managed treasury update
                updateTokenInfoContract.call("updateTokenTreasury", mutableToken, newTreasury),
                // And verify a treasury-owned NFT has the new treasury as its owner
                mutableToken.serialNo(1).assertOwnerIs(newTreasury));
    }
}
