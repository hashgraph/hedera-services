package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API
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
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.X509EncodedKeySpec;

public class SignatureVerifier {

  /**
   * Crypto algorithm for key pair
   */
  static final String KEY_ALGORITHM = "EC"; // "DSA"

  /**
   * Signature algorithm
   */
  static final String SIGNATURE_ALGORITHM = "SHA384withECDSA"; // "SHA1withDSA"

  /**
   * Character set name for messages to be signed
   */
  public static final String CHARACTER_SET_NAME = "UTF-8";

  /**
   * Size of the key pair
   */
  public static final int KEY_SIZE = 384;

  /**
   * Secure random algorithm
   */
  public static final String RANDOM_ALGORITHM = "SHA1PRNG";

  /**
   * Verifies a signature for a message with a provided public key.
   *
   * @param signature the digital signature to be verified
   * @param msg the data signed
   * @param pubKeyStr the hex encoded public key for verification
   * @return true if verified, false otherwise
   */
  public static boolean verifyECDSA(String signature, String msg, String pubKeyStr)
      throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, SignatureException,
      InvalidKeyException, DecoderException, UnsupportedEncodingException, InvalidParameterSpecException {

    if (pubKeyStr.startsWith("04")) {
      return verifyWithBinaryEC384PublicKey(signature, msg, pubKeyStr);
    } else {
      return verifyX509PublicKey(signature, msg, pubKeyStr);
    }
  }

  /**
   * Verifies a signature for a message with a provided public key.
   *
   * @param signature the digital signature to be verified
   * @param msg the data signed
   * @param pubKeyStr the hex encoded public key for verification
   * @return true if verified, false otherwise
   */
  public static boolean verifyX509PublicKey(String signature, String msg, String pubKeyStr)
      throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, SignatureException,
      InvalidKeyException, DecoderException, UnsupportedEncodingException {
    boolean rv = false;
    byte[] sigBytes = getSigBytes(signature);
    byte[] msgBytes = getMsgBytes(msg);
    byte[] pubKeyBytes = getPubKeyBytes(pubKeyStr);

    X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
    PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);

    // create a Signature object and initialize it with the public key
    Signature sigInstance = Signature.getInstance(SIGNATURE_ALGORITHM);
    sigInstance.initVerify(pubKey);

    // Update and verify the data
    sigInstance.update(msgBytes, 0, msgBytes.length);
    rv = sigInstance.verify(sigBytes);

    return rv;
  }

  /**
   * Verifies a signature for a message with an EC public key in binary format.
   *
   * @param signature the digital signature to be verified
   * @param msg the data signed
   * @param pubKeyStr the hex encoded EC public key in binary format (i.e. 0x04 followed by x and y
   * coordinates) with for verification
   * @return true if verified, false otherwise
   */
  public static boolean verifyWithBinaryEC384PublicKey(String signature, String msg,
      String pubKeyStr)
      throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, SignatureException,
      InvalidKeyException, DecoderException, UnsupportedEncodingException, InvalidParameterSpecException {
    boolean rv = false;
    byte[] sigBytes = getSigBytes(signature);
    byte[] msgBytes = getMsgBytes(msg);

    ECPublicKey pubKey = SignatureGenerator.getEC384PublicKeyFromBinary(pubKeyStr);

    // create a Signature object and initialize it with the public key
    Signature sigInstance = Signature.getInstance(SIGNATURE_ALGORITHM);
    sigInstance.initVerify(pubKey);

    // Update and verify the data
    sigInstance.update(msgBytes, 0, msgBytes.length);
    rv = sigInstance.verify(sigBytes);

    return rv;
  }

  /**
   * Converts public key string to bytes.
   *
   * @param pubKey to be converted
   * @return converted bytes
   */
  private static byte[] getPubKeyBytes(String pubKey) throws DecoderException {
    byte[] rv = null;
    rv = Hex.decodeHex(pubKey);
    return rv;
  }

  /**
   * Converts msg string to bytes.
   *
   * @param msg to be converted
   * @return converted bytes
   */
  static byte[] getMsgBytes(String msg) throws UnsupportedEncodingException {
    byte[] rv = null;
    rv = msg.getBytes(CHARACTER_SET_NAME);
    return rv;
  }

  /**
   * Converts signature string to bytes.
   *
   * @param signature string to be converted
   * @return converted bytes
   */
  private static byte[] getSigBytes(String signature) throws DecoderException {
    byte[] rv = null;
    rv = Hex.decodeHex(signature);
    return rv;
  }

  public static boolean verifyED25519(byte[] pubKeyBytes, byte[] msgBytes, byte[] sigBytes)
      throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, DecoderException {

    EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(pubKeyBytes, spec);
    PublicKey vKey = new EdDSAPublicKey(pubKey);
    Signature verifier = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));

    verifier.initVerify(vKey);
    verifier.update(msgBytes);
    return verifier.verify(sigBytes);
  }

}
