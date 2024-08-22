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
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.PAUSE_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.SUPPLY_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.WIPE_KEY;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATES;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;

@Tag(SMART_CONTRACT)
@DisplayName("updateToken")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
@Isolated
public class GasCalculationIntegrityTest {

    @Contract(contract = "NumericContract", creationGas = 1_000_000L)
    static SpecContract numericContract;

    @Contract(contract = "NumericContractComplex", creationGas = 1_000_000L)
    static SpecContract numericContractComplex;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount alice;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount bob;

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
            new RatesProvider(10, 147),
            new RatesProvider(1, 1),
            new RatesProvider(1, 1),
            new RatesProvider(189, 10),
            new RatesProvider(30000, 552522226),
            new RatesProvider(787812, 1112),
            new RatesProvider(14444, 9999));

    private final Stream<RatesProvider> testCasesNew = Stream.of(
            new RatesProvider(30000, 16197),
            new RatesProvider(30000, 359789),
            new RatesProvider(30000, 2888899),
            new RatesProvider(30000, 269100));

    private record RatesProvider(int hBarEquiv, int centEquiv) {}

    @Nested
    @DisplayName("calls to approve functions and checks gas integrity with different exchange rates")
    class ApproveTests {

        @BeforeAll
        public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
            lifecycle.doAdhoc(
                    numericContract.associateTokens(fungibleToken, nft),
                    fungibleToken.treasury().transferUnitsTo(numericContract, 100L, fungibleToken),
                    nft.treasury().transferNFTsTo(numericContract, nft, 7L));
        }

        @HapiTest
        @DisplayName("when using nft via redirect proxy contract")
        public Stream<DynamicTest> approveViaProxyNft() {
            return hapiTest(
                    numericContract
                            .call("approveRedirect", nft, bob, BigInteger.valueOf(7))
                            .gas(756_729L)
                            .via("approveRedirectTxn")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("approveRedirectTxn").logged());
        }

        @HapiTest
        @DisplayName("when using fungible token hts system contract")
        public Stream<DynamicTest> approveFungibleToken() {
            return hapiTest(
                    numericContract
                            .call("approve", fungibleToken, alice, BigInteger.TWO)
                            .gas(742_877L)
                            .via("approveTxn")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("approveTxn").logged());
        }
    }

    @Nested
    @DisplayName("updating and creating tokens does not result in gas change when exchange rates change")
    class CreateTokenTests {

        @BeforeAll
        public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
            lifecycle.doAdhoc(
                    fungibleToken.authorizeContracts(numericContractComplex),
                    nft.authorizeContracts(numericContractComplex),
                    alice.transferHBarsTo(numericContractComplex, ONE_HUNDRED_HBARS),
                    numericContractComplex.getBalance().andAssert(balance -> balance.hasTinyBars(ONE_HUNDRED_HBARS)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenWithCustomFeesV3 with fractionalFee")
        public Stream<DynamicTest> createFungibleTokenWithCustomFeesV3FractionalFee() {
            final long nominator = 1;
            final long denominator = 1;
            final long maxAmount = 500;
            final long minAmount = 100;
            return hapiTest(
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
                    getTxnRecord("createWithCustomFeeFractional").logged());
        }

        @HapiTest
        @DisplayName("when using createNonFungibleTokenWithCustomFeesV3 with fractionalFee where denominator is bad")
        public Stream<DynamicTest> createNonFungibleTokenWithCustomRoyaltyFeesV3WithBadDenominator() {
            return hapiTest(
                    numericContractComplex
                            .call(
                                    "createNonFungibleTokenWithCustomRoyaltyFeesV3",
                                    alice.getED25519KeyBytes(),
                                    1L,
                                    2L,
                                    10L)
                            .gas(169_584L)
                            .sending(ONE_HUNDRED_HBARS)
                            .payingWith(alice)
                            .via("createWithCustomFeeRoyalty")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("createWithCustomFeeRoyalty").logged());
        }

        @HapiTest
        @DisplayName("when using createFungibleToken")
        public Stream<DynamicTest> createFungible() {
            return hapiTest(
                    numericContractComplex
                            .call(
                                    "createFungibleToken",
                                    EXPIRY_RENEW,
                                    EXPIRY_RENEW,
                                    10000L,
                                    BigInteger.TEN,
                                    BigInteger.TWO)
                            .gas(165_800L)
                            .sending(ONE_HUNDRED_HBARS)
                            .via("createFungibleToken")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("createFungibleToken").logged());
        }

        @HapiTest
        @DisplayName("when using createNonFungibleTokenV3")
        public Stream<DynamicTest> createNonFungibleTokenV3() {
            return hapiTest(
                    numericContractComplex
                            .call(
                                    "createNonFungibleTokenV3",
                                    alice.getED25519KeyBytes(),
                                    EXPIRY_RENEW,
                                    EXPIRY_RENEW,
                                    10L)
                            .gas(166_944L)
                            .sending(ONE_HUNDRED_HBARS)
                            .payingWith(alice)
                            .via("createNonFungibleTokenV3")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("createNonFungibleTokenV3").logged());
        }
    }

    @Nested
    @DisplayName("calls to transfer functions and checks gas integrity with different exchange rates")
    class TransfersTests {

        @BeforeAll
        public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
            lifecycle.doAdhoc(
                    fungibleToken.treasury().approveTokenAllowance(fungibleToken, numericContractComplex, 1000L),
                    nft.treasury().approveNFTAllowance(nft, numericContractComplex, true, List.of(1L, 2L, 3L, 4L, 5L)),
                    alice.approveCryptoAllowance(numericContractComplex, ONE_HBAR));
        }

        @HapiTest
        @DisplayName("lazyCreate")
        public Stream<DynamicTest> lazyCreate() {
            return hapiTest(
                    numericContractComplex
                            .call("cryptoTransferV2", new long[] {-5, 5}, alice, bob)
                            .gas(1_000_000L)
                            .via("cryptoTransferV2")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("cryptoTransferV2").logged());
        }

        @HapiTest
        @DisplayName("when using cryptoTransferFungibleV1")
        public Stream<DynamicTest> useCryptoTransferFungibleV1() {
            return hapiTest(numericContractComplex
                    .call("cryptoTransferFungibleV1", fungibleToken, new long[] {-5, 5}, fungibleToken.treasury(), bob)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using cryptoTransferV2 for hBar transfer")
        public Stream<DynamicTest> useCryptoTransferV2() {
            return hapiTest(numericContractComplex
                    .call("cryptoTransferV2", new long[] {-5, 5}, alice, bob)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using cryptoTransferNonFungible for nft transfer")
        public Stream<DynamicTest> useCryptoTransferNonFungible() {
            return hapiTest(
                    numericContractComplex
                            .call("cryptoTransferNonFungible", nft, nft.treasury(), bob, 1L)
                            .gas(761_070L)
                            .via("cryptoTransferNonFungible")
                            .andAssert(txn -> txn.hasKnownStatus(SUCCESS)),
                    getTxnRecord("cryptoTransferNonFungible").logged());
        }

        @HapiTest
        @DisplayName("when using transferNFTs with invalid serial numbers")
        public Stream<DynamicTest> useTransferNFTs() {
            return hapiTest(numericContractComplex
                    .call("transferNFTs", nft, nft.treasury(), alice, new long[] {4L})
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using transferToken with negative amount")
        public Stream<DynamicTest> useTransferToken() {
            return hapiTest(numericContractComplex
                    .call("transferTokenTest", fungibleToken, fungibleToken.treasury(), alice, 1L)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using transferNFT")
        public Stream<DynamicTest> useTransferNFT() {
            return hapiTest(numericContractComplex
                    .call("transferNFTTest", nft, nft.treasury(), alice, 3L)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using transferFrom")
        public Stream<DynamicTest> useTransferFrom() {
            return hapiTest(numericContractComplex
                    .call("transferFrom", fungibleToken, fungibleToken.treasury(), alice, BigInteger.ONE)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using transferFromERC")
        public Stream<DynamicTest> useTransferFromERC() {
            return hapiTest(numericContractComplex
                    .call("transferFromERC", fungibleToken, fungibleToken.treasury(), alice, BigInteger.ONE)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("when using transferFromNFT")
        public Stream<DynamicTest> useTransferNFTFrom() {
            return hapiTest(numericContractComplex
                    .call("transferFromNFT", nft, nft.treasury(), alice, BigInteger.TWO)
                    .gas(1_000_000L));
        }
    }

    @Nested
    @OrderedInIsolation
    @DisplayName("when calling view functions")
    class ViewFunctions {

        @Contract(contract = "TokenInfoContract", creationGas = 1_000_000L)
        static SpecContract tokenInfoContract;

        @Contract(contract = "ERC20Contract", creationGas = 1_000_000L)
        static SpecContract erc20Contract;

        @FungibleToken
        static SpecFungibleToken token;

        @HapiTest
        @Order(1)
        @DisplayName("for token info call")
        public Stream<DynamicTest> checkTokenGetInfoGas() {
            return testCases.flatMap(ratesProvider -> hapiTest(
                    tokenInfoContract
                            .call("getInformationForToken", token)
                            .gas(78_805L)
                            .via("tokenInfo"),
                    getTxnRecord("tokenInfo").logged()));
        }

        @HapiTest
        @Order(2)
        @DisplayName("for token custom fees call")
        public Stream<DynamicTest> checkTokenGetCustomFeesGas() {
            return testCases.flatMap(ratesProvider -> hapiTest(
                    tokenInfoContract
                            .call("getCustomFeesForToken", token)
                            .gas(31_421L)
                            .via("customFees"),
                    getTxnRecord("customFees").logged()));
        }

        @HapiTest
        @Order(3)
        @DisplayName("for token name call")
        public Stream<DynamicTest> checkErc20Name() {
            return testCases.flatMap(ratesProvider -> hapiTest(
                    erc20Contract.call("name", token).gas(30_207L).via("name"),
                    getTxnRecord("name").logged()));
        }

        @HapiTest
        @Order(4)
        @DisplayName("for token balance of call")
        public Stream<DynamicTest> checkErc20BalanceOf() {
            return testCases.flatMap(ratesProvider -> hapiTest(
                    erc20Contract.call("balanceOf", token, alice).gas(30_074L).via("balance"),
                    getTxnRecord("balance").logged()));
        }
    }

    private HapiFileUpdate updateRates(final int hbarEquiv, final int centEquiv) {
        return fileUpdate(EXCHANGE_RATES).contents(spec -> {
            ByteString newRates =
                    spec.ratesProvider().rateSetWith(hbarEquiv, centEquiv).toByteString();
            spec.registry().saveBytes("midnightRate", newRates);
            return newRates;
        });
    }
}
