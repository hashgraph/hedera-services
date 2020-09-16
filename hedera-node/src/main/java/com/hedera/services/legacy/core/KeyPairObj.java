package com.hedera.services.legacy.core;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.utils.MiscUtils;

import java.io.Serializable;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.codec.DecoderException;

public class KeyPairObj implements Serializable {
	private static final long serialVersionUID = 9146375644904969927L;
	private String publicKey;
	private String privateKey;

	public KeyPairObj(String publicKey, String privateKey) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}

	public String getPublicKeyStr() {
		return publicKey;
	}

	public String getPrivateKeyStr() {
		return privateKey;
	}

	public String getPublicKeyAbyteStr() throws InvalidKeySpecException, DecoderException {
		return MiscUtils.commonsBytesToHex(((EdDSAPublicKey) getPublicKey()).getAbyte());
	}

	private PublicKey getPublicKey() throws DecoderException, InvalidKeySpecException {
		byte[] pubKeybytes = MiscUtils.commonsHexToBytes(publicKey);
		X509EncodedKeySpec pencoded = new X509EncodedKeySpec(pubKeybytes);
		EdDSAPublicKey pubKey = new EdDSAPublicKey(pencoded);
		return pubKey;
	}

}
