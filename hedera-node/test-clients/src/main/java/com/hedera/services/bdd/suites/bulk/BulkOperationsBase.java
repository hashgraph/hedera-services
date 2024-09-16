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

package com.hedera.services.bdd.suites.bulk;

import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class BulkOperationsBase {
    protected static final String OWNER = "owner";

    protected static final String NFT_TOKEN = "nftToken";

    /**
     * Create tokens and accounts
     *
     * @return array of operations
     */
    protected static SpecOperation[] createTokensAndAccounts() {
        var nftSupplyKey = "nftSupplyKey";
        final var t = new ArrayList<SpecOperation>(List.of(
                // NFT token
                newKeyNamed(nftSupplyKey),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).key(nftSupplyKey),
                tokenCreate(NFT_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(nftSupplyKey)
                        .supplyType(TokenSupplyType.INFINITE)
                        .initialSupply(0)
        ));

        return t.toArray(new SpecOperation[0]);
    }
}
