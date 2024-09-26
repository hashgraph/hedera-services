package com.hedera.services.bdd.suites.nodeOperatorQueries;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

public class FreeQueriesBase {

    // node operator account
    protected static final String NODE_OPERATOR = "operator";
    protected static final String QUERY_TEST_ACCOUNT = "queryTestAccount";
    protected static final String FUNGIBLE_QUERY_TOKEN = "fungibleQueryToken";
    protected static final String OWNER = "owner";
    protected static final String PAYER = "payer";


    /**
     * Create Node Operator account
     * Create all other accounts
     * Create tokens
     *
     * @return array of operations
     */
    protected static SpecOperation[] createAllAccountsAndTokens() {
        final var t = new ArrayList<SpecOperation>(List.of(
                cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(OWNER).balance(0L),
                cryptoCreate(QUERY_TEST_ACCOUNT).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                tokenCreate(FUNGIBLE_QUERY_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L)
        ));

        return t.toArray(new SpecOperation[0]);
    }

}
