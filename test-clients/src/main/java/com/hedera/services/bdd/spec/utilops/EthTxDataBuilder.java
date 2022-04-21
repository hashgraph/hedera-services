package com.hedera.services.bdd.spec.utilops;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.sun.jna.ptr.IntByReference;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

public class EthTxDataBuilder {

    private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

    private byte[] rawTx;
    private EthTransactionType type;
    private byte[] chainId;
    private long nonce;
    private byte[] gasPrice;
    private byte[] maxPriorityGas;
    private byte[] maxGas;
    private long gasLimit;
    private byte[] to;
    private BigInteger value;
    private byte[] callData;
    private byte[] accessList;
    private int recId;
    private byte[] v;
    private byte[] r;
    private byte[] s;

    public static EthTxDataBuilder ethTxDataBuilder() {
        return new EthTxDataBuilder();
    }

    public EthTxDataBuilder forType(EthTransactionType type) {
        this.type = type;
        return this;
    }

    public EthTxDataBuilder withRawTx(byte[] rawTx) {
        this.rawTx = rawTx;
        return this;
    }

    public EthTxDataBuilder withChainId(byte[] chainId) {
        this.chainId = chainId;
        return this;
    }

    public EthTxDataBuilder withNonce(long nonce) {
        this.nonce = nonce;
        return this;
    }

    public EthTxDataBuilder withGasPrice(byte[] gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    public EthTxDataBuilder withMaxPriorityGas(byte[] maxPriorityGas) {
        this.maxPriorityGas = maxPriorityGas;
        return this;
    }

    public EthTxDataBuilder withMaxGas(byte[] maxGas) {
        this.maxGas = maxGas;
        return this;
    }

    public EthTxDataBuilder withGasLimit(long gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    public EthTxDataBuilder to(byte[] to) {
        this.to = to;
        return this;
    }

    public EthTxDataBuilder withValue(BigInteger value) {
        this.value = value;
        return this;
    }

    public EthTxDataBuilder withCallData(byte[] callData) {
        this.callData = callData;
        return this;
    }

    public EthTxDataBuilder withAccessList(byte[] accessList) {
        this.accessList = accessList;
        return this;
    }

    public EthTxDataBuilder withRecId(int recId) {
        this.recId = recId;
        return this;
    }

    public EthTxDataBuilder withSignature(byte[] v, byte[] r, byte[] s) {
        this.v = v;
        this.r = r;
        this.s = s;
        return this;
    }

    public byte[] build() {
        if (accessList != null && accessList.length > 0) {
            throw new IllegalStateException("Re-encoding access list is unsupported");
        }
        return switch (type) {
            case LEGACY_ETHEREUM -> RLPEncoder.encodeAsList(
                    Integers.toBytes(nonce), gasPrice, Integers.toBytes(gasLimit), to, Integers.toBytesUnsigned(value),
                    callData, v, r, s);
            case EIP2930 -> throw new IllegalStateException("EIP2930 txes not supported");
            case EIP1559 -> RLPEncoder.encodeSequentially(Integers.toBytes(0x02), List.of(
                    chainId, Integers.toBytes(nonce), maxPriorityGas, maxGas, Integers.toBytes(gasLimit), to,
                    Integers.toBytesUnsigned(value), callData, List.of(/*accessList*/), Integers.toBytes(recId), r, s
            ));
        };
    }

    public enum EthTransactionType {
        LEGACY_ETHEREUM,
        EIP2930,
        EIP1559,
    }
}
