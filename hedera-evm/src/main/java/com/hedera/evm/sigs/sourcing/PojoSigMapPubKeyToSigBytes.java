package com.hedera.evm.sigs.sourcing;

import com.hederahashgraph.api.proto.java.SignatureMap;

public class PojoSigMapPubKeyToSigBytes implements PubKeyToSigBytes {
    private final PojoSigMap pojoSigMap;

    private final boolean[] used;

    public PojoSigMapPubKeyToSigBytes(SignatureMap sigMap) {
        pojoSigMap = PojoSigMap.fromGrpc(sigMap);
        used = new boolean[pojoSigMap.numSigsPairs()];
    }
}
