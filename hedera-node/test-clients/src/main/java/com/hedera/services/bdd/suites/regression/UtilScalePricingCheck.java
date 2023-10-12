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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class UtilScalePricingCheck extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(UtilScalePricingCheck.class);
    private static final String NON_FUNGIBLE_TOKEN = "NON_FUNGIBLE_TOKEN";
    private static final String MAX_MINTS_PROP = "tokens.nfts.maxAllowedMints";
    private static final String ENTITY_UTILIZATION_SCALE_FACTOR_PROP = "fees.percentUtilizationScaleFactors";

    public static void main(String... args) {
        new UtilScalePricingCheck().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(nftPriceScalesWithUtilization());
    }

    // Can only be run against an isolated network since it assumes the state begins with 0 NFTs minted
    private HapiSpec nftPriceScalesWithUtilization() {
        final var civilian = "civilian";
        final var maxAllowed = 100;
        final IntFunction<String> mintOp = i -> "mint" + i;
        final var standard100ByteMetadata = ByteString.copyFromUtf8(
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
        return propertyPreservingHapiSpec("PrecompileNftMintsAreLimitedByConsThrottle")
                .preserving(MAX_MINTS_PROP, ENTITY_UTILIZATION_SCALE_FACTOR_PROP)
                .given(
                        overridingTwo(
                                MAX_MINTS_PROP,
                                "" + maxAllowed,
                                ENTITY_UTILIZATION_SCALE_FACTOR_PROP,
                                "NFT(0,1:1,25,2:1,50,5:1,75,10:1,90,50:1)"),
                        cryptoCreate(civilian).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyKey(civilian)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0))
                .when(blockingOrder(IntStream.range(1, maxAllowed + 1)
                        .mapToObj(i -> mintToken(NON_FUNGIBLE_TOKEN, List.of(standard100ByteMetadata))
                                .payingWith(civilian)
                                .blankMemo()
                                .fee(1000 * ONE_HUNDRED_HBARS)
                                .via(mintOp.apply(i)))
                        .toArray(HapiSpecOperation[]::new)))
                .then(blockingOrder(IntStream.range(1, maxAllowed + 1)
                        .mapToObj(i -> getTxnRecord(mintOp.apply(i)).noLogging().loggingOnlyFee())
                        .toArray(HapiSpecOperation[]::new)));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
