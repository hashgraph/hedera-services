package com.hedera.services.bdd.spec.keys;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.util.KeyExpansion;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.CommonUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Map;

public enum DefaultKeyGen implements KeyGenerator {
	DEFAULT_KEY_GEN;

	private static final ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
	private static final KeyPairGenerator ecKpGenerator;

	static {
		try {
			Security.insertProviderAt(new BouncyCastleProvider(), 1);
			ecKpGenerator = KeyPairGenerator.getInstance("EC");
			ecKpGenerator.initialize(ecSpec, new SecureRandom());
		} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException fatal) {
			throw new IllegalStateException(fatal);
		}
	}


	@Override
	public Key genEd25519AndUpdateMap(Map<String, PrivateKey> mutablePkMap) {
		return KeyExpansion.genSingleEd25519Key(mutablePkMap);
	}

	@Override
	public Key genEcdsaSecp256k1AndUpdate(Map<String, PrivateKey> mutablePkMap) {
		final var kp = ecKpGenerator.generateKeyPair();
		final var encodedPk = kp.getPublic().getEncoded();
		final var rawPkCoords = Arrays.copyOfRange(encodedPk, encodedPk.length - 64, encodedPk.length);

		final var uncompressedPk = new byte[65];
		uncompressedPk[0] = (byte) 0x04;
		System.arraycopy(rawPkCoords, 0, uncompressedPk, 1, 64);

		mutablePkMap.put(CommonUtils.hex(uncompressedPk), kp.getPrivate());
		return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(uncompressedPk)).build();
	}
}
