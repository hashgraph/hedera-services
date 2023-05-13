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

package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.SystemContractAbis.Kind;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(SoftAssertionsExtension.class)
class SystemContractAbisTest {

    @InjectSoftAssertions
    SoftAssertions softly;

    @Test
    @DisplayName("Confirm all values defined in SystemContractTypes are known to AbiConstants.java")
    void confirmAllMethodSelectorsAreKnown() {
        final var allKnownABIThings = EnumSet.allOf(SystemContractAbis.class);

        final var allKnownABISelectors = allKnownABIThings.stream()
                .filter(SystemContractAbis::isMethod)
                .map(SystemContractAbis::selectorAsHex)
                .map(s -> s.substring(2)) // lose the `0x` prefix
                .map(HexFormat::fromHexDigits)
                .toList();
        softly.assertThat(allAbiConstants).as("missing ABI selectors").containsAll(allKnownABISelectors);

        final var abiSelectorMap = allKnownABIThings.stream()
                .filter(SystemContractAbis::isMethod)
                .map(abi -> Pair.of(abi.selector.toInt(), abi))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        final var mismatchedABIs = CollectionUtils.subtract(allKnownABISelectors, allAbiConstants).stream()
                .map(abiSelectorMap::get)
                .toList();
        softly.assertThat(mismatchedABIs).as("mismatched ABI selectors").isEmpty();
    }

    @Test
    @DisplayName("Confirm all names defined in SystemContractTypes meet consistency checks")
    void confirmAllAbisHaveConsistentNames() {
        final var allKnownABIThings = EnumSet.allOf(SystemContractAbis.class);

        assertThatNoException()
                .isThrownBy(() -> allKnownABIThings.forEach(abi -> SystemContractAbis.validateNameIsConsistent(
                        abi.kind, abi.isMethod() ? "METHOD" : "STRUCT,ARRAY", abi)));
    }

