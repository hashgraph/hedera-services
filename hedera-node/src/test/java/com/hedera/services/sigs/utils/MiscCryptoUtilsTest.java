package com.hedera.services.sigs.utils;

import com.hedera.test.factories.keys.KeyFactory;
import com.swirlds.common.CommonUtils;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MiscCryptoUtilsTest {
	@Test
	void computesExpectedHashes() {
		final var data = "AHOTMESS".getBytes();

		final var expectedHexedHash = "9eed2a3d8a3987c15d6ec326012c8a3b91346341921a09cc75eb38df28101e8d";

		final var actualHexedHash = CommonUtils.hex(MiscCryptoUtils.keccak256DigestOf(data));

		assertEquals(expectedHexedHash, actualHexedHash);
	}

	@Test
	void recoversUncompressedSecp256k1PubKey() {
		final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
		final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
		final var compressed = q.getEncoded(true);
		final var uncompressed = q.getEncoded(false);

		assertArrayEquals(
				Arrays.copyOfRange(uncompressed, 1, 65),
				MiscCryptoUtils.decompressSecp256k1(compressed));
	}
}