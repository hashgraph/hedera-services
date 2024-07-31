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
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("numericValidation")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
public class NumericValidationTest {
    @Contract(contract = "NumericContract", creationGas = 1_000_000L)
    static SpecContract numericContract;

    @FungibleToken(name = "fungibleToken")
    static SpecFungibleToken fungibleToken;

    @NonFungibleToken(
            numPreMints = 1,
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
     * Validate that functions calls to the HTS system contract that take numeric values
     * handle error cases correctly.
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
    @DisplayName("fail to call HAS functions with invalid amounts")
    class HASFunctionsTests {

        @Account(name = "owner", tinybarBalance = ONE_HUNDRED_HBARS)
        static SpecAccount owner;

        @Account(name = "spender")
        static SpecAccount spender;

        @HapiTest
        @DisplayName("when using hbarAllowance")
        public Stream<DynamicTest> failToApproveHbar() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("hbarApproveProxy", spender, testCase.amount())
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status()))));
        }

        @HapiTest
        @DisplayName("when using hbarApprove")
        public Stream<DynamicTest> failToHbarApprove() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("hbarApprove", owner, spender, testCase.amount())
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status()))));
        }
    }

    @Nested
    @DisplayName("fail to call Exchange Rate System contract functions")
    class ExchangeRateSystemContractTests {

        @HapiTest
        @DisplayName("when converting tinycents to tinybars")
        public Stream<DynamicTest> convertTinycentsToTinybars() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("convertTinycentsToTinybars", testCase.amount())
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status()))));
        }

        @HapiTest
        @DisplayName("when converting tinybars to tinycents")
        public Stream<DynamicTest> convertTinybarsToTinycents() {
            return zeroNegativeAndGreaterThanLong.stream()
                    .flatMap(testCase -> hapiTest(numericContract
                            .call("convertTinybarsToTinycents", testCase.amount())
                            .andAssert(txn -> txn.hasKnownStatus(testCase.status()))));
        }
    }
}
