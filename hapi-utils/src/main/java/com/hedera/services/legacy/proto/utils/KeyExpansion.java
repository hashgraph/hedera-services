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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.ThresholdSignature;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class provides utilities to expand keys.
 *
 * @author hua
 */
public class KeyExpansion {

  private static final Logger log = LogManager.getLogger(KeyExpansion.class);
  private static int KEY_EXPANSION_DEPTH = 15; // recursion level for expansion
  public static boolean USE_HEX_ENCODED_KEY = false;

  /**
   * Generates a KeyList key from a list of keys.
   *
   * @param keys list of keys
   * @return generated KeyList key
   */
  public static Key genKeyList(List<Key> keys) {
    KeyList tkey = KeyList.newBuilder().addAllKeys(keys).build();
    Key rv = Key.newBuilder().setKeyList(tkey).build();
    return rv;
  }

  /**
   * Generates a list of Ed25519 keys.
   *
   * @param numKeys number of keys
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   *
   * @return a list of generated Ed25519 keys
   */
  public static List<Key> genEd25519Keys(int numKeys, Map<String, PrivateKey> pubKey2privKeyMap) {
    List<Key> rv = new ArrayList<>();
    for (int i = 0; i < numKeys; i++) {
      Key akey = genSingleEd25519Key(pubKey2privKeyMap);
      rv.add(akey);
    }

    return rv;
  }

  /**
   * Generates a threshold key from a list of keys.
   *
   * @param keys list of keys
   * @param threshold threshold
   *
   * @return generated threshold key
   */
  public static Key genThresholdKey(List<Key> keys, int threshold) {
    ThresholdKey tkey = ThresholdKey.newBuilder()
        .setKeys(KeyList.newBuilder().addAllKeys(keys).build())
        .setThreshold(threshold).build();
    Key rv = Key.newBuilder().setThresholdKey(tkey).build();
    return rv;
  }

  /**
   * Signs a message for a complex key up to a given level of depth. Both the signature and the key
   * may be complex with multiple levels.
   *
   * @param key the complex key used to sign
   * @param message message to be signed
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @param depth current level that is to be verified. The first level has a value of 1.
   *
   * @return the complex signature generated
   * @throws Exception for failed sign
   */
  public static Signature sign(Key key, byte[] message, Map<String, PrivateKey> pubKey2privKeyMap,
      int depth)
      throws Exception {
    if (depth > KEY_EXPANSION_DEPTH) {
      log.warn("Exceeding max expansion depth of " + KEY_EXPANSION_DEPTH);
    }

    if (!(key.hasThresholdKey() || key.hasKeyList())) {
      Signature result = signBasic(key, pubKey2privKeyMap, message);
      log.debug("depth=" + depth + "; signBasic: result=" + result + "; key=" + key);
      return result;
    } else if (key.hasThresholdKey()) {
      List<Key> tKeys = key.getThresholdKey().getKeys().getKeysList();
      List<Signature> signatures = new ArrayList<>();
      int cnt = 0;
      int thd = key.getThresholdKey().getThreshold();
      Signature signature = null;
      for (Key aKey : tKeys) {
        if (cnt < thd) {
          signature = sign(aKey, message, pubKey2privKeyMap, depth + 1);
          cnt++;
        } else {
          signature = genEmptySignature();
        }
        signatures.add(signature);
      }

      Signature result = Signature.newBuilder()
          .setThresholdSignature(ThresholdSignature.newBuilder()
              .setSigs(SignatureList.newBuilder().addAllSigs(signatures).build())
              .build()).build();
      log.debug("depth=" + depth + "; sign ThresholdKey: result=" + result + "; threshold=" + thd);
      return (result);
    } else {
      List<Key> tKeys = key.getKeyList().getKeysList();
      List<Signature> signatures = new ArrayList<>();
      Signature signature = null;
      for (Key aKey : tKeys) {
        signature = sign(aKey, message, pubKey2privKeyMap, depth + 1);
        signatures.add(signature);
      }

      Signature result = Signature.newBuilder()
          .setSignatureList(SignatureList.newBuilder().addAllSigs(signatures).build()).build();
      log.debug("depth=" + depth + "; sign KeyList: result=" + result);
      return (result);
    }
  }

  /**
   * Generates an empty signature.
   *
   * @return the empty signature generated
   */
  private static Signature genEmptySignature() throws DecoderException {
    String EMPTY_STR = "";
    Signature rv = Signature.newBuilder()
        .setEd25519(ByteString.copyFrom(Hex.decodeHex(EMPTY_STR))).build();
    return rv;
  }

