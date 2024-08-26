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

import static com.hedera.services.bdd.junit.ContextRequirement.UPGRADE_FILE_CONTENT;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.PAUSE_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.SUPPLY_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.WIPE_KEY;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATES;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("Gas Integrity Tests for Token Contracts")
@HapiTestLifecycle
@OrderedInIsolation
@SuppressWarnings("java:S1192")
public class GasCalculationIntegrityTest {

    @Contract(contract = "NumericContract", creationGas = 1_000_000L)
    static SpecContract numericContract;

    @Contract(contract = "NumericContractComplex", creationGas = 1_000_000L)
    static SpecContract numericContractComplex;

    @Contract(contract = "TokenInfoContract", creationGas = 1_000_000L)
    static SpecContract tokenInfoContract;

    @Contract(contract = "ERC20Contract", creationGas = 1_000_000L)
    static SpecContract erc20Contract;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount alice;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount bob;

    @FungibleToken
    static SpecFungibleToken token;

    @FungibleToken(
            initialSupply = 1_000L,
            maxSupply = 1_200L,
            keys = {SUPPLY_KEY, PAUSE_KEY, ADMIN_KEY, WIPE_KEY})
    static SpecFungibleToken fungibleToken;

    @NonFungibleToken(
            numPreMints = 7,
            keys = {SUPPLY_KEY, PAUSE_KEY, ADMIN_KEY, WIPE_KEY})
    static SpecNonFungibleToken nft;

    public static final long EXPIRY_RENEW = 3_000_000L;

    private final Stream<RatesProvider> testCases = Stream.of(
            new RatesProvider(30000, 16197),
            new RatesProvider(30000, 359789),
            new RatesProvider(30000, 2888899),
            new RatesProvider(30000, 269100));

    private record RatesProvider(int hBarEquiv, int centEquiv) {}

    private static final AtomicReference<ByteString> validRates = new AtomicReference<>();

    @AfterAll
    public static void afterAll(final @NonNull TestLifecycle lifecycle) {
        // Reset exchange rates
        lifecycle.doAdhoc(fileUpdate(EXCHANGE_RATES).contents(spec -> validRates.get()));
    }

