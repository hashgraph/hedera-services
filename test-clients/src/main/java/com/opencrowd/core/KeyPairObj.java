package com.opencrowd.core;

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

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import com.hedera.services.legacy.client.test.ClientBaseThread;
import com.hedera.services.legacy.core.HexUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;


public class KeyPairObj implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 9146375644904969927L;
	private String publicKey;


	private String privateKey;

	private PrivateKey privKey;


	public KeyPairObj(String publicKey, String privateKey) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}


	public String getPublicKeyStr() {
		return publicKey;
	}


	public void setPublicKeyStr(String publicKey) {
		this.publicKey = publicKey;
	}


	public String getPrivateKeyStr() {
		return privateKey;
	}


	public void setPrivateKeyStr(String privateKey) {
		this.privateKey = privateKey;
	}

	public static String getPrivateKeyString (PrivateKey pKey) {

		return null;
	}

	public PrivateKey getPrivateKey() {
		PrivateKey privKey=null;
		byte[] privArray = new byte[0];
		try {
			privArray = Hex.decodeHex(privateKey);
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		if(privateKey.length()==128) {
			EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
			EdDSAPrivateKeySpec pubKeySpec = new EdDSAPrivateKeySpec(spec, privArray);
			privKey = new EdDSAPrivateKey(pubKeySpec);
		} else {
			PKCS8EncodedKeySpec encoded = new PKCS8EncodedKeySpec(privArray);
			try{
				privKey = new EdDSAPrivateKey(encoded);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return privKey;
	}



	public PrivateKey getPrivateKey_128() {
		byte[] privArray = null;
		try {
			privArray=Hex.decodeHex(privateKey);
		} catch (Exception e) {
			e.printStackTrace();
		}
		EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		EdDSAPrivateKeySpec pubKeySpec = new EdDSAPrivateKeySpec(spec, privArray);
		PrivateKey privKey = new EdDSAPrivateKey(pubKeySpec);
		return privKey;
	}


	public PublicKey getPublicKey() throws DecoderException, InvalidKeySpecException {
		byte[] pubKeyBytes = HexUtils.hexToBytes(publicKey);
		PublicKey pubKey = null;
		if(publicKey.length()==64) {
			EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
			EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(pubKeyBytes, spec);
			pubKey = new EdDSAPublicKey(pubKeySpec);
		} else {
			X509EncodedKeySpec pencoded = new X509EncodedKeySpec(pubKeyBytes);
			pubKey = new EdDSAPublicKey(pencoded);
		}
		return pubKey;
	}

	public PublicKey getPublicKey_64() {
		byte[] pubKeyBytes=null;
		try {
			pubKeyBytes = ClientBaseThread.hexToBytes(publicKey);
		} catch(Exception e) {
			e.printStackTrace();
		}
		EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(pubKeyBytes, spec);
		PublicKey pubKey = new EdDSAPublicKey(pubKeySpec);
		return pubKey;
	}

	public KeyPair getKeyPair() throws InvalidKeySpecException, DecoderException {
		return new KeyPair(getPublicKey(), getPrivateKey());
	}

	public String getPublicKeyAbyteStr() throws InvalidKeySpecException, DecoderException {
		return HexUtils.bytes2Hex(((EdDSAPublicKey) getPublicKey()).getAbyte());
	}


}
