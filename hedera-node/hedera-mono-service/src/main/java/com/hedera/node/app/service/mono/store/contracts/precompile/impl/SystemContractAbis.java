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

import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.SystemContractTypes.*;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Ground truth for everything related to system contract ABIs: their names, signatures, argument
 * decoders, etc., for all versions of each ABI
 * <p>
 * N.B.: Currently ONLY the ABIs in multiple versions are in this enum; it is intended that ALL
 * system contract ABIs go in this enum
 * N.B.: Currently there are _multiple_ sources of this information in the code base, scattered
 * about; it is intended that ALL those other sources will be refactored away, leaving only this
 * enum as the single source of information about these ABIs. Specifically: DecodingFacade and
 * ParsingConstants have versions of this data too.
 * N.B.: Currently this file needs to be maintained by hand when ABIs are added or versioned; it is
 * intended that this file be GENERATED from the actual Solidity contracts (in the smart contracts
 * repository)
 * N.B.: Need to add method return types and argument names (which could be used for various purposes)
 */
@SuppressWarnings("java:S1192") // duplicate string literals - in this case, removing duplication makes code
//                       more difficult to read
public enum SystemContractAbis {

    // HTS, structs and arrays-of-structs

    ACCOUNT_AMOUNT_STRUCT_V1(1, "accountAmount", "(address,int64)"),
    ACCOUNT_AMOUNT_STRUCT_V2(2, "accountAmount", "(address,int64,bool)"),

    EXPIRY_STRUCT_V1(1, "expiry", "(uint32,address,uint32)"),
    EXPIRY_STRUCT_V2(2, "expiry", "(int64,address,int64)"),

    FIXED_FEE_STRUCT_V1(1, "fixedFee", "(uint32,address,bool,bool,address)"),
    FIXED_FEE_STRUCT_V2(2, "fixedFee", "(int64,address,bool,bool,address)"),

    FRACTIONAL_FEE_STRUCT_V1(1, "fractionalFee", "(uint32,uint32,uint32,uint32,bool,address)"),
    FRACTIONAL_FEE_STRUCT_V2(2, "fractionalFee", "(int64,int64,int64,int64,bool,address)"),

    ROYALTY_FEE_STRUCT_V1(1, "royaltyFee", "(uint32,uint32,uint32,address,bool,address)"),
    ROYALTY_FEE_STRUCT_V2(2, "royaltyFee", "(int64,int64,int64,address,bool,address)"),

    NFT_TRANSFER_STRUCT_V1(1, "nftTransfer", "(address,address,int64)"),
    NFT_TRANSFER_STRUCT_V2(2, "nftTransfer", "(address,address,int64,bool)"),

    KEY_VALUE_STRUCT_V1(1, "keyValue", "(bool,address,bytes,bytes,address)"),
    TOKEN_KEY_STRUCT_V1(1, "tokenKey", "(uint256,%s)".formatted(KEY_VALUE_STRUCT_V1.signature)),

    HEDERA_TOKEN_STRUCT_V1(
            1,
            "hederaToken",
            "(string,string,address,string,bool,uint32,bool,%s[],%s)"
                    .formatted(TOKEN_KEY_STRUCT_V1.signature, EXPIRY_STRUCT_V1.signature)),
    HEDERA_TOKEN_STRUCT_V2(
            2,
            "hederaToken",
            "(string,string,address,string,bool,int64,bool,%s[],%s)"
                    .formatted(TOKEN_KEY_STRUCT_V1.signature, EXPIRY_STRUCT_V1.signature)),
    HEDERA_TOKEN_STRUCT_V3(
            3,
            "hederaToken",
            "(string,string,address,string,bool,int64,bool,%s[],%s)"
                    .formatted(TOKEN_KEY_STRUCT_V1.signature, EXPIRY_STRUCT_V2.signature)),

    TRANSFER_LIST_ARRAY_V1(1, "transferList", "(%s[])".formatted(ACCOUNT_AMOUNT_STRUCT_V2.signature)),

