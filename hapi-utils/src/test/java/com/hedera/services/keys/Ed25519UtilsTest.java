package com.hedera.services.keys;

import com.swirlds.common.CommonUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.KeyPair;

import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ed25519UtilsTest {
	private static final String devGenesisPemLoc = "src/test/resources/vectors/genesis.pem";
	private static final String devGenesisPassphrase = "swirlds";

	// The well-known Ed25519 key used to bootstrap a development network; has ZERO sensitivity or security impact
	final String devPrivateKey = "91132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";
	final String devPublicKey = "0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

	@Test
	void throwsIllegalArgumentOnMissingTargetLoc() {
		final var tmpLoc = "unreachable/missing/re-encrypted_genesis.pem";
		final var tmpPassphrase = "hedera";

		assertThrows(IllegalArgumentException.class, () ->
				Ed25519Utils.writeKeyTo(CommonUtils.unhex(devPrivateKey), tmpLoc, tmpPassphrase));
	}

	@Test
	void writesRecoverableDevGenesisKey() {
		final var tmpLoc = "re-encrypted_genesis.pem";
		final var tmpPassphrase = "hedera";

		Ed25519Utils.writeKeyTo(CommonUtils.unhex(devPrivateKey), tmpLoc, tmpPassphrase);
		final var recovered = Ed25519Utils.readKeyFrom(tmpLoc, tmpPassphrase);
		assertIsGenesisDevKey(recovered);

		new File(tmpLoc).delete();
	}

	@Test
	void recoversDevGenesisKeyPair() {
		final var recovered = Ed25519Utils.readKeyPairFrom(new File(devGenesisPemLoc), devGenesisPassphrase);

		assertIsGenesisDevKeyPair(recovered);
	}

	@Test
	void recoversDevGenesisKey() {
		final var recovered = Ed25519Utils.readKeyFrom(devGenesisPemLoc, devGenesisPassphrase);

		assertIsGenesisDevKey(recovered);
	}

	@Test
	void failsOnMissingPem() {
		final var notDevGenesisPemLoc = "src/test/resources/vectors/missing.pem";
		assertThrows(IllegalArgumentException.class, () ->
				Ed25519Utils.readKeyFrom(notDevGenesisPemLoc, devGenesisPassphrase));
	}

	@Test
	void failsOnBadPassphrase() {
		final var notDevGenesisPassphrase = "sdlriws";
		assertThrows(IllegalArgumentException.class, () ->
				Ed25519Utils.readKeyFrom(devGenesisPemLoc, notDevGenesisPassphrase));
	}

	private void assertIsGenesisDevKey(final EdDSAPrivateKey key) {
		assertEquals(devPublicKey, hex(key.getAbyte()));
		assertEquals(devPrivateKey, hex(key.getSeed()));
	}

	private void assertIsGenesisDevKeyPair(final KeyPair keyPair) {
		assertTrue(hex(keyPair.getPublic().getEncoded()).endsWith(devPublicKey));
		assertTrue(hex(keyPair.getPrivate().getEncoded()).endsWith(devPrivateKey));
	}
}