    @Test
    @Disabled("this test for debugging only - deliberately fails in order to dump enum strings")
    void toFullStringTest() {
        final var allKnownABIThings = EnumSet.allOf(SystemContractAbis.class);

        final var nABIMethods = (int)
                allKnownABIThings.stream().filter(SystemContractAbis::isMethod).count();
        final var nABITypes = (int)
                allKnownABIThings.stream().filter(SystemContractAbis::isType).count();

        final Function<SystemContractAbis.Kind, String> listAllSystemContractThings =
                kind -> EnumSet.allOf(SystemContractAbis.class).stream()
                        .filter(abi -> abi.kind == kind)
                        .map(SystemContractAbis::toFullString)
                        .collect(Collectors.joining("\n"));

        softly.assertThat(listAllSystemContractThings.apply(Kind.METHOD))
                .as("toString for all methods")
                .isNotBlank()
                .hasLineCount(nABIMethods);
        softly.assertThat(listAllSystemContractThings.apply(Kind.TYPE))
                .as("toString for all types")
                .isNotBlank()
                .hasLineCount(nABITypes);

        softly.assertThat(listAllSystemContractThings.apply(Kind.METHOD))
                .as("raw dump of methods")
                .isEqualTo("");
        softly.assertThat(listAllSystemContractThings.apply(Kind.TYPE))
                .as("raw dump of types")
                .isEqualTo("");
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        FOO1,  1, true
        FOO2,  2, true
        FOO1,  2, false
        FOOX,  1, false
        FOOB, 11, false
        """)
    void versionMatchValidation(@NonNull final String name, final int version, final boolean expected) {
        assertThat(SystemContractAbis.versionMatches(name, version)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            textBlock =
                    """
        foo(a,b,c);     true
        (a,b,c);        true
        foo(a,(b,c),d); true
        (a,(b,c),d);    true
        foo(a,b,c;      false
        (a,b,c;         false
        foo(a,(b,c,d);  false
        (a,(b,c,d);     false
        """)
    void signatureValidation(@NonNull final String sig, final boolean expected) {
        assertThat(SystemContractAbis.signatureValidates(sig)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            textBlock =
                    """
        METHOD; TEST_METHOD_V1; test; test(a,b,c); 1; true
        METHOD; TEST_METHOD_V1; test; test(a,b,c;  1; false
        METHOD; TEST_METH0D_V1; test; test(a,b,c); 1; false
        METHOD; TEST_METHOD_V1; test; test(a,b,c); 2; false
        METHOD; TEST_METHOD_V1; '';   (a,b,c);     1; false
        TYPE;   TEST_STRUCT_V1; '';   (a,b,c);     1; true
        TYPE;   TEST_ARRAY_V1;  '';   (a,b,c);     1; true
        TYPE;   TEST_STRUCT_V1; '';   (a,b,c;      1; false
        TYPE;   TEST_STRÜCT_V1; '';   (a,b,c);     1; false
        TYPE;   TEST_ARRAY_V1;  '';   (a,b,c);     2; false
        TYPE;   TEST_STRUCT_V1; test; test(a,b,c); 1; false
        """)
    void validateTheNameIsConsistentValidation(
            @NonNull final Kind abiKind,
            @NonNull final String enumName,
            @NonNull final String abiName,
            @NonNull final String abiSignature,
            final int abiVersion,
            final boolean expectedValidation) {
        if (expectedValidation) {
            assertThatNoException()
                    .isThrownBy(() -> SystemContractAbis.validateNameIsConsistent(
                            abiKind,
                            abiKind == Kind.METHOD ? "METHOD" : "STRUCT,ARRAY",
                            enumName,
                            abiName,
                            abiSignature,
                            abiVersion));
        } else {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SystemContractAbis.validateNameIsConsistent(
                            abiKind,
                            abiKind == Kind.METHOD ? "METHOD" : "STRUCT,ARRAY",
                            enumName,
                            abiName,
                            abiSignature,
                            abiVersion));
        }
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        0,    0,
        10,   10
        100,  100
        255,  255
        256,  256
        257,  257
        1000, 1000
        10000, 10000
        32767, 32767
        32768, 32768
        32769, 32769
        100000,      100000
        1000000000,  1000000000
        2147483647,  2147483647
        2147483648,  2147483648
        4294967295,  4294967295
        4294967296,  4294967296
        10000000000, 10000000000
        281474976710655,        281474976710655
        281474976710656,        281474976710656
        9223372036854775807,    9223372036854775807
        9223372036854775808,    0
        9223372036854775809,    0
        9999999999999999999999, 0
        """)
    void toLongOrIntSafelyTest(@NonNull final BigInteger actualBI, final long expectedBI) {

        for (final var actual : List.of(actualBI, actualBI.negate())) {
            var expected = actual.compareTo(BigInteger.ZERO) >= 0 ? expectedBI : -expectedBI;

            if (actual.compareTo(MIN_LONG) >= 0 && actual.compareTo(MAX_LONG) <= 0) {

                if (actual.compareTo(MIN_LONG) == 0) expected = Long.MIN_VALUE;

                softly.assertThat(SystemContractAbis.toLongSafely(actual)).isEqualTo(expected);

                final Long actualL = actual.longValueExact();
                softly.assertThat(SystemContractAbis.toLongSafely(actualL)).isEqualTo(expected);

                if (actual.compareTo(MIN_INT) >= 0 && actual.compareTo(MAX_INT) <= 0) {
                    final Integer actualI = actual.intValueExact();
                    softly.assertThat(SystemContractAbis.toLongSafely(actualI)).isEqualTo(expected);
                    softly.assertThat(SystemContractAbis.toIntSafely(actualI)).isEqualTo(expected);
                }

                if (actual.compareTo(MIN_SHORT) >= 0 && actual.compareTo(MAX_SHORT) <= 0) {
                    final Short actualS = actual.shortValueExact();
                    softly.assertThat(SystemContractAbis.toLongSafely(actualS)).isEqualTo(expected);
                    softly.assertThat(SystemContractAbis.toIntSafely(actualS)).isEqualTo(expected);
                }

                if (actual.compareTo(MIN_BYTE) >= 0 && actual.compareTo(MAX_BYTE) <= 0) {
                    final Byte actualB = actual.byteValueExact();
                    softly.assertThat(SystemContractAbis.toLongSafely(actualB)).isEqualTo(expected);
                    softly.assertThat(SystemContractAbis.toIntSafely(actualB)).isEqualTo(expected);
                }
            } else {
                softly.assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> SystemContractAbis.toLongSafely(actual))
                        .withMessageContaining(actual.toString(16));
            }
        }
    }

    @Test
    void badValuesToLongOrIntSafelyTest() {
        softly.assertThatIllegalArgumentException().isThrownBy(() -> SystemContractAbis.toLongSafely("foobar!"));
        softly.assertThatIllegalArgumentException().isThrownBy(() -> SystemContractAbis.toIntSafely("foobar!"));
        softly.assertThatIllegalArgumentException()
                .isThrownBy(() -> SystemContractAbis.toIntSafely(MAX_INT.add(MAX_INT)));
    }

    static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
    static final BigInteger MAX_SHORT = BigInteger.valueOf(Short.MAX_VALUE);
    static final BigInteger MAX_BYTE = BigInteger.valueOf(Byte.MAX_VALUE);
    static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    static final BigInteger MIN_INT = BigInteger.valueOf(Integer.MIN_VALUE);
    static final BigInteger MIN_SHORT = BigInteger.valueOf(Short.MIN_VALUE);
    static final BigInteger MIN_BYTE = BigInteger.valueOf(Byte.MIN_VALUE);

    // From `mono/store/contracts/precompile/AbiConstants.java` via emacs:
    final List<Integer> allAbiConstants = List.of(
            AbiConstants.ABI_ID_CRYPTO_TRANSFER,
            AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2,
            AbiConstants.ABI_ID_TRANSFER_TOKENS,
            AbiConstants.ABI_ID_TRANSFER_TOKEN,
            AbiConstants.ABI_ID_TRANSFER_NFTS,
            AbiConstants.ABI_ID_TRANSFER_NFT,
            AbiConstants.ABI_ID_MINT_TOKEN,
            AbiConstants.ABI_ID_MINT_TOKEN_V2,
            AbiConstants.ABI_ID_BURN_TOKEN,
            AbiConstants.ABI_ID_BURN_TOKEN_V2,
            AbiConstants.ABI_ID_DELETE_TOKEN,
            AbiConstants.ABI_ID_ASSOCIATE_TOKENS,
            AbiConstants.ABI_ID_ASSOCIATE_TOKEN,
            AbiConstants.ABI_ID_DISSOCIATE_TOKENS,
            AbiConstants.ABI_ID_DISSOCIATE_TOKEN,
            AbiConstants.ABI_ID_PAUSE_TOKEN,
            AbiConstants.ABI_ID_UNPAUSE_TOKEN,
            AbiConstants.ABI_ID_ALLOWANCE,
            AbiConstants.ABI_ID_APPROVE,
            AbiConstants.ABI_ID_APPROVE_NFT,
            AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL,
            AbiConstants.ABI_ID_GET_APPROVED,
            AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL,
            AbiConstants.ABI_ID_TRANSFER_FROM,
            AbiConstants.ABI_ID_TRANSFER_FROM_NFT,
            AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN,
            AbiConstants.ABI_ID_ERC_NAME,
            AbiConstants.ABI_ID_ERC_SYMBOL,
            AbiConstants.ABI_ID_ERC_DECIMALS,
            AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN,
            AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN,
            AbiConstants.ABI_ID_ERC_TRANSFER,
            AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
            AbiConstants.ABI_ID_ERC_ALLOWANCE,
            AbiConstants.ABI_ID_ERC_APPROVE,
            AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL,
            AbiConstants.ABI_ID_ERC_GET_APPROVED,
            AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL,
            AbiConstants.ABI_ID_ERC_OWNER_OF_NFT,
            AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT,
            AbiConstants.ABI_ID_HRC_ASSOCIATE,
            AbiConstants.ABI_ID_HRC_DISSOCIATE,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT,
            AbiConstants.ABI_ID_IS_FROZEN,
            AbiConstants.ABI_ID_FREEZE,
            AbiConstants.ABI_ID_UNFREEZE,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3,
            AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS,
            AbiConstants.ABI_ID_GET_TOKEN_KEY,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3,
            AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS,
            AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS,
            AbiConstants.ABI_ID_IS_KYC,
            AbiConstants.ABI_ID_GRANT_TOKEN_KYC,
            AbiConstants.ABI_ID_REVOKE_TOKEN_KYC,
            AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES,
            AbiConstants.ABI_ID_IS_TOKEN,
            AbiConstants.ABI_ID_GET_TOKEN_TYPE,
            AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2);
}