    TOKEN_TRANSFER_LIST_ARRAY_V1(
            1,
            "tokenTransferList",
            "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_STRUCT_V1.signature, NFT_TRANSFER_STRUCT_V1.signature)),
    TOKEN_TRANSFER_LIST_ARRAY_V2(
            2,
            "tokenTransferList",
            "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_STRUCT_V2.signature, NFT_TRANSFER_STRUCT_V2.signature)),

    // HTS, versioned ABIs only

    BURN_TOKEN_METHOD_V1(1, "burnToken(address,uint64,int64[])"),
    BURN_TOKEN_METHOD_V2(2, "burnToken(address,int64,int64[])"),

    CREATE_FUNGIBLE_TOKEN_METHOD_V1(
            1, "createFungibleToken(%s,uint256,uint256)".formatted(HEDERA_TOKEN_STRUCT_V1.signature)),
    CREATE_FUNGIBLE_TOKEN_METHOD_V2(
            2, "createFungibleToken(%s,uint64,uint32)".formatted(HEDERA_TOKEN_STRUCT_V2.signature)),
    CREATE_FUNGIBLE_TOKEN_METHOD_V3(
            3, "createFungibleToken(%s,int64,int32)".formatted(HEDERA_TOKEN_STRUCT_V3.signature)),

    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V1(
            1,
            "createFungibleTokenWithCustomFees(%s,uint256,uint256,%s[],%s[])"
                    .formatted(
                            HEDERA_TOKEN_STRUCT_V1.signature,
                            FIXED_FEE_STRUCT_V1.signature,
                            FRACTIONAL_FEE_STRUCT_V1.signature)),
    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V2(
            2,
            "createFungibleTokenWithCustomFees(%s,uint64,uint32,%s[],%s[])"
                    .formatted(
                            HEDERA_TOKEN_STRUCT_V2.signature,
                            FIXED_FEE_STRUCT_V1.signature,
                            FRACTIONAL_FEE_STRUCT_V1.signature)),
    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V3(
            3,
            "createFungibleTokenWithCustomFees(%s,int64,int32,%s[],%s[])"
                    .formatted(
                            HEDERA_TOKEN_STRUCT_V3.signature,
                            FIXED_FEE_STRUCT_V2.signature,
                            FRACTIONAL_FEE_STRUCT_V2.signature)),

    CREATE_NON_FUNGIBLE_TOKEN_METHOD_V1(1, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V1.signature)),
    CREATE_NON_FUNGIBLE_TOKEN_METHOD_V2(2, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V2.signature)),
    CREATE_NON_FUNGIBLE_TOKEN_METHOD_V3(3, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V3.signature)),

    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V1(
            1,
            "createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
                    .formatted(
                            HEDERA_TOKEN_STRUCT_V1.signature,
                            FIXED_FEE_STRUCT_V1.signature,
                            ROYALTY_FEE_STRUCT_V1.signature)),
    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V2(
            2,
            "createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
                    .formatted(
                            HEDERA_TOKEN_STRUCT_V2.signature,
                            FIXED_FEE_STRUCT_V1.signature,
                            ROYALTY_FEE_STRUCT_V1.signature)),
    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V3(
            3,
            "createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
                    .formatted(
                            HEDERA_TOKEN_STRUCT_V3.signature,
                            FIXED_FEE_STRUCT_V2.signature,
                            ROYALTY_FEE_STRUCT_V2.signature)),

    CRYPTO_TRANSFER_METHOD_V1(1, "cryptoTransfer(%s[])".formatted(TOKEN_TRANSFER_LIST_ARRAY_V1.signature)),
    CRYPTO_TRANSFER_METHOD_V2(
            2,
            "cryptoTransfer(%s,%s[])"
                    .formatted(TRANSFER_LIST_ARRAY_V1.signature, TOKEN_TRANSFER_LIST_ARRAY_V2.signature)),

    MINT_TOKEN_METHOD_V1(1, "mintToken(address,uint64,bytes[])"),
    MINT_TOKEN_METHOD_V2(2, "mintToken(address,int64,bytes[])"),

    UPDATE_TOKEN_EXPIRY_INFO_METHOD_V1(1, "updateTokenExpiryInfo(address,%s)".formatted(EXPIRY_STRUCT_V1.signature)),
    UPDATE_TOKEN_EXPIRY_INFO_METHOD_V2(2, "updateTokenExpiryInfo(address,%s)".formatted(EXPIRY_STRUCT_V2.signature)),

    UPDATE_TOKEN_INFO_METHOD_V1(1, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V1.signature)),
    UPDATE_TOKEN_INFO_METHOD_V2(2, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V2.signature)),
    UPDATE_TOKEN_INFO_METHOD_V3(3, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V3.signature)),

    WIPE_TOKEN_ACCOUNT_METHOD_V1(1, "wipeTokenAccount(address,address,uint32)"),
    WIPE_TOKEN_ACCOUNT_METHOD_V2(2, "wipeTokenAccount(address,address,int64)");

    /** Declare a METHOD */
    SystemContractAbis(int version, @NonNull final String signature) {
        this.kind = Kind.METHOD;
        this.version = version;
        this.signature = stripBlanks(signature);
        this.decoderSignature = stripName(addressToUint(this.signature));
        this.decoder = TypeFactory.create(this.decoderSignature);
        this.selector = Bytes.wrap(new Function(this.signature).selector());
        this.name = nameFrom(this.signature);
        if (!this.name().contains("_METHOD_"))
            throw new IllegalArgumentException("ABI METHOD must have '_METHOD_' in its name");
        if (!versionMatches(version))
            throw new IllegalArgumentException("ABI METHOD name must end in digit which it its version");
        if (!signatureValidates(this.signature))
            throw new IllegalArgumentException("ABI METHOD signature is not well-formed");
        if (this.name.isEmpty())
            throw new IllegalArgumentException("ABI METHOD must have a method name in its signature");
    }

    /** Declare a TYPE (struct or array-of-struct) */
    SystemContractAbis(final int version, @NonNull String name, @NonNull final String signature) {
        this.kind = Kind.TYPE;
        this.version = version;
        this.signature = stripBlanks(signature);
        this.decoderSignature = stripName(addressToUint(this.signature));
        this.decoder = TypeFactory.create(this.decoderSignature);
        this.selector = null;
        this.name = name;
        if (!this.name().contains("_STRUCT_") && !this.name().contains("_ARRAY_"))
            throw new IllegalArgumentException("ABI TYPE must have '_STRUCT_' or 'ARRAY_' in its name");
        if (!versionMatches(version))
            throw new IllegalArgumentException("ABI TYPE name must end in digit which it its version");
        if (!signatureValidates(this.signature))
            throw new IllegalArgumentException("ABI METHOD signature is not well-formed");
        if (!nameFrom(this.signature).isEmpty())
            throw new IllegalArgumentException("ABI TYPE must have no method name in its signature");
    }

    public enum Kind {
        METHOD,
        TYPE
    }

    public final Kind kind;
    public final int version;
    public final String signature;
    public final String decoderSignature;
    public final ABIType<Tuple> decoder;
    public final Bytes selector; // METHOD ONLY
    private final String name;

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String selectorAsHex() {
        return to8CharHexString(selector);
    }

    public boolean isMethod() {
        return kind == Kind.METHOD;
    }

    public boolean isType() {
        return kind == Kind.TYPE;
    }

    boolean versionMatches(final int version) {
        final var lastCharOfName = name().charAt(name().length() - 1);
        final var versionFromName = Character.digit(lastCharOfName, 10);
        return versionFromName == version;
    }

    static boolean signatureValidates(@NonNull final String s) {

        // Could imagine a better validation that would include checking for properly comma-separated
        // lists and that all types were known...

        // This regex matches ensures properly balanced and nested parenthesis, in Java (using
        // forward references, not recursion). The astonishing regex is from comment here -
        // https://stackoverflow.com/questions/47162098/is-it-possible-to-match-nested-brackets-with-a-regex-without-using-recursion-or/47162099#comment81280995_47163828
        // - which is a correction to an amazing answer here - https://stackoverflow.com/a/47162099/751579

        // spotless:off
        return s.matches(
                "^"
                    + "(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*))(?!.*(?=\\2$).*\\1$).)+"
                    + ".*?"
                    + "(?=\\1$)"
                    + "[^(]*"
                    + "(?!.*[()])"
                    + "\\2"
                    + "$");
    }   // spotless:on

    @Override
    public String toString() {
        return switch (kind) {
            case METHOD -> methodToString();
            case TYPE -> typeToString();
            default -> throw new IllegalStateException("somehow got called with an invalid enum");
        };
    }

    @NonNull
    private String methodToString() {
        if (kind != Kind.METHOD) throw new IllegalArgumentException("must be called with a METHOD");
        return "method %s (%d) %s[%d]: %s {%s}"
                .formatted(selectorAsHex(), selector.toInt(), name, version, signature, decoderSignature);
    }

    @NonNull
    private String typeToString() {
        if (kind != Kind.TYPE) throw new IllegalArgumentException("must be called with a STRUCT");
        return "struct %s[%d]: %s {%s}".formatted(name, version, signature, decoderSignature);
    }

    @NonNull
    static String nameFrom(@NonNull final String s) {
        // signature must look like `fooMethod(arg1,arg2,arg3)` so this just pulls out that
        // part before the first `(`
        return s.substring(0, s.indexOf('('));
    }

    @NonNull
    static String addressToUint(@NonNull final String s) {
        // for some reason the decoder library doesn't accept the Solidity type `address` and
        // requires the equivalent `bytes32` instead
        return s.replace("address", "bytes32");
    }

    @NonNull
    static String stripBlanks(@NonNull final String s) {
        return s.replaceAll("\\s+", "");
    }

    @NonNull
    static String stripName(@NonNull final String s) {
        // signature must look like `fooMethod(arg1,arg2,arg3)` so this just trims off
        // the `fooMethod` at the beginning
        return s.substring(s.indexOf('('));
    }

    @NonNull
    static String to8CharHexString(@NonNull final Bytes bytes) {
        // `Bytes` has a bunch of "toHex"-ish routines - but not one that can be asked to cough
        // up a _fixed-width_ hex value.  So you've got to pad it yourself.  And the way it is
        // done here is to glue the hex digits onto the back of 8 `0`s and then just take the last
        // 8 characters of that.
        final var shortHex = "00000000" + bytes.toUnprefixedHexString();
        return "0x" + shortHex.substring(shortHex.length() - 8);
    }

    /** Safely convert from either a BigInteger or a Long to a Long.
     * <p>
     * The method parameter decoder returns Objects. In the case of a Solidity `uint64` it will
     * be a `BigInteger`, for a `int64` it will be a `Long`.  Safely convert to a `long` (the given
     * `BigInteger.longValue` does a narrowing primitive conversion (which simply truncates to the
     * low 64 bits)).
     */
    public static long toLongSafely(@NonNull final Object bigNumeric) {
        if (bigNumeric instanceof BigInteger big) {
            try {
                return big.longValueExact();
            } catch (final ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "uint64 out of range for Java.long (0x%s)".formatted(big.toString(16)));
            }
        } else if (bigNumeric instanceof Long big) {
            return big;
        } else if (bigNumeric instanceof Integer big) {
            return big;
        } else if (bigNumeric instanceof Short big) {
            return big;
        } else if (bigNumeric instanceof Byte big) {
            return big;
        } else
            throw new IllegalArgumentException("unknown (non Number) type (%s) returned from EVM decoder"
                    .formatted(bigNumeric.getClass().getName()));
    }

    public static int toIntSafely(@NonNull final Object bigNumeric) {
        try {
            final var l = toLongSafely(bigNumeric);
            return Math.toIntExact(l);
        } catch (final ArithmeticException ex) {
            throw new IllegalArgumentException("%s out of range for Java.int".formatted(bigNumeric));
        }
    }

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
}
