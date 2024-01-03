/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
 * enum as the single source of information about these ABIs
 * N.B.: Currently this file needs to be maintained by hand when ABIs are added or versioned; it is
 * intended that this file be GENERATED from the actual Solidity contracts (in the smart contracts
 * repository)
 * N.B.: Need to add return types, and argument names (which could be used for various purposes)
 */
@SuppressWarnings("java:S1192") // duplicate string literals - in this case, removing duplication makes code
//                       more difficult to read
public enum SystemContractAbis {

    // HTS, versioned ABIs only

    BURN_TOKEN_V1(1, "burnToken(address,uint64,int64[])"),
    BURN_TOKEN_V2(2, "burnToken(address,int64,int64[])"),

    CREATE_FUNGIBLE_TOKEN_V1(1, "createFungibleToken(%s,uint256,uint256)".formatted(HEDERA_TOKEN_STRUCT_V1)),
    CREATE_FUNGIBLE_TOKEN_V2(2, "createFungibleToken(%s,uint64,uint32)".formatted(HEDERA_TOKEN_STRUCT_V2)),
    CREATE_FUNGIBLE_TOKEN_V3(3, "createFungibleToken(%s,int64,int32)".formatted(HEDERA_TOKEN_STRUCT_V3)),

    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1(
            1,
            "createFungibleTokenWithCustomFees(%s,uint256,uint256,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V1, FIXED_FEE_V1, FRACTIONAL_FEE_V1)),
    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2(
            2,
            "createFungibleTokenWithCustomFees(%s,uint64,uint32,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V2, FIXED_FEE_V1, FRACTIONAL_FEE_V1)),
    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3(
            3,
            "createFungibleTokenWithCustomFees(%s,int64,int32,%s[],%s[])"
                    .formatted(HEDERA_TOKEN_STRUCT_V3, FIXED_FEE_V2, FRACTIONAL_FEE_V2)),

    CREATE_NON_FUNGIBLE_TOKEN_V1(1, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V1)),
    CREATE_NON_FUNGIBLE_TOKEN_V2(2, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V2)),
    CREATE_NON_FUNGIBLE_TOKEN_V3(3, "createNonFungibleToken(%s)".formatted(HEDERA_TOKEN_STRUCT_V3)),

    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1("createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
            .formatted(HEDERA_TOKEN_STRUCT_V1, FIXED_FEE_V1, ROYALTY_FEE_V1)),
    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2("createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
            .formatted(HEDERA_TOKEN_STRUCT_V2, FIXED_FEE_V1, ROYALTY_FEE_V1)),
    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3("createNonFungibleTokenWithCustomFees(%s,%s[],%s[])"
            .formatted(HEDERA_TOKEN_STRUCT_V3, FIXED_FEE_V2, ROYALTY_FEE_V2)),

    CRYPTO_TRANSFER_V1(1, "cryptoTransfer(%s[])".formatted(TOKEN_TRANSFER_LIST_V1)),
    CRYPTO_TRANSFER_V2(2, "cryptoTransfer(%s,%s[])".formatted(TRANSFER_LIST_V1, TOKEN_TRANSFER_LIST_V2)),

    MINT_TOKEN_V1(1, "mintToken(address,uint64,bytes[])"),
    MINT_TOKEN_V2(2, "mintToken(address,int64,bytes[])"),

    UPDATE_TOKEN_EXPIRY_INFO_V1(1, "updateTokenExpiryInfo(address,%s)".formatted(EXPIRY_V1)),
    UPDATE_TOKEN_EXPIRY_INFO_V2(2, "updateTokenExpiryInfo(address,%s)".formatted(EXPIRY_V2)),

    UPDATE_TOKEN_INFO_V1(1, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V1)),
    UPDATE_TOKEN_INFO_V2(2, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V2)),
    UPDATE_TOKEN_INFO_V3(3, "updateTokenInfo(address,%s)".formatted(HEDERA_TOKEN_STRUCT_V3)),

    WIPE_TOKEN_ACCOUNT_V1(1, "wipeTokenAccount(address,address,uint32)"),
    WIPE_TOKEN_ACCOUNT_V2(2, "wipeTokenAccount(address,address,int64)");

    SystemContractAbis(int version, @NonNull final String signature) {
        this.version = version;
        this.signature = stripBlanks(signature);
        this.decoderSignature = stripName(addressToUint(this.signature));
        this.decoder = TypeFactory.create(this.decoderSignature);
        this.selector = Bytes.wrap(new Function(this.signature).selector());
        this.methodName = nameFrom(this.signature);
    }

    SystemContractAbis(@NonNull final String signature) {
        this(1, signature);
    }

    public final int version;
    public final String signature;
    public final String decoderSignature;
    public final ABIType<Tuple> decoder;
    public final Bytes selector;
    public final String methodName;

    @NonNull
    public String selectorAsHex() {
        return to8CharHexString(selector);
    }

    @NonNull
    String nameFrom(@NonNull final String s) {
        // signature must look like `fooMethod(arg1,arg2,arg3)` so this just pulls out that
        // part before the first `(`
        return s.substring(0, s.indexOf('('));
    }

    @NonNull
    String addressToUint(@NonNull final String s) {
        // for some reason the decoder library doesn't accept the Solidity type `address` and
        // requires the equivalent `bytes32` instead
        return s.replace("address", "bytes32");
    }

    @NonNull
    String stripBlanks(@NonNull final String s) {
        return s.replaceAll("\\s+", "");
    }

    @NonNull
    String stripName(@NonNull final String s) {
        // signature must look like `fooMethod(arg1,arg2,arg3)` so this just trims off
        // the `fooMethod` at the beginning
        return s.substring(s.indexOf('('));
    }

    @NonNull
    String to8CharHexString(@NonNull final Bytes bytes) {
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
            if (big.compareTo(LONG_MIN) < 0 || big.compareTo(LONG_MAX) > 0)
                throw new IllegalArgumentException("uint64 out of range for Java.long");
            return big.longValue();
        } else if (bigNumeric instanceof Long big) {
            return big;
        } else return (long) bigNumeric;
    }

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
}
