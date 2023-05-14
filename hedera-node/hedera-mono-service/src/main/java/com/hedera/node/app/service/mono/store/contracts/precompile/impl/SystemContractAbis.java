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

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;

/**
 * Ground truth for everything related to system contract ABIs: their names, signatures, argument
 * decoders, etc., for all versions of each ABI
 * <p>
 * N.B.: Currently ONLY the ABIs in multiple versions are in this enum; it is intended that ALL
 * system contract ABIs go in this enum
 * <p>
 * N.B.: Currently there are _multiple_ sources of this information in the code base, scattered
 * about; it is intended that ALL those other sources will be refactored away, leaving only this
 * enum as the single source of information about these ABIs. Specifically: ParsingConstants and
 * AbiConstants have versions of this data.
 * <p>
 * N.B.: Currently this file needs to be maintained by hand when ABIs are added or versioned; it is
 * intended that this file be GENERATED from the actual Solidity contracts (in the smart contracts
 * repository)
 * <p>
 * N.B.: Need to add method return types and argument names (which could be used for various purposes)
 */
@SuppressWarnings("java:S1192") // duplicate string literals - in this case, removing duplication makes code
//                                 more difficult to read
public enum SystemContractAbis {

    // N.B.: Each method and type is versioned independently and their version numbers are
    // sequential and _not_ related to release tags in the smart contracts repository.

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
    TOKEN_KEY_STRUCT_V1(1, "tokenKey", "(uint256,%s)".formatted(KEY_VALUE_STRUCT_V1)),

    HEDERA_TOKEN_STRUCT_V1(
            1,
            "hederaToken",
            "(string,string,address,string,bool,uint32,bool,%s[],%s)".formatted(TOKEN_KEY_STRUCT_V1, EXPIRY_STRUCT_V1)),
    HEDERA_TOKEN_STRUCT_V2(
            2,
            "hederaToken",
            "(string,string,address,string,bool,int64,bool,%s[],%s)".formatted(TOKEN_KEY_STRUCT_V1, EXPIRY_STRUCT_V1)),
    HEDERA_TOKEN_STRUCT_V3(
            3,
            "hederaToken",
            "(string,string,address,string,bool,int64,bool,%s[],%s)".formatted(TOKEN_KEY_STRUCT_V1, EXPIRY_STRUCT_V2)),

    TRANSFER_LIST_ARRAY_V1(1, "transferList", "(%s[])".formatted(ACCOUNT_AMOUNT_STRUCT_V2)),

