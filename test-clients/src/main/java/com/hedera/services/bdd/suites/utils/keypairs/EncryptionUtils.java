package com.hedera.services.bdd.suites.utils.keypairs;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignaturePair;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EncryptionUtils {

	private static final Logger logger = LogManager.getLogger(EncryptionUtils.class);
	private static final int ITERATION_COUNT = 65536;
	private static final int KEY_LENGTH = 256;

	/**
	 * START Migration from client-tools project release 1.2 repo master branch
	 *
	 * @return
	 */
	public static char[] readPassphraseForNewKeyStore() {
		return readPassword("Passphrase for the new KeyStore: ", "Confirm passphrase for the new KeyStore: ");
	}

	public static char[] readPassphraseForNewAccounts() {
		return readPassword("Passphrase for new accounts KeyStores: ",
				"Confirm passphrase for new accounts KeyStores: ");
	}

	public static char[] readPassphraseForKeyStore() {
		return readPassword("Passphrase for KeyStores: ", "Confirm passphrase for KeyStores: ");
	}

	/**
	 * Ask the user to input password and confirm the password
	 *
	 * @param requestMessage
	 * @param confirmMessage
	 * @return
	 */
	private static char[] readPassword(final String requestMessage, final String confirmMessage) {
		try {
			return readPasswordAndConfirm(requestMessage, confirmMessage);
		} catch (final Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	/**
	 * Ask the user to input password once
	 *
	 * @param format
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static char[] readPassword(String format, Object... args) throws IOException {
		if (System.console() != null) {
			return System.console().readPassword(format, args);
		}
		final String password = Utils.readLine(format, args);
		logger.debug("\nPassphrase is " + password);
		return password.toCharArray();
	}

	public static char[] readPasswordAndConfirm(String format, String formatConfirm,
			Object... args) throws IOException, IllegalArgumentException {

		while (true) {
			final char[] password;
			final char[] confirmedPassword;
			if (System.console() != null) {
				password = System.console().readPassword(format, args);
				confirmedPassword = System.console().readPassword(formatConfirm, args);
			} else {
				BufferedReader reader = new BufferedReader(new InputStreamReader(
						System.in));
				password = reader.readLine().toCharArray();
				confirmedPassword = reader.readLine().toCharArray();
			}

			if (Arrays.areEqual(password, confirmedPassword)) {
				return password;
			}
			logger.warn("Passwords do NOT match");
		}
	}
	/**
	 * END of Migration
	 */

	/**
	 * Store public key into a file
	 *
	 * @param filename
	 * @param key
	 * @throws IOException
	 */
	public static void storePubKey(String filename, EdDSAPublicKey key) throws IOException {
		FileOutputStream fos = new FileOutputStream(filename);
		fos.write(Hex.encode(key.getAbyte()));
	}

	/**
	 * Wrap the given key into a Key which has a KeyList
	 *
	 * @param key
	 * @return
	 */
	public static Key wrapKeys(Key key) {
		return Key.newBuilder().setKeyList(
				KeyList.newBuilder().addAllKeys(Collections.singletonList(key)).build()).build();
	}

	/**
	 * Given a list of Keys_Test, return a Set which contains ByteStrings of all simple Keys_Test contained in the
	 * keyList
	 *
	 * @param keyList
	 * @return
	 */
	public static Set<ByteString> flatPubKeys(List<Key> keyList) {
		Set<ByteString> result = new HashSet<>();
		for (Key key : keyList) {
			flatPubKeys(key, result);
		}
		return result;
	}

	public static void flatPubKeys(Key key, Set<ByteString> result) {
		if (!key.hasKeyList() && !key.hasThresholdKey()) {
			result.add(key.getEd25519());
			return;
		}
		List<Key> keyList;
		if (key.hasThresholdKey()) {
			keyList = key.getThresholdKey().getKeys().getKeysList();
		} else {
			keyList = key.getKeyList().getKeysList();
		}
		for (Key ele : keyList) {
			flatPubKeys(ele, result);
		}
	}

	/**
	 * Return PubKeyPrefixSize for the public key with byteString.
	 * 1. Compare byteString with each byteString in others, get the number of bytes of common prefix between the two
	 * byte arrays;
	 * 2. If a byteString in others has the same content as the byteString, we ignore it;
	 * 3. Get the max number of bytes of common prefix between bytes and each byteString in others
	 * 4. If the max prefix size is equal to the length of the byteString, return it
	 * 5. If all elements in others has the same content as the byteString, return 0
	 * 6. Else return max prefix size + 1
	 *
	 * @param byteString
	 * @param others
	 * @return
	 */
	public static int calcPubKeyPrefixSize(ByteString byteString, Collection<ByteString> others) {
		int result = -1;
		byte[] bytes = byteString.toByteArray();
		for (ByteString otherBS : others) {
			if (otherBS.equals(byteString)) {
				continue;
			}
			byte[] other = otherBS.toByteArray();
			int commonPrefixSize = commonPrefixSize(bytes, other);
			result = Math.max(commonPrefixSize, result);
		}
		return result == bytes.length ? result : result + 1;
	}

	/**
	 * Return the number of bytes of common prefix between bytes and other
	 *
	 * @param bytes
	 * @param other
	 * @return
	 */
	public static int commonPrefixSize(byte[] bytes, byte[] other) {
		int i = 0;
		while (i < bytes.length && i < other.length) {
			if (bytes[i] != other[i]) {
				return i;
			}
			i++;
		}
		return i;
	}

	/**
	 * When a list of keyPair are used to sign a Transaction and generate a SigMap,
	 * we should remove duplicates first;
	 * Otherwise, there would be duplicate sigPairs in the SigMap, which would cause KEY_PREFIX_MISMATCH
	 *
	 * @param keyPairs
	 * @return
	 */
	public static List<KeyPair> removeDupKeyPair(List<KeyPair> keyPairs) {
		HashSet<ByteString> set = new HashSet<>();
		List<KeyPair> result = new ArrayList<>();
		for (KeyPair keyPair : keyPairs) {
			ByteString bs = ByteString.copyFrom(((EdDSAPublicKey) keyPair.getPublic()).getAbyte());
			if (set.add(bs)) {
				result.add(keyPair);
			}
		}
		return result;
	}

	/**
	 * When a list of sigPair are used to sign a Transaction and generate a SigMap,
	 * we should remove duplicates first;
	 * Otherwise it would cause KEY_PREFIX_MISMATCH
	 *
	 * @param sigPairs
	 * @return
	 */
	public static List<SignaturePair> removeDupSigPair(List<SignaturePair> sigPairs) {
		HashSet<ByteString> set = new HashSet<>();
		List<SignaturePair> result = new ArrayList<>();
		for (SignaturePair sigPair : sigPairs) {
			ByteString bs = sigPair.getEd25519();
			if (set.add(bs)) {
				result.add(sigPair);
			}
		}
		return result;
	}


	/**
	 * Build a KeyPair from Ed25519PrivateKey
	 *
	 * @param ed25519PrivateKey
	 * @return
	 */
	public static KeyPair buildKeyPairFromMainnetPrivateKey(final Ed25519PrivateKey ed25519PrivateKey) {
		return buildKeyPairFromMainnetPriKeyEncHex(ed25519PrivateKey.toString());
	}

	/**
	 * Build a KeyPair from a priKeyEncHex String generated by hedera key-gen tool
	 *
	 * @param priKeyEncHex
	 * @return
	 */
	private static KeyPair buildKeyPairFromMainnetPriKeyEncHex(final String priKeyEncHex) {
		byte[] privateKeyBytes = Hex.decode(priKeyEncHex);
		EdDSAPrivateKey privateKey;
		EdDSAPublicKey publicKey;
		try {
			// try encoded first
			final PKCS8EncodedKeySpec encodedPrivKey = new PKCS8EncodedKeySpec(privateKeyBytes);
			privateKey = new EdDSAPrivateKey(encodedPrivKey);
		} catch (InvalidKeySpecException e) {
			// key is invalid (likely not encoded)
			// try non encoded
			final EdDSAPrivateKeySpec privKey =
					new EdDSAPrivateKeySpec(privateKeyBytes, EdDSANamedCurveTable.ED_25519_CURVE_SPEC);
			privateKey = new EdDSAPrivateKey(privKey);
		}

		EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

		publicKey = new EdDSAPublicKey(
				new EdDSAPublicKeySpec(privateKey.getAbyte(), spec));
		return new KeyPair(publicKey, privateKey);
	}

	/***
	 * Converts a char array to a byte array with 32 elements
	 * @param password char array
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	public static byte[] keyFromPassword_(char[] password) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256", new BouncyCastleProvider());
		digest.update(password.toString().getBytes(StandardCharsets.UTF_8));
		return digest.digest();
	}

	public static byte[] keyFromPassword(char[] password) {

		byte[] salt = { 1, 2, 3, 4, 5, 6, 7, 8 };
		/* Derive the key, given password and salt. */
		try {
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

			KeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
			SecretKey tmp = factory.generateSecret(spec);
			SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
			return secret.getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * @param keyPair
	 * @return
	 */
	public static Key buildED25519Key(KeyPair keyPair) {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(((EdDSAPublicKey) keyPair
						.getPublic())
						.getAbyte()))
				.build();
	}

}
