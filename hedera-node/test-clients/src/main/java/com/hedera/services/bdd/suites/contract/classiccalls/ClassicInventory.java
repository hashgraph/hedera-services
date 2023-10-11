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

package com.hedera.services.bdd.suites.contract.classiccalls;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.base.utility.Pair;
import java.util.Set;

public class ClassicInventory {
    public static final String ALICE = "Alice";
    public static final String BOB = "Bob";
    public static final String ALPHA = "Alpha";
    public static final String BRAVO = "Bravo";
    public static final String ZERO = "Zero";
    public static final String ONE = "One";
    public static final String CRYPTO_KEY = "cryptoKey";
    public static final String FAILABLE_CALLS_CONTRACT = "FailableClassicCalls";
    public static final String FAILABLE_CONTROL_KEY = "authorizedContractKey";
    public static final Address INVALID_TOKEN_ADDRESS = HapiPropertySource.idAsHeadlongAddress(
            TokenID.newBuilder().setTokenNum(Long.MAX_VALUE).build());
    public static final Address INVALID_ACCOUNT_ADDRESS = HapiPropertySource.idAsHeadlongAddress(
            AccountID.newBuilder().setAccountNum(Long.MAX_VALUE).build());
    public static final String[] VALID_ACCOUNT_IDS = new String[] {ALICE, BOB};

    public static final String[] VALID_FUNGIBLE_TOKEN_IDS = new String[] {ALPHA, BRAVO};

    public static final String[] VALID_NON_FUNGIBLE_TOKEN_IDS = new String[] {ZERO, ONE};

    public static final Set<Pair<String, String>> VALID_ASSOCIATIONS =
            Set.of(Pair.of(ALICE, ALPHA), Pair.of(ALICE, ZERO), Pair.of(BOB, BRAVO), Pair.of(BOB, ONE));
}