    TOKEN_TRANSFER_LIST_ARRAY_V1(
            1, "tokenTransferList", "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_STRUCT_V1, NFT_TRANSFER_STRUCT_V1)),
    TOKEN_TRANSFER_LIST_ARRAY_V2(
            2, "tokenTransferList", "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_STRUCT_V2, NFT_TRANSFER_STRUCT_V2)),

    // HTS, versioned ABIs only

    BURN_TOKEN_METHOD_V1(1, "burnToken(address,uint64,int64[])"),
    BURN_TOKEN_METHOD_V2(2, "burnToken(address,int64,int64[])"),

    CREATE_FUNGIBLE_TOKEN_METHOD_V1(1, "createFungibleToken(%s,uint256,uint256)".formatted(HEDERA_TOKEN_STRUCT_V1)),
    CREATE_FUNGIBLE_TOKEN_METHOD_V2(2, "createFungibleToken(%s,uint64,uint32)".formatted(HEDERA_TOKEN_STRUCT_V2)),
    CREATE_FUNGIBLE_TOKEN_METHOD_V3(3, "createFungibleToken(%s,int64,int32)".formatted(HEDERA_TOKEN_STRUCT_V3)),

    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V1(
            1,
            "createFungibleTokenWithCustomFees(%s,uint256,uint256,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V1, FIXED_FEE_STRUCT_V1, FRACTIONAL_FEE_STRUCT_V1)),
    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V2(
            2,
            "createFungibleTokenWithCustomFees(%s,uint64,uint32,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V2, FIXED_FEE_STRUCT_V1, FRACTIONAL_FEE_STRUCT_V1)),
    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V3(
            3,
            "createFungibleTokenWithCustomFees(%s,int64,int32,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V3, FIXED_FEE_STRUCT_V2, FRACTIONAL_FEE_STRUCT_V2)),

    CREATE_NON_FUNGIBLE_TOKEN_METHOD_V1(1, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V1)),
    CREATE_NON_FUNGIBLE_TOKEN_METHOD_V2(2, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V2)),
    CREATE_NON_FUNGIBLE_TOKEN_METHOD_V3(3, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V3)),

    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V1(
            1,
            "createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V1, FIXED_FEE_STRUCT_V1, ROYALTY_FEE_STRUCT_V1)),
    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V2(
            2,
            "createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V2, FIXED_FEE_STRUCT_V1, ROYALTY_FEE_STRUCT_V1)),
    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_METHOD_V3(
            3,
            "createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V3, FIXED_FEE_STRUCT_V2, ROYALTY_FEE_STRUCT_V2)),

    CRYPTO_TRANSFER_METHOD_V1(1, "cryptoTransfer(%s[])".formatted(TOKEN_TRANSFER_LIST_ARRAY_V1)),
    CRYPTO_TRANSFER_METHOD_V2(
            2, "cryptoTransfer(%s,%s[])".formatted(TRANSFER_LIST_ARRAY_V1, TOKEN_TRANSFER_LIST_ARRAY_V2)),

    MINT_TOKEN_METHOD_V1(1, "mintToken(address,uint64,bytes[])"),
    MINT_TOKEN_METHOD_V2(2, "mintToken(address,int64,bytes[])"),

    UPDATE_TOKEN_EXPIRY_INFO_METHOD_V1(1, "updateTokenExpiryInfo(address,%s)".formatted(EXPIRY_STRUCT_V1)),
    UPDATE_TOKEN_EXPIRY_INFO_METHOD_V2(2, "updateTokenExpiryInfo(address,%s)".formatted(EXPIRY_STRUCT_V2)),

    UPDATE_TOKEN_INFO_METHOD_V1(1, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V1)),
    UPDATE_TOKEN_INFO_METHOD_V2(2, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V2)),
    UPDATE_TOKEN_INFO_METHOD_V3(3, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V3)),

    WIPE_TOKEN_ACCOUNT_METHOD_V1(1, "wipeTokenAccount(address,address,uint32)"),
    WIPE_TOKEN_ACCOUNT_METHOD_V2(2, "wipeTokenAccount(address,address,int64)"),

    // HTS, non-versioned ABIs here (for now - ultimately, everything should be put in alphabetical
    // order for easier reading)

    UPDATE_TOKEN_KEYS_METHOD_V1(1, "updateTokenKeys(address,%s[])".formatted(TOKEN_KEY_STRUCT_V1));

    /** Declare a METHOD */
    SystemContractAbis(int version, @NonNull final String signature) {
        this.kind = Kind.METHOD;
        this.version = version;
        this.signature = stripBlanks(signature);
        this.decoderSignature = stripName(addressToUint(this.signature));
        this.decoder = TypeFactory.create(this.decoderSignature);
        this.selector = Bytes.wrap(new Function(this.signature).selector());
        this.name = nameFrom(this.signature);
    }

    /** Declare a TYPE (struct or array-of-struct) */
    SystemContractAbis(final int version, @NonNull String name, @NonNull final String signature) {
        this.kind = Kind.TYPE;
        this.version = version;
        this.signature = stripBlanks(signature);
        this.decoderSignature = stripName(addressToUint(this.signature));
        this.decoder = TypeFactory.create(this.decoderSignature);
        this.selector = Bytes.EMPTY;
        this.name = name;
    }

    public enum Kind {
        METHOD,
        TYPE
    }

    public final Kind kind;
    public final int version;
    public final String signature;
    public final String decoderSignature;

    @NonNull
    public final ABIType<Tuple> decoder;

    @NonNull
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

    /** Returns the _signature_ of this ABI enum (intead of the standard `Enum.toString()` which returns
     * the actual spelling of the enum value.
     */
    @Override
    public String toString() {
        return signature;
    }

    /** Returns a string completely describing this ABI enum. */
    @NonNull
    public String toFullString() {
        return switch (kind) {
            case METHOD -> methodToString();
            case TYPE -> typeToString();
        };
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

    /** Safely convert from either a BigInteger or a Long to an Integer. */
    public static int toIntSafely(@NonNull final Object bigNumeric) {
        try {
            final var l = toLongSafely(bigNumeric);
            return Math.toIntExact(l);
        } catch (final ArithmeticException ex) {
            throw new IllegalArgumentException("%s out of range for Java.int".formatted(bigNumeric));
        }
    }

    // End of the public API - internal methods only after this

    static void validateNameIsConsistent(
            @NonNull final Kind kind, @NonNull final String keywordsCsv, @NonNull SystemContractAbis abi) {
        validateNameIsConsistent(kind, keywordsCsv, abi.name(), abi.name, abi.signature, abi.version);
    }

    @SuppressWarnings("java:S1301") // switch->if - I disagree: a switch on 2-elt enum is better than if
    static void validateNameIsConsistent(
            @NonNull final Kind kind,
            @NonNull final String keywordsCsv,
            @NonNull final String abiEnumName,
            @NonNull final String abiName,
            @NonNull final String abiSignature,
            final int abiVersion) {
        final var keywords =
                Arrays.stream(keywordsCsv.split(",")).map(s -> "_" + s + "_").toList();

        if (!signatureValidates(abiSignature))
            throw new IllegalArgumentException(
                    "ABI %s %s signature is not well-formed: '%s'".formatted(kind, abiEnumName, abiSignature));

        if (keywords.stream().noneMatch(abiEnumName::contains)) {
            final var keywordsOr = keywords.stream().map(s -> "'" + s + "'").collect(Collectors.joining(" or "));
            throw new IllegalArgumentException(
                    "ABI %s %s must have %s in its name".formatted(kind, abiEnumName, keywordsOr));
        }

        if (!versionMatches(abiEnumName, abiVersion))
            throw new IllegalArgumentException("ABS %s %s name must end in a digit which is its version: %d"
                    .formatted(kind, abiEnumName, abiVersion));

        switch (kind) {
            case METHOD -> {
                if (abiName.isEmpty())
                    throw new IllegalArgumentException("ABI %s %s must have a method name in its signature: '%s'"
                            .formatted(kind, abiEnumName, abiSignature));
            }
            case TYPE -> {
                if (!nameFrom(abiSignature).isEmpty())
                    throw new IllegalArgumentException("ABI %s %s must have no method name in its signature: '%s'"
                            .formatted(kind, abiEnumName, abiSignature));
            }
        }
    }

    static boolean versionMatches(@NonNull final String enumName, final int version) {
        final var lastCharOfName = enumName.charAt(enumName.length() - 1);
        final var versionFromName = Character.digit(lastCharOfName, 10);
        return versionFromName == version;
    }

    @SuppressWarnings({"java:S5852", "Java:S5843"}) // slow regular expression due to back-tracking could lead to DoS
    static boolean signatureValidates(@NonNull final String s) {

        // This regex matches ensures properly balanced and nested parenthesis, in Java (using
        // forward references, not recursion). The astonishing regex is from comment here -
        // https://stackoverflow.com/questions/47162098/is-it-possible-to-match-nested-brackets-with-a-regex-without-using-recursion-or/47162099#comment81280995_47163828
        // - which is a correction to an amazing answer here - https://stackoverflow.com/a/47162099/751579

        // (This regex backtracks which can lead to "polynomial time complexity" in general, and can be
        // a source of DoS attacks when used on an attacker's input.  But this one can't be used that
        // way because it is never ever called on user input, or even on variable input, or even in
        // production code: It's only called during a unit test.  And that only once per defined
        // enum, and those are all "well-behaved" and _expected_ to be balanced, thus, non-explosive.

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
        // spotless:on

        // Could imagine a better validation that would include checking for properly comma-separated
        // lists and that all types were known.  Disadvantage would be that then I couldn't use this
        // really clever regex...
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
        // `org.apache.tuweni.bytes.Bytes` has a bunch of "toHex"-ish routines - but not one that
        // can be asked to cough up a _fixed-width_ hex value.  So you've got to pad it yourself.
        // And the way it is done here is to glue 8 '0`s to the front of the hex digits and then
        // just take the last 8 characters of that.
        final var zeroExtendedHex = "00000000" + bytes.toUnprefixedHexString();
        return "0x" + zeroExtendedHex.substring(zeroExtendedHex.length() - 8);
    }

    @NonNull
    private String methodToString() {
        if (!isMethod()) throw new IllegalArgumentException("must be called with a METHOD");
        return "method %s (%d) %s[%d]: %s {%s}"
                .formatted(selectorAsHex(), selector.toInt(), name, version, signature, decoderSignature);
    }

    @NonNull
    private String typeToString() {
        if (!isType()) throw new IllegalArgumentException("must be called with a TYPE");
        return "struct %s[%d]: %s {%s}".formatted(name, version, signature, decoderSignature);
    }
}
