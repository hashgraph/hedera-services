package com.hedera.services.legacy.bip39utils;

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


import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import java.security.MessageDigest;
import java.security.Signature;


public class EDKeyPair implements KeyPair {

	private EdDSAPrivateKey privateKey;
	private EdDSAPublicKey publicKey;

	public EDKeyPair(byte[] seed) {
		EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		EdDSAPrivateKeySpec privateKeySpec = new EdDSAPrivateKeySpec(seed, spec);
		this.privateKey = new EdDSAPrivateKey(privateKeySpec);
		EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(privateKeySpec.getA(), spec);
		this.publicKey = new EdDSAPublicKey(pubKeySpec);
	}

	public EdDSAPrivateKey getPrivateKeySecurity() {
		return this.privateKey;
	}


	@Override
	public byte[] getPrivateKey() {
		byte[] seed = privateKey.getSeed();
		byte[] publicKey = getPublicKey();

		byte[] key = new byte[seed.length + publicKey.length];
		System.arraycopy(seed, 0, key, 0, seed.length);
		System.arraycopy(publicKey, 0, key, seed.length, publicKey.length);
		return key;
	}


	@Override
	public byte[] getPublicKey() {
		return publicKey.getAbyte();
	}


	@Override
	public byte[] signMessage(byte[] message) {
		EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		try {
			Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
			sgr.initSign(privateKey);
			sgr.update(message);
			return sgr.sign();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return new byte[0];
	}

	@Override
	public boolean verifySignature(byte[] message, byte[] signature) {
		EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		try {
			Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
			sgr.initVerify(publicKey);
			sgr.update(message);
			return sgr.verify(signature);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
