package com.hedera.evm.sigs.sourcing;

import com.hederahashgraph.api.proto.java.SignatureMap;

import static com.hedera.services.legacy.proto.utils.ByteStringUtils.unwrapUnsafelyIfPossible;

public class PojoSigMap {
    private static final int PUB_KEY_PREFIX_INDEX = 0;
    private static final int SIG_BYTES_INDEX = 1;
    private static final int DATA_PER_SIG_PAIR = 2;
    private final KeyType[] keyTypes;
    private final byte[][][] rawMap;

    private PojoSigMap(final byte[][][] rawMap, final KeyType[] keyTypes) {
        this.rawMap = rawMap;
        this.keyTypes = keyTypes;
    }

    public static PojoSigMap fromGrpc(final SignatureMap sigMap) {
        final var n = sigMap.getSigPairCount();
        final var rawMap = new byte[n][DATA_PER_SIG_PAIR][];
        final var keyTypes = new KeyType[n];
        for (var i = 0; i < n; i++) {
            final var sigPair = sigMap.getSigPair(i);
            rawMap[i][PUB_KEY_PREFIX_INDEX] = unwrapUnsafelyIfPossible(sigPair.getPubKeyPrefix());
            if (!sigPair.getECDSASecp256K1().isEmpty()) {
                rawMap[i][SIG_BYTES_INDEX] = unwrapUnsafelyIfPossible(sigPair.getECDSASecp256K1());
                keyTypes[i] = KeyType.ECDSA_SECP256K1;
            } else {
                rawMap[i][SIG_BYTES_INDEX] = unwrapUnsafelyIfPossible(sigPair.getEd25519());
                keyTypes[i] = KeyType.ED25519;
            }
        }
        return new PojoSigMap(rawMap, keyTypes);
    }

    public int numSigsPairs() {
        return rawMap.length;
    }
}
