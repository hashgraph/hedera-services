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

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * An ed25519 private key.
 *
 * <p>To obtain an instance, see {@link #generate()} or {@link #fromString(String)}.
 */
@SuppressWarnings("Duplicates")
public final class Ed25519PrivateKey {
	final Ed25519PrivateKeyParameters privKeyParams;

	// computed from private key and memoized
	@Nullable
	private Ed25519PublicKey publicKey;

	@Nullable
	private final KeyParameter chainCode;

	private Ed25519PrivateKey(Ed25519PrivateKeyParameters privateKeyParameters) {
		this.privKeyParams = privateKeyParameters;
		chainCode = null;
	}

	private Ed25519PrivateKey(
			Ed25519PrivateKeyParameters privateKeyParameters, @Nullable Ed25519PublicKeyParameters publicKeyParameters) {
		this.privKeyParams = privateKeyParameters;

		if (publicKeyParameters != null) {
			this.publicKey = new Ed25519PublicKey(publicKeyParameters);
		}
		chainCode = null;
	}

	private Ed25519PrivateKey(Ed25519PrivateKeyParameters privateKeyParameters, KeyParameter chainCode) {
		this.privKeyParams = privateKeyParameters;
		this.publicKey = new Ed25519PublicKey(privateKeyParameters.generatePublicKey());
		this.chainCode = chainCode;
	}

	public static Ed25519PrivateKey fromBytes(byte[] keyBytes) {
		Ed25519PrivateKeyParameters privKeyParams;
		Ed25519PublicKeyParameters pubKeyParams = null;

		if (keyBytes.length == Ed25519.SECRET_KEY_SIZE) {
			// if the decoded bytes matches the length of a private key, try that
			privKeyParams = new Ed25519PrivateKeyParameters(keyBytes, 0);
		} else if (keyBytes.length == Ed25519.SECRET_KEY_SIZE + Ed25519.PUBLIC_KEY_SIZE) {
			// some legacy code delivers private and public key pairs concatted together
			try {
				// this is how we read only the first 32 bytes
				privKeyParams = new Ed25519PrivateKeyParameters(
						new ByteArrayInputStream(keyBytes, 0, Ed25519.SECRET_KEY_SIZE));
				// read the remaining 32 bytes as the public key
				pubKeyParams = new Ed25519PublicKeyParameters(keyBytes, Ed25519.SECRET_KEY_SIZE);

				return new Ed25519PrivateKey(privKeyParams, pubKeyParams);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			// decode a properly DER-encoded private key descriptor
			var privateKeyInfo = PrivateKeyInfo.getInstance(keyBytes);

			try {
				var privateKey = privateKeyInfo.parsePrivateKey();
				privKeyParams = new Ed25519PrivateKeyParameters(((ASN1OctetString) privateKey).getOctets(), 0);

				var pubKeyData = privateKeyInfo.getPublicKeyData();

				if (pubKeyData != null) {
					pubKeyParams = new Ed25519PublicKeyParameters(pubKeyData.getOctets(), 0);
				}

			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return new Ed25519PrivateKey(privKeyParams, pubKeyParams);
	}

	/**
	 * Recover a private key from its text-encoded representation.
	 *
	 * @param privateKeyString
	 * 		the hex-encoded private key string
	 * @return the restored private key
	 * @throws org.bouncycastle.util.encoders.DecoderException
	 * 		if the hex string is invalid
	 * @throws RuntimeException
	 * 		if the decoded key was invalid
	 */
	public static Ed25519PrivateKey fromString(String privateKeyString) {
		var keyBytes = Hex.decode(privateKeyString);
		return fromBytes(keyBytes);
	}

	/** @return a new private key using {@link SecureRandom} */
	public static Ed25519PrivateKey generate() {
		return generate(new SecureRandom());
	}

	/** @return a new private key using the given {@link SecureRandom} */
	public static Ed25519PrivateKey generate(SecureRandom secureRandom) {
		return new Ed25519PrivateKey(new Ed25519PrivateKeyParameters(secureRandom));
	}

	/** @return the public key counterpart of this private key to share with the hashgraph */
	public Ed25519PublicKey getPublicKey() {
		if (publicKey == null) {
			publicKey = new Ed25519PublicKey(privKeyParams.generatePublicKey());
		}

		return publicKey;
	}

	/**
	 * Convert to a broader scoped private key instance given an encrypted ed25519 private key hex string.
	 * @param encryptPrivkeyAsHex
	 * @return
	 */
	public static PrivateKey convert(String encryptPrivkeyAsHex) {
		if (encryptPrivkeyAsHex == null && encryptPrivkeyAsHex.isEmpty()) {
			return null;
		}
		byte[] privateKeyBytes = Hex.decode(encryptPrivkeyAsHex);
		EdDSAPrivateKey privateKey;
		try {
			// try encoded first
			final PKCS8EncodedKeySpec encodedPrivKey = new PKCS8EncodedKeySpec(privateKeyBytes);
			privateKey = new EdDSAPrivateKey(encodedPrivKey);
		} catch (InvalidKeySpecException e) {
			// key is invalid (likely not encoded)
			// try non encoded
			final EdDSAPrivateKeySpec privKey = new EdDSAPrivateKeySpec(privateKeyBytes, EdDSANamedCurveTable.ED_25519_CURVE_SPEC);
			privateKey = new EdDSAPrivateKey(privKey);
		}
		return privateKey;
	}

	byte[] toBytes() { return privKeyParams.getEncoded(); }

	@Override
	public String toString() {
		PrivateKeyInfo privateKeyInfo;

		try {
			privateKeyInfo = new PrivateKeyInfo(
					new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
					new DEROctetString(privKeyParams.getEncoded()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		byte[] encoded;

		try {
			encoded = privateKeyInfo.getEncoded("DER");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return Hex.toHexString(encoded);
	}

	/**
	 * Recover a private key from a generated mnemonic phrase and a passphrase.
	 *
	 * This is not compatible with the phrases generated by the Android and iOS wallets;
	 * use the no-passphrase version instead.
	 *
	 * @param mnemonic   the mnemonic phrase which should be a 24 byte list of words.
	 * @param passphrase the passphrase used to protect the mnemonic (not used in the
	 *                   mobile wallets, use {@link #fromMnemonic(Mnemonic)} instead.)
	 * @return the recovered key; use {@link #derive(int)} to get a key for an account index (0
	 * for default account)
	 */
	public static Ed25519PrivateKey fromMnemonic(Mnemonic mnemonic, String passphrase) {
		final byte[] seed = mnemonic.toSeed(passphrase);

		final HMac hmacSha512 = new HMac(new SHA512Digest());
		hmacSha512.init(new KeyParameter("ed25519 seed".getBytes(StandardCharsets.UTF_8)));
		hmacSha512.update(seed, 0, seed.length);

		final byte[] derivedState = new byte[hmacSha512.getMacSize()];
		hmacSha512.doFinal(derivedState, 0);

		Ed25519PrivateKey derivedKey = derivableKey(derivedState);

		// BIP-44 path with the Hedera Hbar coin-type (omitting key index)
		// we pre-derive most of the path as the mobile wallets don't expose more than the index
		// https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
		// https://github.com/satoshilabs/slips/blob/master/slip-0044.md
		for (int index : new int[]{44, 3030, 0, 0}) {
			derivedKey = derivedKey.derive(index);
		}

		return derivedKey;
	}

	private static Ed25519PrivateKey derivableKey(byte[] deriveData) {
		final Ed25519PrivateKeyParameters privateKeyParameters = new Ed25519PrivateKeyParameters(deriveData, 0);
		final KeyParameter chainCode = new KeyParameter(deriveData, 32, 32);
		return new Ed25519PrivateKey(privateKeyParameters, chainCode);
	}

	/**
	 * Recover a private key from a mnemonic phrase compatible with the iOS and Android wallets.
	 * <p>
	 * An overload of {@link #fromMnemonic(Mnemonic, String)} which uses an empty string for the
	 * passphrase.
	 *
	 * @param mnemonic the mnemonic phrase which should be a 24 byte list of words.
	 * @return the recovered key; use {@link #derive(int)} to get a key for an account index (0
	 * for default account)
	 */
	public static Ed25519PrivateKey fromMnemonic(Mnemonic mnemonic) {
		return fromMnemonic(mnemonic, "");
	}

	/**
	 * Recover a private key from a mnemonic phrase compatible with the iOS and Android wallets.
	 * <p>
	 * An overload of {@link #fromMnemonic(Mnemonic, String)} which uses an empty string for the
	 * passphrase.
	 *
	 * @param mnemonic	the mnemonic phrase which should be a 24 byte list of words.
	 * @param index		the index of the requested key. index = 0 should return the wallet key
	 * @return			the recovered key;
	 */
	public static Ed25519PrivateKey fromMnemonic(Mnemonic mnemonic, int index){
		return fromMnemonic(mnemonic, "").derive(index);
	}

	/**
	 * Check if this private key supports derivation.
	 * <p>
	 * This is currently only the case if this private key was created from a mnemonic.
	 */
	public boolean supportsDerivation() {
		return this.chainCode != null;
	}

	/**
	 * Given a wallet/account index, derive a child key compatible with the iOS and Android wallets.
	 * <p>
	 * Use index 0 for the default account.
	 *
	 * @param index the wallet/account index of the account, 0 for the default account.
	 * @return the derived key
	 * @throws IllegalStateException if this key does not support derivation.
	 * @see #supportsDerivation()
	 */
	public Ed25519PrivateKey derive(int index) {
		if (this.chainCode == null) {
			throw new IllegalStateException("this private key does not support derivation");
		}

		// SLIP-10 child key derivation
		// https://github.com/satoshilabs/slips/blob/master/slip-0010.md#master-key-generation
		final HMac hmacSha512 = new HMac(new SHA512Digest());

		hmacSha512.init(chainCode);
		hmacSha512.update((byte) 0);

		hmacSha512.update(privKeyParams.getEncoded(), 0, Ed25519.SECRET_KEY_SIZE);

		// write the index in big-endian order, setting the 31st bit to mark it "hardened"
		final byte[] indexBytes = new byte[4];
		ByteBuffer.wrap(indexBytes).order(ByteOrder.BIG_ENDIAN).putInt(index);
		indexBytes[0] |= (byte) 0b10000000;

		hmacSha512.update(indexBytes, 0, indexBytes.length);

		byte[] output = new byte[64];
		hmacSha512.doFinal(output, 0);

		final Ed25519PrivateKeyParameters childKeyParams = new Ed25519PrivateKeyParameters(output, 0);
		final KeyParameter childChainCode = new KeyParameter(output, 32, 32);

		return new Ed25519PrivateKey(childKeyParams, childChainCode);
	}
}
