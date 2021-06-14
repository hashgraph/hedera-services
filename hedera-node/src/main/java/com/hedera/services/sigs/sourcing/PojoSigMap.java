package com.hedera.services.sigs.sourcing;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

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