    @BeforeAll
    public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
        // Fetch exchange rates before tests
        lifecycle.doAdhoc(
                //  Save exchange rates
                withOpContext((spec, opLog) -> {
                    var fetch = getFileContents(EXCHANGE_RATES);
                    CustomSpecAssert.allRunFor(spec, fetch);
                    validRates.set(fetch.getResponse()
                            .getFileGetContents()
                            .getFileContents()
                            .getContents());
                }),

                // Authorizations
                fungibleToken.authorizeContracts(numericContractComplex),
                nft.authorizeContracts(numericContractComplex),
                numericContract.associateTokens(fungibleToken, nft),

                // Approvals
                fungibleToken.treasury().approveTokenAllowance(fungibleToken, numericContractComplex, 1000L),
                nft.treasury().approveNFTAllowance(nft, numericContractComplex, true, List.of(1L, 2L, 3L, 4L, 5L)),
                alice.approveCryptoAllowance(numericContractComplex, ONE_HBAR),
                // Transfers
                fungibleToken.treasury().transferUnitsTo(numericContract, 100L, fungibleToken),
                nft.treasury().transferNFTsTo(numericContract, nft, 7L),
                alice.transferHBarsTo(numericContractComplex, ONE_HUNDRED_HBARS));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(1)
    @DisplayName("when using nft via redirect proxy contract")
    public Stream<DynamicTest> approveViaProxyNft() {
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContract
                        .call("approveRedirect", nft, bob, BigInteger.valueOf(7))
                        .gas(756_729L)
                        .via("approveRedirectTxn")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("approveRedirectTxn").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(2)
    @DisplayName("when using fungible token hts system contract")
    public Stream<DynamicTest> approveFungibleToken() {
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContract
                        .call("approve", fungibleToken, alice, BigInteger.TWO)
                        .gas(1500_877L)
                        .via("approveTxn")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("approveTxn").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(3)
    @DisplayName("when using createFungibleTokenWithCustomFeesV3 with fractionalFee")
    public Stream<DynamicTest> createFungibleTokenWithCustomFeesV3FractionalFee() {
        final long nominator = 1;
        final long denominator = 1;
        final long maxAmount = 500;
        final long minAmount = 100;
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call(
                                "createFungibleTokenWithCustomFeesV3FractionalFee",
                                nominator,
                                denominator,
                                minAmount,
                                maxAmount)
                        .gas(165_038L)
                        .sending(ONE_HUNDRED_HBARS)
                        .via("createWithCustomFeeFractional")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("createWithCustomFeeFractional").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(4)
    @DisplayName("when using createNonFungibleTokenWithCustomFeesV3 with fractionalFee")
    public Stream<DynamicTest> createNonFungibleTokenWithCustomRoyaltyFeesV3() {
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("createNonFungibleTokenWithCustomRoyaltyFeesV3", alice.getED25519KeyBytes(), 1L, 2L, 10L)
                        .gas(169_584L)
                        .sending(ONE_HUNDRED_HBARS)
                        .payingWith(alice)
                        .via("createWithCustomFeeRoyalty")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("createWithCustomFeeRoyalty").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @DisplayName("when using createFungibleToken")
    public Stream<DynamicTest> createFungible() {
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("createFungibleToken", EXPIRY_RENEW, EXPIRY_RENEW, 10000L, BigInteger.TEN, BigInteger.TWO)
                        .gas(10080_800L)
                        .sending(ONE_HUNDRED_HBARS)
                        .via("createFungibleToken")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("createFungibleToken").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(5)
    @DisplayName("when using createNonFungibleTokenV3")
    public Stream<DynamicTest> createNonFungibleTokenV3() {
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("createNonFungibleTokenV3", alice.getED25519KeyBytes(), EXPIRY_RENEW, EXPIRY_RENEW, 10L)
                        .gas(166_944L)
                        .sending(ONE_HUNDRED_HBARS)
                        .payingWith(alice)
                        .via("createNonFungibleTokenV3")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("createNonFungibleTokenV3").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(6)
    @DisplayName("when using cryptoTransferV2 for hBar transfer")
    public Stream<DynamicTest> useCryptoTransferV2() {
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("cryptoTransferV2", new long[] {-5, 5}, alice, bob)
                        .gas(33_304L)
                        .via("cryptoTransferV2")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("cryptoTransferV2").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(7)
    @DisplayName("when using cryptoTransferFungibleV1 with internal auto associate")
    public Stream<DynamicTest> useCryptoTransferFungibleV1() {
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call(
                                "cryptoTransferFungibleV1",
                                fungibleToken,
                                new long[] {-5, 5},
                                fungibleToken.treasury(),
                                bob)
                        .via("cryptoTransferFungibleV1")
                        .gas(763_480L),
                getTxnRecord("cryptoTransferFungibleV1").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(8)
    @DisplayName("when using cryptoTransferNonFungible with internal auto associate")
    public Stream<DynamicTest> useCryptoTransferNonFungible() {
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("cryptoTransferNonFungible", nft, nft.treasury(), bob, 1L)
                        .gas(761_070L)
                        .via("cryptoTransferNonFungible")
                        .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                getTxnRecord("cryptoTransferNonFungible").logged(),
                bob.transferNFTsTo(nft.treasury(), nft, 1L)));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(9)
    @DisplayName("when using transferNFTs with internal auto associate")
    public Stream<DynamicTest> useTransferNFTs() {
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("transferNFTs", nft, nft.treasury(), alice, new long[] {4L})
                        .via("transferNFTs")
                        .gas(761_519L),
                getTxnRecord("transferNFTs").logged(),
                alice.transferNFTsTo(nft.treasury(), nft, 4L)));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(10)
    @DisplayName("when using transferToken with internal auto associate")
    public Stream<DynamicTest> useTransferToken() {
        return testCases.flatMap(rates -> hapiTest(
                //  updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("transferTokenTest", fungibleToken, fungibleToken.treasury(), alice, 1L)
                        .via("transferTokenTest")
                        .gas(758_568L),
                getTxnRecord("transferTokenTest").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(11)
    @DisplayName("when using transferNFT")
    public Stream<DynamicTest> useTransferNFT() {
        // Cannot be tested directly as it requires associate from previous test
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("transferNFTTest", nft, nft.treasury(), alice, 3L)
                        .via("transferNFTTest")
                        .gas(42_235L),
                getTxnRecord("transferNFTTest").logged(),
                alice.transferNFTsTo(nft.treasury(), nft, 3L)));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(12)
    @DisplayName("when using transferFrom")
    public Stream<DynamicTest> useTransferFrom() {
        // Cannot be tested directly as it requires associate from previous test
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("transferFrom", fungibleToken, fungibleToken.treasury(), alice, BigInteger.ONE)
                        .via("transferFrom")
                        .gas(42_264L),
                getTxnRecord("transferFrom").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(13)
    @DisplayName("when using transferFromERC")
    public Stream<DynamicTest> useTransferFromERC() {
        // Cannot be tested directly as it requires associate from previous test
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("transferFromERC", fungibleToken, fungibleToken.treasury(), alice, BigInteger.ONE)
                        .via("transferFromERC")
                        .gas(44_900L),
                getTxnRecord("transferFromERC").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(14)
    @DisplayName("when using transferFromNFT")
    public Stream<DynamicTest> useTransferNFTFrom() {
        // Cannot be tested directly as it requires associate from previous test
        return testCases.flatMap(rates -> hapiTest(
                // updateRates(rates.hBarEquiv, rates.centEquiv),
                numericContractComplex
                        .call("transferFromNFT", nft, nft.treasury(), alice, BigInteger.TWO)
                        .via("transferFromNFT")
                        .gas(42_263L),
                getTxnRecord("transferFromNFT").logged(),
                alice.transferNFTsTo(nft.treasury(), nft, 2L)));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(15)
    @DisplayName("for token info call")
    public Stream<DynamicTest> checkTokenGetInfoGas() {
        return testCases.flatMap(ratesProvider -> hapiTest(
                // updateRates(ratesProvider.hBarEquiv, ratesProvider.centEquiv),
                tokenInfoContract
                        .call("getInformationForToken", token)
                        .gas(78_805L)
                        .via("tokenInfo"),
                getTxnRecord("tokenInfo").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(16)
    @DisplayName("for token custom fees call")
    public Stream<DynamicTest> checkTokenGetCustomFeesGas() {
        return testCases.flatMap(ratesProvider -> hapiTest(
                // updateRates(ratesProvider.hBarEquiv, ratesProvider.centEquiv),
                tokenInfoContract
                        .call("getCustomFeesForToken", token)
                        .gas(31_421L)
                        .via("customFees"),
                getTxnRecord("customFees").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(17)
    @DisplayName("for token name call")
    public Stream<DynamicTest> checkErc20Name() {
        return testCases.flatMap(ratesProvider -> hapiTest(
                // updateRates(ratesProvider.hBarEquiv, ratesProvider.centEquiv),
                erc20Contract.call("name", token).gas(30_207L).via("name"),
                getTxnRecord("name").logged()));
    }

    @LeakyHapiTest(requirement = UPGRADE_FILE_CONTENT)
    @Order(18)
    @DisplayName("for token balance of call")
    public Stream<DynamicTest> checkErc20BalanceOf() {
        return testCases.flatMap(ratesProvider -> hapiTest(
                // updateRates(ratesProvider.hBarEquiv, ratesProvider.centEquiv),
                erc20Contract.call("balanceOf", token, alice).gas(30_074L).via("balance"),
                getTxnRecord("balance").logged()));
    }

    private static HapiFileUpdate updateRates(final int hbarEquiv, final int centEquiv) {
        return fileUpdate(EXCHANGE_RATES).contents(spec -> {
            ByteString newRates =
                    spec.ratesProvider().rateSetWith(hbarEquiv, centEquiv).toByteString();
            return newRates;
        });
    }
}