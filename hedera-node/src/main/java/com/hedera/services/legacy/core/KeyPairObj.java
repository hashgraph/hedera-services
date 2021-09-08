package com.hedera.services.legacy.core;

/*-
 * ‌
 * Hedera Services Node
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

import com.swirlds.common.CommonUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyPairObj implements Serializable {
  private static final Logger log = LogManager.getLogger(KeyPairObj.class);
  private static final long serialVersionUID = 9146375644904969927L;
  private String publicKey;
  private String privateKey;

  public KeyPairObj(String publicKey, String privateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }


  public PrivateKey getPrivateKey() {
    PrivateKey privKey = null;
    byte[] privArray = new byte[0];
    try {
      privArray = CommonUtils.unhex(privateKey);
    } catch (IllegalArgumentException e) {
      log.info("Bad decoding: ", e);
    }
    if (privateKey.length() == 128) {
      EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
      EdDSAPrivateKeySpec pubKeySpec = new EdDSAPrivateKeySpec(spec, privArray);
      privKey = new EdDSAPrivateKey(pubKeySpec);
    } else {
      PKCS8EncodedKeySpec encoded = new PKCS8EncodedKeySpec(privArray);
      try {
        privKey = new EdDSAPrivateKey(encoded);
      } catch (InvalidKeySpecException e) {
        log.info("Private key is invalid: ", e);
      }
    }

    return privKey;
  }

  public PublicKey getPublicKey() throws IllegalArgumentException, InvalidKeySpecException {
    byte[] pubKeyBytes = CommonUtils.unhex(publicKey);
    PublicKey pubKey = null;
    if (publicKey.length() == 64) {
      EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
      EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(pubKeyBytes, spec);
      pubKey = new EdDSAPublicKey(pubKeySpec);
    } else {
      X509EncodedKeySpec pencoded = new X509EncodedKeySpec(pubKeyBytes);
      pubKey = new EdDSAPublicKey(pencoded);
    }
    return pubKey;
  }

  public KeyPair getKeyPair() throws InvalidKeySpecException, IllegalArgumentException {
    return new KeyPair(getPublicKey(), getPrivateKey());
  }


  public String getPublicKeyAbyteStr() throws InvalidKeySpecException {
    return CommonUtils.hex(((EdDSAPublicKey)getPublicKey()).getAbyte());
  }

//  private PublicKey getPublicKey() throws IllegalArgumentException, InvalidKeySpecException {
//    byte[] pubKeybytes = CommonUtils.unhex(publicKey);
//    X509EncodedKeySpec pencoded = new X509EncodedKeySpec(pubKeybytes);
//    EdDSAPublicKey pubKey = new EdDSAPublicKey(pencoded);
//    return pubKey;
//  }

}
