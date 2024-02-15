/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.util.Integers;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.Keccak;

public record EthTxData(
        byte[] rawTx,
        EthTransactionType type,
        byte[] chainId,
        long nonce,
        byte[] gasPrice,
        byte[] maxPriorityGas,
        byte[] maxGas,
        long gasLimit,
        byte[] to,
        BigInteger value,
        byte[] callData,
        byte[] accessList,
        int recId,
        byte[] v,
        byte[] r,
        byte[] s) {

    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

    // Copy of constants from besu-native, remove when next besu-native publishes
    static final int SECP256K1_FLAGS_TYPE_COMPRESSION = 1 << 1;
    static final int SECP256K1_FLAGS_BIT_COMPRESSION = 1 << 8;
    static final int SECP256K1_EC_COMPRESSED = (SECP256K1_FLAGS_TYPE_COMPRESSION | SECP256K1_FLAGS_BIT_COMPRESSION);

    // EIP155 note support for v = 27|28 cases in unprotected transaction cases
    static final BigInteger LEGACY_V_BYTE_SIGNATURE_0 = BigInteger.valueOf(27);
    static final BigInteger LEGACY_V_BYTE_SIGNATURE_1 = BigInteger.valueOf(28);

    public static EthTxData populateEthTxData(byte[] data) {
        try {
            var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(data);
            var rlpItem = decoder.next();
            if (rlpItem.isList()) {
                return populateLegacyEthTxData(rlpItem, data);
            }

            return switch (rlpItem.asByte()) {
                case 1 -> populateEip2390EthTxData(decoder.next(), data);
                case 2 -> populateEip1559EthTxData(decoder.next(), data);
                default -> null;
            };

        } catch (IllegalArgumentException | NoSuchElementException e) {
            return null;
        }
    }

    public EthTxData replaceCallData(byte[] callData) {
        return new EthTxData(
                null,
                type,
                chainId,
                nonce,
                gasPrice,
                maxPriorityGas,
                maxGas,
                gasLimit,
                to,
                value,
                callData,
                accessList,
                recId,
                v,
                r,
                s);
    }

    @VisibleForTesting
    EthTxData replaceOfferedGasPrice(@NonNull final BigInteger replacementGasPrice) {
        return new EthTxData(
                null,
                type,
                chainId,
                nonce,
                replacementGasPrice.toByteArray(),
                maxPriorityGas,
                maxGas,
                gasLimit,
                to,
                value,
                callData,
                accessList,
                recId,
                v,
                r,
                s);
    }

    @VisibleForTesting
    EthTxData replaceValue(@NonNull final BigInteger replacementValue) {
        return new EthTxData(
                null,
                type,
                chainId,
                nonce,
                gasPrice,
                maxPriorityGas,
                maxGas,
                gasLimit,
                to,
                replacementValue,
                callData,
                accessList,
                recId,
                v,
                r,
                s);
    }

    public byte[] encodeTx() {
        if (accessList != null && accessList.length > 0) {
            throw new IllegalStateException("Re-encoding access list is unsupported");
        }
        return switch (type) {
            case LEGACY_ETHEREUM -> RLPEncoder.encodeAsList(
                    Integers.toBytes(nonce),
                    gasPrice,
                    Integers.toBytes(gasLimit),
                    to,
                    Integers.toBytesUnsigned(value),
                    callData,
                    v,
                    r,
                    s);
            case EIP2930 -> RLPEncoder.encodeSequentially(
                    Integers.toBytes(0x01),
                    List.of(
                            chainId,
                            Integers.toBytes(nonce),
                            gasPrice,
                            Integers.toBytes(gasLimit),
                            to,
                            Integers.toBytesUnsigned(value),
                            callData,
                            List.of(/*accessList*/ ),
                            Integers.toBytes(recId),
                            r,
                            s));
            case EIP1559 -> RLPEncoder.encodeSequentially(
                    Integers.toBytes(0x02),
                    List.of(
                            chainId,
                            Integers.toBytes(nonce),
                            maxPriorityGas,
                            maxGas,
                            Integers.toBytes(gasLimit),
                            to,
                            Integers.toBytesUnsigned(value),
                            callData,
                            List.of(/*accessList*/ ),
                            Integers.toBytes(recId),
                            r,
                            s));
        };
    }

    public long getAmount() {
        return value.divide(WEIBARS_TO_TINYBARS).longValueExact();
    }

    public BigInteger getMaxGasAsBigInteger() {
        return switch (type) {
            case LEGACY_ETHEREUM, EIP2930 -> new BigInteger(1, gasPrice);
            case EIP1559 -> new BigInteger(1, maxGas);
        };
    }

    /**
     * Returns the effective offered gas price for this transaction, defined as the minimum of the
     * nominal offered gas price in tinybars and {@code (Long.MAX_VALUE / gasLimit)}.
     *
     * <p>Clearly the latter value would always be un-payable, since the transaction would cost more
     * than the entire hbar supply. We just do this to avoid integral overflow.
     *
     * @return the effective offered gas price
     */
    public long effectiveOfferedGasPriceInTinybars() {
        return BigInteger.valueOf(Long.MAX_VALUE)
                .min(getMaxGasAsBigInteger().divide(WEIBARS_TO_TINYBARS))
                .longValueExact();
    }

    /**
     * Returns the effective tinybar value of this transaction, defined as the minimum of the nominal
     * value in tinybars and {@code Long.MAX_VALUE}.
     *
     * <p>Clearly the latter value would always be un-payable, since the transaction would send more
     * than the entire hbar supply. We just do this to avoid integral overflow.
     *
     * @return the effective tinybar value
     */
    public long effectiveTinybarValue() {
        return BigInteger.valueOf(Long.MAX_VALUE)
                .min(value.divide(WEIBARS_TO_TINYBARS))
                .longValueExact();
    }

    public byte[] getEthereumHash() {
        return new Keccak.Digest256().digest(rawTx == null ? encodeTx() : rawTx);
    }

    public enum EthTransactionType {
        LEGACY_ETHEREUM,
        EIP2930,
        EIP1559,
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final EthTxData ethTxData = (EthTxData) o;

        return (nonce == ethTxData.nonce)
                && (gasLimit == ethTxData.gasLimit)
                && (recId == ethTxData.recId)
                && (Arrays.equals(rawTx, ethTxData.rawTx))
                && (type == ethTxData.type)
                && (Arrays.equals(chainId, ethTxData.chainId))
                && (Arrays.equals(gasPrice, ethTxData.gasPrice))
                && (Arrays.equals(maxPriorityGas, ethTxData.maxPriorityGas))
                && (Arrays.equals(maxGas, ethTxData.maxGas))
                && (Arrays.equals(to, ethTxData.to))
                && (Objects.equals(value, ethTxData.value))
                && (Arrays.equals(callData, ethTxData.callData))
                && (Arrays.equals(accessList, ethTxData.accessList))
                && (Arrays.equals(v, ethTxData.v))
                && (Arrays.equals(r, ethTxData.r))
                && (Arrays.equals(s, ethTxData.s));
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(rawTx);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(chainId);
        result = 31 * result + (int) (nonce ^ (nonce >>> 32));
        result = 31 * result + Arrays.hashCode(gasPrice);
        result = 31 * result + Arrays.hashCode(maxPriorityGas);
        result = 31 * result + Arrays.hashCode(maxGas);
        result = 31 * result + (int) (gasLimit ^ (gasLimit >>> 32));
        result = 31 * result + Arrays.hashCode(to);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(callData);
        result = 31 * result + Arrays.hashCode(accessList);
        result = 31 * result + recId;
        result = 31 * result + Arrays.hashCode(v);
        result = 31 * result + Arrays.hashCode(r);
        result = 31 * result + Arrays.hashCode(s);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rawTx", rawTx == null ? null : Hex.encodeHexString(rawTx))
                .add("type", type)
                .add("chainId", chainId == null ? null : Hex.encodeHexString(chainId))
                .add("nonce", nonce)
                .add("gasPrice", gasPrice == null ? null : Hex.encodeHexString(gasPrice))
                .add("maxPriorityGas", maxPriorityGas == null ? null : Hex.encodeHexString(maxPriorityGas))
                .add("maxGas", maxGas == null ? null : Hex.encodeHexString(maxGas))
                .add("gasLimit", gasLimit)
                .add("to", to == null ? null : Hex.encodeHexString(to))
                .add("value", value)
                .add("callData", Hex.encodeHexString(callData))
                .add("accessList", accessList == null ? null : Hex.encodeHexString(accessList))
                .add("recId", recId)
                .add("v", v == null ? null : Hex.encodeHexString(v))
                .add("r", Hex.encodeHexString(r))
                .add("s", Hex.encodeHexString(s))
                .toString();
    }

    public boolean hasCallData() {
        return callData != null && callData.length > 0;
    }

    public boolean hasToAddress() {
        return to != null && to.length > 0;
    }

    public boolean matchesChainId(final byte[] hederaChainId) {
        // first two checks handle the unprotected ethereum transactions
        // before EIP155 - source: https://eips.ethereum.org/EIPS/eip-155
        return chainId == null || chainId.length == 0 || Arrays.compare(chainId, hederaChainId) == 0;
    }

    @VisibleForTesting
    public EthTxData replaceTo(byte[] to) {
        return new EthTxData(
                null,
                type,
                chainId,
                nonce,
                gasPrice,
                maxPriorityGas,
                maxGas,
                gasLimit,
                to,
                value,
                callData,
                accessList,
                recId,
                v,
                r,
                s);
    }

    /**
     * Encodes the transaction data into a EthTxData according to legacy RLP format.
     *
     * @return the encoded transaction data
     */
    private static EthTxData populateLegacyEthTxData(RLPItem rlpItem, byte[] rawTx) {
        List<RLPItem> rlpList = rlpItem.asRLPList().elements();
        if (rlpList.size() != 9) {
            return null;
        }

        byte[] chainId = null;
        byte[] v = rlpList.get(6).asBytes();
        BigInteger vBI = new BigInteger(1, v);
        byte recId = vBI.testBit(0) ? (byte) 0 : 1;
        // https://eips.ethereum.org/EIPS/eip-155
        if (vBI.compareTo(BigInteger.valueOf(34)) > 0) {
            // after EIP155 the chain id is equal to
            // CHAIN_ID = (v - {0,1} - 35) / 2
            chainId = vBI.subtract(BigInteger.valueOf(35)).shiftRight(1).toByteArray();
        } else if (isLegacyUnprotectedEtx(vBI)) {
            // before EIP155 the chain id is considered equal to 0
            chainId = new byte[0];
        }

        return new EthTxData(
                rawTx,
                EthTransactionType.LEGACY_ETHEREUM,
                chainId,
                rlpList.get(0).asLong(), // nonce
                rlpList.get(1).asBytes(), // gasPrice
                null, // maxPriorityGas
                null, // maxGas
                rlpList.get(2).asLong(), // gasLimit
                rlpList.get(3).data(), // to
                rlpList.get(4).asBigInt(), // value
                rlpList.get(5).data(), // callData
                null, // accessList
                recId,
                v,
                rlpList.get(7).data(), // r
                rlpList.get(8).data() // s
                );
    }

    /**
     * Encodes the transaction data into a EthTxData according to EIP 1559 RLP format.
     *
     * @return the encoded transaction data
     */
    private static EthTxData populateEip1559EthTxData(RLPItem rlpItem, byte[] rawTx) {
        if (!rlpItem.isList()) {
            return null;
        }

        List<RLPItem> rlpList = rlpItem.asRLPList().elements();
        if (rlpList.size() != 12) {
            return null;
        }

        return new EthTxData(
                rawTx,
                EthTransactionType.EIP1559,
                rlpList.get(0).data(), // chainId
                rlpList.get(1).asLong(), // nonce
                null, // gasPrice
                rlpList.get(2).data(), // maxPriorityGas
                rlpList.get(3).data(), // maxGas
                rlpList.get(4).asLong(), // gasLimit
                rlpList.get(5).data(), // to
                rlpList.get(6).asBigInt(), // value
                rlpList.get(7).data(), // callData
                rlpList.get(8).data(), // accessList
                rlpList.get(9).asByte(), // recId
                null, // v
                rlpList.get(10).data(), // r
                rlpList.get(11).data() // s
                );
    }

    /**
     * Encodes the transaction data into a EthTxData according to EIP 2930 RLP format.
     *
     * @return the encoded transaction data
     */
    private static EthTxData populateEip2390EthTxData(RLPItem rlpItem, byte[] rawTx) {
        if (!rlpItem.isList()) {
            return null;
        }

        List<RLPItem> rlpList = rlpItem.asRLPList().elements();
        if (rlpList.size() != 11) {
            return null;
        }

        return new EthTxData(
                rawTx,
                EthTransactionType.EIP2930,
                rlpList.get(0).data(), // chainId
                rlpList.get(1).asLong(), // nonce
                rlpList.get(2).data(), // gasPrice
                null, // maxPriorityGas
                null, // maxGas
                rlpList.get(3).asLong(), // gasLimit
                rlpList.get(4).data(), // to
                rlpList.get(5).asBigInt(), // value
                rlpList.get(6).data(), // callData
                rlpList.get(7).data(), // accessList
                rlpList.get(8).asByte(), // recId
                null, // v
                rlpList.get(9).data(), // r
                rlpList.get(10).data() // s
                );
    }

    // before EIP155 the value of v in
    // (unprotected) ethereum transactions is either 27 or 28
    private static boolean isLegacyUnprotectedEtx(@NonNull BigInteger vBI) {
        return vBI.compareTo(LEGACY_V_BYTE_SIGNATURE_0) == 0 || vBI.compareTo(LEGACY_V_BYTE_SIGNATURE_1) == 0;
    }
}
