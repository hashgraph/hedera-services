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
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("numericValidation")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
public class NumericValidationTest {
    @Contract(contract = "NumericContract", creationGas = 1_000_000L, tinybarBalance = ONE_HUNDRED_HBARS)
    static SpecContract numericContract;

    @Contract(contract = "NumericContractComplex", creationGas = 1_000_000L)
    static SpecContract numericContractComplex;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount alice;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_HUNDRED_HBARS)
    static SpecAccount bob;

    @FungibleToken(name = "fungibleToken", initialSupply = 1_000L)
    static SpecFungibleToken fungibleToken;

    @NonFungibleToken(
            numPreMints = 5,
            keys = {SUPPLY_KEY, PAUSE_KEY, ADMIN_KEY})
    static SpecNonFungibleToken nft;

    private static final String NEGATIVE_ONE = "FFFFFFFFFFFFFFFF";
    private static final String MAX_LONG_PLUS_1 = "010000000000000000";
    private static final BigInteger NEGATIVE_ONE_BIG_INT =
            new BigInteger(1, Bytes.fromHex(NEGATIVE_ONE).toByteArray());
    private static final BigInteger MAX_LONG_PLUS_1_BIG_INT =
            new BigInteger(1, Bytes.fromHex(MAX_LONG_PLUS_1).toByteArray());

    private record BigIntegerTestCase(BigInteger amount, ResponseCodeEnum status) {}

    // Big integer test cases for zero, negative, and greater than Long.MAX_VALUE amounts with expected failed status
    private final List<BigIntegerTestCase> zeroNegativeAndGreaterThanLong = List.of(
            new BigIntegerTestCase(NEGATIVE_ONE_BIG_INT, CONTRACT_REVERT_EXECUTED),
            new BigIntegerTestCase(MAX_LONG_PLUS_1_BIG_INT, CONTRACT_REVERT_EXECUTED),
            new BigIntegerTestCase(BigInteger.ZERO, CONTRACT_REVERT_EXECUTED));

    /**
     * Validate that functions calls to the HTS system contract that take numeric values handle error cases correctly.
     */
    @Nested
    @DisplayName("calls fail to approve functions with invalid amounts")
    class ApproveTests {
        @HapiTest
        @DisplayName("when using fungible token via redirect proxy contract")
        public Stream<DynamicTest> failToApproveViaProxyFungibleToken() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("approveRedirect", fungibleToken, numericContract, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using nft via redirect proxy contract")
        public Stream<DynamicTest> failToApproveViaProxyNft() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("approveRedirect", nft, numericContract, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using fungible token hts system contract")
        public Stream<DynamicTest> failToApproveFungibleToken() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("approve", fungibleToken, numericContract, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using nft hts system contract")
        public Stream<DynamicTest> failToApproveNft() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("approveNFT", nft, numericContract, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }
    }

    @Nested
    @DisplayName("calls fail to burn functions with invalid amounts")
    class BurnTests {
        @HapiTest
        @DisplayName("when using fungible tokens via the V1 version of the burn function")
        public Stream<DynamicTest> failToBurnFTV1() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("burnTokenV1", fungibleToken, NEGATIVE_ONE_BIG_INT, new long[0])
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using nft via the V1 version of the burn function")
        public Stream<DynamicTest> failToBurnNftV1() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("burnTokenV1", nft, BigInteger.ZERO, new long[] {-1L})
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using fungible tokens via the V2 version of the burn function")
        public Stream<DynamicTest> failToBurnFTV2() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("burnTokenV2", fungibleToken, -1L, new long[0])
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using nft via the V2 version of the burn function")
        public Stream<DynamicTest> failToBurnNftV2() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("burnTokenV2", nft, 0L, new long[] {-1L})
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }
    }

    @Nested
    @DisplayName("calls fail to mint functions with invalid amounts")
    class MintTests {
        @HapiTest
        @DisplayName("when using fungible tokens via the V1 version of the mint function")
        public Stream<DynamicTest> failToMintFTV1() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("mintTokenV1", fungibleToken, NEGATIVE_ONE_BIG_INT, new byte[0][0])
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using nft via the V1 version of the mint function")
        public Stream<DynamicTest> failToMintNftV1() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("mintTokenV1", nft, BigInteger.ZERO, new byte[][] {{(byte) 0x1}})
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using fungible tokens via the V2 version of the mint function")
        public Stream<DynamicTest> failToMintFTV2() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("mintTokenV2", fungibleToken, -1L, new byte[0][0])
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using nft via the V2 version of the mint function")
        public Stream<DynamicTest> failToMintNftV2() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("mintTokenV2", nft, 0L, new byte[][] {{(byte) 0x1}})
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }
    }

    @Nested
    @DisplayName("calls fail to wipe functions with invalid amounts")
    class WipeTests {

        /*
         ** We should have a test for wipe function V1 with fungible tokens but all values are either valid or
         ** the abi definition will block an attempt to send
         */

        @HapiTest
        @DisplayName("when using fungible tokens via the V2 version of the wipe function")
        public Stream<DynamicTest> failToWipeFTV2() {
            // only negative numbers are invalid. zero is considered valid and the abi definition will block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("wipeFungibleV2", fungibleToken, numericContract, -1L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using nft the wipe function")
        public Stream<DynamicTest> failToWipeNft() {
            // only negative number serial numbers are invalid. zero is considered valid and the abi definition will
            // block an attempt
            // to send number greater than Long.MAX_VALUE
            return hapiTest(numericContract
                    .call("wipeNFT", nft, numericContract, new long[] {-1L})
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }
    }

    @Nested
    @DisplayName("calls fail to static functions with invalid amounts")
    class StaticFunctionsTests {

        @HapiTest
        @DisplayName("when using tokenURI")
        public Stream<DynamicTest> failTokenURI() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("tokenURI", nft, testCase.amount)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using getTokenKey for NFT")
        public Stream<DynamicTest> failToGetTokenKeyNFT() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("getTokenKey", nft, testCase.amount)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using getTokenKey for Fungible Token")
        public Stream<DynamicTest> failToGetTokenKeyFT() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("getTokenKey", fungibleToken, testCase.amount)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using getNonFungibleTokenInfo")
        public Stream<DynamicTest> failToGetNonFungibleTokenInfo() {
            return hapiTest(numericContract
                    .call("getNonFungibleTokenInfo", nft, -1L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using getApproved")
        public Stream<DynamicTest> failToGetApproved() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("getApproved", nft, testCase.amount)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using getApprovedERC")
        public Stream<DynamicTest> failToGetApprovedERC() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("getApprovedERC", nft, testCase.amount)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using ownerOf")
        public Stream<DynamicTest> failToOwnerOf() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("ownerOf", nft, testCase.amount)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }
    }

    @Nested
    @DisplayName("calls fail to non-static create/update token functions with invalid values")
    class CreateAndUpdateTokenTests {

        @BeforeAll
        public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
            lifecycle.doAdhoc(
                    alice.transferHBarsTo(numericContractComplex, ONE_HUNDRED_HBARS * 10),
                    fungibleToken.authorizeContracts(numericContract),
                    nft.authorizeContracts(numericContract));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenWithCustomFees with FixedFee")
        public Stream<DynamicTest> failToUseCreateFungibleTokenWithCustomFees() {
            return hapiTest(numericContractComplex
                    .call("createFungibleTokenWithCustomFeesFixedFee", fungibleToken, 0L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenWithCustomFees with empty expiry")
        public Stream<DynamicTest> failToUseCreateFungibleTokenWithCustomFeesEmptyExpiry() {
            return hapiTest(numericContractComplex
                    .call("createFungibleTokenWithCustomFeesFractionalFeeEmptyExpiry", fungibleToken)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenWithCustomFees with FractionalFee")
        public Stream<DynamicTest> failToUseCreateFungibleTokenWithCustomFeesFractionalFee() {
            // Try division by 0
            final long nominator = 1;
            final long denominator = 10;
            return hapiTest(numericContractComplex
                    .call("createFungibleTokenWithCustomFeesFractionalFee", fungibleToken, nominator, denominator)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(SUCCESS)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenWithCustomFeesV3 with Negative FixedFee")
        public Stream<DynamicTest> failToUseCreateFungibleTokenWithCustomFeesV3NegativeFixedFee() {
            return hapiTest(numericContractComplex
                    .call("createFungibleTokenWithCustomFeesV3WithNegativeFixedFee", fungibleToken)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenWithCustomFeesV3 with fractionalFee where maxAmount < minAmount")
        public Stream<DynamicTest> failToUseCreateFungibleTokenWithCustomFeesV3FractionalFee() {
            // Try division by 0
            final long nominator = 1;
            final long denominator = 1;
            final long maxAmount = Long.MAX_VALUE - 1;
            final long minAmount = Long.MAX_VALUE;
            return hapiTest(numericContractComplex
                    .call(
                            "createFungibleTokenWithCustomFeesV3FractionalFee",
                            fungibleToken,
                            nominator,
                            denominator,
                            minAmount,
                            maxAmount)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using createFungibleToken")
        public Stream<DynamicTest> failToUseCreateFungible() {
            return hapiTest(numericContractComplex
                    .call("createFungibleToken", 0L, 100L, 100L, NEGATIVE_ONE_BIG_INT, NEGATIVE_ONE_BIG_INT)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.logged().hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenV2")
        public Stream<DynamicTest> failToUseCreateFungibleTokenV2() {
            return hapiTest(numericContractComplex
                    .call("createFungibleTokenV2", -1L, NEGATIVE_ONE_BIG_INT, 0L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using createFungibleTokenV2")
        public Stream<DynamicTest> failToUseCreateFungibleTokenV3() {
            return hapiTest(numericContractComplex
                    .call("createFungibleTokenV3", -1L, -1L, -1L, -1L, -1)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using failToUseNonFungibleToken")
        public Stream<DynamicTest> failToUseCreateNonFungibleTokenV1() {
            return hapiTest(numericContractComplex
                    .call("createNonFungibleToken", 0L, 0L, 0L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using failToUseNonFungibleTokenV2")
        public Stream<DynamicTest> failToUseCreateNonFungibleTokenV2() {
            return hapiTest(numericContractComplex
                    .call("createNonFungibleTokenV2", 0L, 1000L, -1L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using failToUseNonFungibleTokenV3")
        public Stream<DynamicTest> failToUseCreateNonFungibleTokenV3() {
            return hapiTest(numericContractComplex
                    .call("createNonFungibleTokenV3", -1L, -1L, -1L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using updateTokenInfoV1 for both fungible and nonFungible token")
        public Stream<DynamicTest> failToUpdateTokenInfoV1Fungible() {
            return Stream.of(fungibleToken, nft)
                    .flatMap(testCaseToken -> hapiTest(numericContractComplex
                            .call("updateTokenInfoV1", testCaseToken, 0L, 10_000L, 0L)
                            .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED))));
        }

        @HapiTest
        @DisplayName("when using updateTokenInfoV2 for both fungible and nonFungible token")
        public Stream<DynamicTest> failToUpdateTokenInfoV2Fungible() {
            return Stream.of(fungibleToken, nft)
                    .flatMap(testCaseToken -> hapiTest(numericContractComplex
                            .call("updateTokenInfoV2", testCaseToken, 0L, 10_000L, -1L)
                            .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED))));
        }

        @HapiTest
        @DisplayName("when using updateTokenInfoV3 for both fungible and nonFungible token")
        public Stream<DynamicTest> failToUpdateTokenInfoV3Fungible() {
            return Stream.of(fungibleToken, nft)
                    .flatMap(testCaseToken -> hapiTest(numericContractComplex
                            .call("updateTokenInfoV3", testCaseToken, -1L, -1L, 5L)
                            .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED))));
        }
    }

    @Nested
    @DisplayName("calls fail to non-static transfer functions with invalid values")
    class TransfersTests {

        @BeforeAll
        public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
            lifecycle.doAdhoc(
                    fungibleToken.treasury().approveTokenAllowance(fungibleToken, numericContractComplex, 100L),
                    nft.treasury().approveNFTAllowance(nft, numericContractComplex, true, List.of(1L, 2L, 3L)),
                    alice.approveCryptoAllowance(numericContractComplex, ONE_HBAR));
        }

        @HapiTest
        @DisplayName("when using cryptoTransferFungibleV1")
        public Stream<DynamicTest> failToUseCryptoTransferFungibleV1() {
            return hapiTest(numericContractComplex
                    .call("cryptoTransferFungibleV1", fungibleToken, new long[] {-5, -5}, fungibleToken.treasury(), bob)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using cryptoTransferV2 for hBar transfer")
        public Stream<DynamicTest> failToUseCryptoTransferV2() {
            return hapiTest(numericContractComplex
                    .call("cryptoTransferV2", new long[] {-5, -5}, alice, bob)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using cryptoTransferNonFungible for nft transfer")
        public Stream<DynamicTest> failToUseCryptoTransferNonFungible() {
            return hapiTest(numericContractComplex
                    .call("cryptoTransferNonFungible", nft, nft.treasury(), bob, -1L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using transferNFTs")
        public Stream<DynamicTest> failToUseTransferNFTs() {
            return hapiTest(numericContractComplex
                    .call("transferNFTs", nft, nft.treasury(), alice, new long[] {-1L})
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using transferToken")
        public Stream<DynamicTest> failToUseTransferToken() {
            return hapiTest(numericContractComplex
                    .call("transferTokenTest", fungibleToken, fungibleToken.treasury(), alice, -1L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using transferTokenERC")
        public Stream<DynamicTest> failToUseTransferTokenERC() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContractComplex
                            .call("transferTokenERC", fungibleToken, fungibleToken.treasury(), alice, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED))));
        }

        @HapiTest
        @DisplayName("when using transferNFT")
        public Stream<DynamicTest> failToUseTransferNFT() {
            return hapiTest(numericContractComplex
                    .call("transferNFTTest", nft, nft.treasury(), alice, -1L)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using transferFrom")
        public Stream<DynamicTest> failToUseTransferFrom() {
            // note: zero seems to be supported
            return hapiTest(numericContractComplex
                    .call("transferFrom", fungibleToken, fungibleToken.treasury(), alice, NEGATIVE_ONE_BIG_INT)
                    .gas(1_000_000L)
                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED)));
        }

        @HapiTest
        @DisplayName("when using transferFromERC")
        public Stream<DynamicTest> failToUseTransferFromERC() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContractComplex
                            .call("transferFromERC", fungibleToken, fungibleToken.treasury(), alice, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }

        @HapiTest
        @DisplayName("when using transferFromNFT")
        public Stream<DynamicTest> failToUseTransferNFTFrom() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContractComplex
                            .call("transferFromNFT", nft, nft.treasury(), alice, testCase.amount)
                            .gas(1_000_000L)
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status))));
        }
    }
}
