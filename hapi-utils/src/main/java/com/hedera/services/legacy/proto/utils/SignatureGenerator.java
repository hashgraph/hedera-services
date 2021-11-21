package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
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

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;

public final class SignatureGenerator {
	private SignatureGenerator() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Signs a message with a private key.
	 *
	 * @param msgBytes
	 * 		to be signed
	 * @param privateKey
	 * 		private key
	 * @return signature in hex format
	 * @throws InvalidKeyException
	 * 		if the key is invalid
	 * @throws SignatureException
	 * 		if there is an error in the signature
	 */
	public static byte[] signBytes(
			final byte[] msgBytes,
			final PrivateKey privateKey
	) throws InvalidKeyException, SignatureException {
		if (!(privateKey instanceof EdDSAPrivateKey)) {
			throw new IllegalArgumentException("Only Ed25519 signatures are supported at this time!");
		}
		final var engine = new EdDSAEngine();
		engine.initSign(privateKey);
		return engine.signOneShot(msgBytes);
	}
}
