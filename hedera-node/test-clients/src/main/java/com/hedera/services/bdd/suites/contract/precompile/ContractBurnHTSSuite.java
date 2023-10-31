/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class ContractBurnHTSSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractBurnHTSSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    public static final String THE_BURN_CONTRACT = "BurnToken";
    public static final String MULTIVERSION_BURN_CONTRACT = "MultiversionBurn";

    public static final String ALICE = "Alice";
    private static final String TOKEN = "Token";
    private static final String TOKEN_TREASURY = "TokenTreasury";
    private static final String MULTI_KEY = "purpose";
    public static final String CREATION_TX = "creationTx";
    public static final String BURN_TOKEN_WITH_EVENT = "burnTokenWithEvent";
    private static final String FIRST = "First!";
    private static final String SECOND = "Second!";
    private static final String BURN_TOKEN_V_1 = "burnTokenV1";
    private static final String BURN_TOKEN_V_2 = "burnTokenV2";

    public static void main(String... args) {
        new ContractBurnHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(burnFungibleV1andV2WithZeroAndNegativeValues(), burnNonFungibleV1andV2WithNegativeValues());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of();
    }

    @HapiTest
    private HapiSpec burnFungibleV1andV2WithZeroAndNegativeValues() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        return defaultHapiSpec("burnFungibleV1andV2WithZeroAndNegativeValues")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress::set),
                        uploadInitCode(MULTIVERSION_BURN_CONTRACT),
                        contractCreate(MULTIVERSION_BURN_CONTRACT)
                                .payingWith(ALICE)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))
                .when(
                        // Burning 0 amount for Fungible tokens should fail
                        sourcing(() -> contractCall(
                                        MULTIVERSION_BURN_CONTRACT,
                                        BURN_TOKEN_V_1,
                                        tokenAddress.get(),
                                        BigInteger.ZERO,
                                        new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        MULTIVERSION_BURN_CONTRACT, BURN_TOKEN_V_2, tokenAddress.get(), 0L, new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .logged()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Burning negative amount for Fungible tokens should fail
                        sourcing(() -> contractCall(
                                        MULTIVERSION_BURN_CONTRACT,
                                        BURN_TOKEN_V_1,
                                        tokenAddress.get(),
                                        new BigInteger("FFFFFFFFFFFFFF00", 16),
                                        new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        MULTIVERSION_BURN_CONTRACT,
                                        BURN_TOKEN_V_2,
                                        tokenAddress.get(),
                                        -1L,
                                        new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .logged()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 50));
    }

    @HapiTest
    private HapiSpec burnNonFungibleV1andV2WithNegativeValues() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        return defaultHapiSpec("burnNonFungibleV1andV2WithNegativeValues")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .exposingAddressTo(tokenAddress::set),
                        mintToken(TOKEN, List.of(copyFromUtf8(FIRST))),
                        mintToken(TOKEN, List.of(copyFromUtf8(SECOND))),
                        uploadInitCode(MULTIVERSION_BURN_CONTRACT),
                        contractCreate(MULTIVERSION_BURN_CONTRACT)
                                .payingWith(ALICE)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))
                .when(
                        // Burning negative amount for Fungible tokens should fail
                        sourcing(() -> contractCall(
                                        MULTIVERSION_BURN_CONTRACT,
                                        BURN_TOKEN_V_1,
                                        tokenAddress.get(),
                                        new BigInteger("FFFFFFFFFFFFFF00", 16),
                                        new long[] {1L})
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        MULTIVERSION_BURN_CONTRACT,
                                        BURN_TOKEN_V_2,
                                        tokenAddress.get(),
                                        -1L,
                                        new long[] {1L})
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .logged()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 2));
    }

    @NonNull
    private String getNestedContractAddress(String outerContract, HapiSpec spec) {
        return HapiPropertySource.asHexedSolidityAddress(spec.registry().getContractId(outerContract));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
