package com.hedera.services.bdd.suites.utils.keypairs;

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

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
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
	 * Convert an Ed25519PublicKey bytes into a java type {@link PublicKey}
	 *
	 * @param ed25519KeyBytes
	 * 		given Ed25519PublicKey byte array
	 * @return the converted java type {@link PublicKey} from the given byte array
	 */
	public static PublicKey convert(final byte[] ed25519KeyBytes) {
		if (ed25519KeyBytes == null || ed25519KeyBytes.length < 1) {
			throw new IllegalArgumentException("Ed25519 byte array is empty.");
		}
		EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(ed25519KeyBytes,
				EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519));
		return new EdDSAPublicKey(pubKeySpec);
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

}
