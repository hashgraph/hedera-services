package com.hedera.services.sigs.sourcing;

import com.hederahashgraph.api.proto.java.SignatureMap;

public class PojoSigMap {
	private final byte[][][] rawMap;

	private PojoSigMap(byte[][][] rawMap) {
		this.rawMap = rawMap;
	}

	public static PojoSigMap fromGrpc(SignatureMap sigMap) {
		final var n = sigMap.getSigPairCount();
		final var rawMap = new byte[n][2][];
		for (var i = 0; i < n; i++) {
			final var sigPair = sigMap.getSigPair(i);
			rawMap[i][0] = sigPair.getPubKeyPrefix().toByteArray();
			rawMap[i][1] = sigPair.getEd25519().toByteArray();
		}
		return new PojoSigMap(rawMap);
	}

	public byte[] pubKeyPrefix(int i) {
		return rawMap[i][0];
	}

	public byte[] ed25519Signature(int i) {
		return rawMap[i][1];
	}

	public int numSigsPairs() {
		return rawMap.length;
	}
}
