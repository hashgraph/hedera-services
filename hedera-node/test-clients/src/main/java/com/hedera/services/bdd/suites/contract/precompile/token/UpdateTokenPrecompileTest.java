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
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.SUPPLY_KEY;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("updateToken")
public class UpdateTokenPrecompileTest {
    @ContractSpec(contract = "UpdateTokenInfoContract", creationGas = 4_000_000L)
    static SpecContract updateTokenInfoContract;

    @FungibleTokenSpec(name = "immutableToken", keys = SUPPLY_KEY)
    static SpecFungibleToken immutableToken;

    @HapiTest
    public Stream<DynamicTest> updateNftTreasuryWithAndWithoutAdminKey() {
        return hapiTest(
                updateTokenInfoContract.getInfo().andAssert(HapiQueryOp::logged),
                immutableToken.getInfo().andAssert(HapiQueryOp::logged));
    }
}