  /**
   * Signs a basic key.
   *
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @return the signature generated
   */
  private static Signature signBasic(Key key, Map<String, PrivateKey> pubKey2privKeyMap,
      byte[] msgBytes)
      throws Exception {
    Signature rv;
    if (key.hasContractID()) {
      rv = genEmptySignature();
    } else if (!key.getEd25519().isEmpty()) {
      String pubKeyHex = null;
      if (USE_HEX_ENCODED_KEY) {
        pubKeyHex = key.getEd25519().toStringUtf8();
      } else {
        byte[] pubKeyBytes = key.getEd25519().toByteArray();
        pubKeyHex = Hex.encodeHexString(pubKeyBytes);
      }
      PrivateKey privKey = pubKey2privKeyMap.get(pubKeyHex);
      String sigHex = SignatureGenerator.signBytes(msgBytes, privKey);
      rv = Signature.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(sigHex)))
          .build();
    } else if (!key.getECDSA384().isEmpty()) {
      String pubKeyHex = null;
      if (USE_HEX_ENCODED_KEY) {
        pubKeyHex = key.getECDSA384().toStringUtf8();
      } else {
        byte[] pubKeyBytes = key.getECDSA384().toByteArray();
        pubKeyHex = Hex.encodeHexString(pubKeyBytes);
      }
      PrivateKey privKey = pubKey2privKeyMap.get(pubKeyHex);
      String sigHex = SignatureGenerator.signBytes(msgBytes, privKey);
      rv = Signature.newBuilder().setECDSA384(ByteString.copyFrom(Hex.decodeHex(sigHex)))
          .build();
    } else {
      throw new Exception("Key type not implemented: key=" + key);
    }
    return rv;
  }

  /**
   * Generates a single Ed25519 key.
   *
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @return generated Ed25519 key
   */
  public static Key genSingleEd25519Key(Map<String, PrivateKey> pubKey2privKeyMap) {
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyHex = null;
    Key akey = null;

    if (USE_HEX_ENCODED_KEY) {
      pubKeyHex = Hex.encodeHexString(pubKey);
      akey = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyHex)).build();
    } else {
      pubKeyHex = Hex.encodeHexString(pubKey);
      akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    }

    pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
    return akey;
  }

  /**
   * Generate a Key instance based on an existing public key of type Ed25519.
   *
   * @param pubKey public key of type Ed25519
   * @return generated Key instance
   */
  public static Key genEd25519Key(PublicKey pubKey) {
    byte[] pubKeyBytes = ((EdDSAPublicKey) pubKey).getAbyte();
    Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKeyBytes)).build();
    return akey;
  }

  /**
   * Generates a key list instance.
   *
   * @param numKeys number of keys in the generated key
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @return generated key list
   */
  public static Key genKeyListInstance(int numKeys, Map<String, PrivateKey> pubKey2privKeyMap) {
    List<Key> keys = KeyExpansion.genEd25519Keys(numKeys, pubKey2privKeyMap);
    Key rv = KeyExpansion.genKeyList(keys);
    return rv;
  }

  /**
   * Generates a threshold key instance.
   *
   * @param numKeys number of keys in the generated key
   * @param threshold the threshold for the generated key
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @return generated threshold key
   */
  public static Key genThresholdKeyInstance(int numKeys, int threshold,
      Map<String, PrivateKey> pubKey2privKeyMap) {
    List<Key> keys = KeyExpansion.genEd25519Keys(numKeys, pubKey2privKeyMap);
    Key rv = KeyExpansion.genThresholdKey(keys, threshold);
    return rv;
  }

  /**
   * Generates a single Ed25519 key.
   *
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @return generated Ed25519 key
   */
  public static Key genSingleEd25519KeyByteEncodePubKey(Map<String, PrivateKey> pubKey2privKeyMap) {
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyHex = null;
    Key akey = null;
    pubKeyHex = Hex.encodeHexString(pubKey);
    akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

    pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
    return akey;
  }

  /**
   * Expands a key to a given level of depth, only keys needed for signing are expanded.
   *
   * @param key key
   * @param depth depth
   * @param expandedKeys list of expanded keys
   */
  public static void expandKeyMinimum4Signing(Key key, int depth, List<Key> expandedKeys) {
    if (!(key.hasThresholdKey() || key.hasKeyList())) {
      expandedKeys.add(key);
    } else if (key.hasThresholdKey()) {
      List<Key> tKeys = key.getThresholdKey().getKeys().getKeysList();
      int thd = key.getThresholdKey().getThreshold();
      if (depth <= KEY_EXPANSION_DEPTH) {
        depth++;
        int i = 0;
        for (Key aKey : tKeys) {
          if(i++ >= thd) // if threshold is reached, stop expanding keys
		  {
		    log.debug("Threshold reached, stopping key expansion.");
            break;
		  }
          expandKeyMinimum4Signing(aKey, depth, expandedKeys);
        }
      }
    } else {
      List<Key> tKeys = key.getKeyList().getKeysList();
      if (depth <= KEY_EXPANSION_DEPTH) {
        depth++;
        for (Key aKey : tKeys) {
          expandKeyMinimum4Signing(aKey, depth, expandedKeys);
        }
      }
    }
  }

  /**
   * Signs a basic key and returns a SignaturePair object.
   *
   * @param key  key
   * @param msgBytes message bytes
   * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
   * @param prefixLen the length of the key prefix, if -1, use the full length of the key
   *
   * @return the SignaturePair generated
   * @throws Exception when key type is not implemented
   */
  public static SignaturePair signBasicAsSignaturePair(Key key, int prefixLen, Map<String, PrivateKey> pubKey2privKeyMap,
      byte[] msgBytes)
      throws Exception {
    SignaturePair rv;
    if (!key.getEd25519().isEmpty()) {
      byte[] pubKeyBytes = key.getEd25519().toByteArray();
      String pubKeyHex = Hex.encodeHexString(pubKeyBytes);
      byte[] prefixBytes = pubKeyBytes;
      if(prefixLen != -1) {
		  prefixBytes = CommonUtils.copyBytes(0, prefixLen, pubKeyBytes);
	  }
      PrivateKey privKey = pubKey2privKeyMap.get(pubKeyHex);
      String sigHex = SignatureGenerator.signBytes(msgBytes, privKey);
      rv = SignaturePair.newBuilder().setPubKeyPrefix(ByteString.copyFrom(prefixBytes))
              .setEd25519(ByteString.copyFrom(Hex.decodeHex(sigHex)))
          .build();
    } else {
      throw new Exception("Key type not implemented: key=" + key);
    }
    return rv;
  }
}
