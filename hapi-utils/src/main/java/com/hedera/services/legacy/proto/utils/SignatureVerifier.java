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


import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
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
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SignatureVerifier {
  private static final Logger log = LogManager.getLogger(SignatureVerifier.class);

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
   * Checks the format of a public address.
   *
   * @param pubKeyStr the public address in hex
   * @return true if the format is correct, false otherwise
   */
  public static boolean checkECDSAAddressFormat(String pubKeyStr) throws Exception {
    boolean rv = false;

    try {
      if (pubKeyStr.startsWith("04")) {
        if (pubKeyStr.length() != 194) {
          throw new Exception("Public address is not 194 bytes. Address = " + pubKeyStr);
        }

        SignatureGenerator.getEC384PublicKeyFromBinary(pubKeyStr);
        rv = true;
      } else {
        byte[] pubKeyBytes;
        pubKeyBytes = getPubKeyBytes(pubKeyStr);

        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        keyFactory.generatePublic(pubKeySpec);
        rv = true;
      }

    } catch (Exception e) {
      rv = false;
      throw new Exception("Invalid address detected: " + pubKeyStr, e);
    }
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

  public static void main(String[] args)  {

    /* Verify a DSA signature */

    if (args.length != 3) {
      System.out.println("Usage: VerSig publickeyfile signaturefile datafile");
      System.exit(1);
    }

    /* import encoded public key */
    byte[] encKey = null;
    try (FileInputStream  keyfis = new FileInputStream(args[0])) {
        encKey = new byte[keyfis.available()];
        keyfis.read(encKey);
    } catch (IOException e) {
      System.err.println("Caught IOException when reading public key file " + e.toString());
      log.error("Caught IOException when reading public key file ", e );
    }

    X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(encKey);

    PublicKey pubKey = null;
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("DSA", "SUN");
      pubKey = keyFactory.generatePublic(pubKeySpec);
    } catch ( NoSuchAlgorithmException | NoSuchProviderException |InvalidKeySpecException e) {
      System.err.println("Caught exception when generating public key " + e.toString());
      log.error("Caught Exception when generating public key", e);
    }

      /* input the signature bytes */
    byte[] sigToVerify = null;
    try(FileInputStream sigfis = new FileInputStream(args[1])) {
      sigToVerify = new byte[sigfis.available()];
      sigfis.read(sigToVerify);
    } catch (IOException e) {
      System.err.println("Caught IOException when reading public key file " + e.toString());
      log.error("Caught IOException when reading public key file", e );
    }

      /* create a Signature object and initialize it with the public key */
    Signature sig = null;
    try {
      sig = Signature.getInstance("SHA1withDSA", "SUN");
      sig.initVerify(pubKey);
    } catch ( InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException  e) {
      System.err.println("Caught exception when initializing verifying signature " + e.toString());
      log.error("Caught Exception when initializing verifying signature", e);
    }
      /* Update and verify the data */

    try (FileInputStream datafis = new FileInputStream(args[2]);
         BufferedInputStream bufin = new BufferedInputStream(datafis)) {
      byte[] buffer = new byte[1024];
      int len;
      while (bufin.available() != 0) {
        len = bufin.read(buffer);
        sig.update(buffer, 0, len);
      }
    } catch (IOException | SignatureException e) {
      System.err.println("Caught exception when reading data file " + e.toString());
      log.error("Caught Exception when reading data file", e);
    }

    boolean verifies = false;
    try {
      verifies = sig.verify(sigToVerify);
    } catch (SignatureException e) {
      System.err.println("Caught exception when verifying signature " + e.toString());
      log.error("Caught Exception when verifying signature", e);
    }
    System.out.println("signature verifies: " + verifies);
  }


  public static boolean verifyED25519Signature(String pubKeyStr, String message, String signature)
      throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException, DecoderException {

    byte[] sigBytes = getSigBytes(signature);
    byte[] msgBytes = getMsgBytes(message);
    byte[] pubKeyBytes = getPubKeyBytes(pubKeyStr);

    EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(pubKeyBytes, spec);
    PublicKey vKey = new EdDSAPublicKey(pubKey);
    Signature verifier = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));

    verifier.initVerify(vKey);
    verifier.update(msgBytes);
    return verifier.verify(sigBytes);
  }

  public static boolean verifyED25519(byte[] pubKeyBytes, byte[] msgBytes, byte[] sigBytes)
      throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException, DecoderException {

    EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(pubKeyBytes, spec);
    PublicKey vKey = new EdDSAPublicKey(pubKey);
    Signature verifier = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));

    verifier.initVerify(vKey);
    verifier.update(msgBytes);
    return verifier.verify(sigBytes);
  }

}
