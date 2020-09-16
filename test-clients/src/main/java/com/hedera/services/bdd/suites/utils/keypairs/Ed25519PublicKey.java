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
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.security.PublicKey;

/**
 * An ed25519 public key.
 *
 * <p>Can be constructed from a byte array or obtained from a private key {@link
 * Ed25519PrivateKey#getPublicKey()}.
 */
@SuppressWarnings("Duplicates") // difficult to factor out common code for all algos without exposing it
public final class Ed25519PublicKey {
	private final Ed25519PublicKeyParameters pubKeyParams;

	Ed25519PublicKey(Ed25519PublicKeyParameters pubKeyParams) {
		this.pubKeyParams = pubKeyParams;
	}

	/**
	 * Construct a known public key from a byte array.
	 *
	 * @throws AssertionError
	 * 		if {@code bytes.length != 32}
	 */
	public static Ed25519PublicKey fromBytes(byte[] bytes) {
		assert bytes.length == Ed25519.PUBLIC_KEY_SIZE;
		return new Ed25519PublicKey(new Ed25519PublicKeyParameters(bytes, 0));
	}

	/**
	 * Recover a public key from its text-encoded representation.
	 *
	 * @param publicKeyString
	 * 		the hex-encoded private key string
	 * @return the restored public key
	 * @throws org.bouncycastle.util.encoders.DecoderException
	 * 		if the hex string is invalid
	 * @throws AssertionError
	 * 		if the hex string decodes to the wrong number of bytes
	 */
	public static Ed25519PublicKey fromString(String publicKeyString) {
		var keyBytes = Hex.decode(publicKeyString);

		// if the decoded bytes matches the length of a public key, try that
		if (keyBytes.length == Ed25519.PUBLIC_KEY_SIZE) {
			return fromBytes(keyBytes);
		}

		var publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyBytes);
		return fromBytes(
				publicKeyInfo.getPublicKeyData()
						.getBytes());
	}

	/**
	 * Convert a Ed25519PublicKey string into a java type <code>PublicKey</code>
	 *
	 * @param ed25529KeyBytes
	 * @return
	 */
	public static PublicKey convert(final byte[] ed25529KeyBytes) {
		if (ed25529KeyBytes == null || ed25529KeyBytes.length < 1) {
			throw new IllegalArgumentException("Ed25519 byte array is empty.");
		}
		EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(ed25529KeyBytes,
				EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519));
		return new EdDSAPublicKey(pubKeySpec);
	}

	public byte[] toBytes() {
		return pubKeyParams.getEncoded();
	}

	@Override
	public String toString() {
		SubjectPublicKeyInfo publicKeyInfo;

		try {
			publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubKeyParams);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// I'd love to dedup this with the code in `Ed25519PrivateKey.toString()`
		// but there's no way to do that without creating an entirely public class
		byte[] encoded;

		try {
			encoded = publicKeyInfo.getEncoded("DER");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return Hex.toHexString(encoded);
	}

	public com.hederahashgraph.api.proto.java.Key toKeyProto() {
		return com.hederahashgraph.api.proto.java.Key.newBuilder()
				.setEd25519(ByteString.copyFrom(toBytes()))
				.build();
	}
}
