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

package com.hedera.services.bdd.suites.queries;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.services.bdd.spec.SpecOperation;
import java.util.ArrayList;
import java.util.List;

/**
 * A class with setup for Node Operator Queries tests
 */
public class NodeOperatorQueriesBase {

    // node operator account
    protected static final String NODE_OPERATOR = "operator";
    protected static final String FUNGIBLE_QUERY_TOKEN = "fungibleQueryToken";
    protected static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    protected static final String TREASURY = "treasury";
    protected static final String WIPE_KEY = "wipeKey";
    protected static final String SUPPLY_KEY = "supplyKey";
    protected static final String OWNER = "owner";
    protected static final String PAYER = "payer";
    protected static final String FILE = "file";
    protected static final String TOPIC = "topic";

    /**
     * Create Node Operator account
     * Create all other accounts
     * Create tokens
     *
     * @return array of operations
     */
    protected static SpecOperation[] createAllAccountsAndTokens() {

        final var createOperations = new ArrayList<SpecOperation>(List.of(
                cryptoCreate(OWNER).balance(0L),
                tokenCreate(FUNGIBLE_QUERY_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L)));

        return createOperations.toArray(new SpecOperation[0]);
    }
}
