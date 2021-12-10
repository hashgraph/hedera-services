package com.hedera.services.bdd.spec.keys;

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

		final var compressedPk = new byte[33];
		compressedPk[0] = (rawPkCoords[63] & 1) == 1 ? (byte) 0x03 : (byte) 0x02;
		System.arraycopy(rawPkCoords, 0, compressedPk, 1, 32);

		mutablePkMap.put(CommonUtils.hex(compressedPk), kp.getPrivate());
		return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(compressedPk)).build();
	}
}
