package com.hederahashgraph.builder;

/*-
 * ‌
 * Hedera Services API
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

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.hedera.services.legacy.proto.utils.CommonUtils;
import org.apache.commons.codec.DecoderException;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignatureList.Builder;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.codec.binary.Hex;

/**
 * Transaction Signing utility.
 *
 * @author hua
 */
public class TransactionSigner {

  /**
   * Signature algorithm
   */
  static final String ECDSA_SIGNATURE_ALGORITHM = "SHA384withECDSA";
  public static enum SIGNATURE_FORMAT_ENUM {
    SignatureList,SignatureMap,Random
  }
  public static SIGNATURE_FORMAT_ENUM SIGNATURE_FORMAT = SIGNATURE_FORMAT_ENUM.SignatureMap;

  public static enum TX_BODY_FORMAT_ENUM {
    Body,BodyBytes,Random
  }
  public static TX_BODY_FORMAT_ENUM TX_BODY_FORMAT = TX_BODY_FORMAT_ENUM.BodyBytes;
  private static Random rand = new Random();

  /**
   * Generates ED25519 or ECDSA signature depending on the type of private key provided.
   *
   * @param msgBytes message to be signed
   * @param priv the private key to sign
   * @return the generated signature as a ByteString
   */
  public static ByteString signBytes(byte[] msgBytes, PrivateKey priv) {
    // Create a Signature object and initialize it with the private key
    byte[] sigBytes = null;
    try {
      if (priv instanceof EdDSAPrivateKey) {
        EdDSAEngine engine = new EdDSAEngine();
        engine.initSign(priv);
        sigBytes = engine.signOneShot(msgBytes);

      } else {
        java.security.Signature sigInstance = null;
        sigInstance = java.security.Signature.getInstance(ECDSA_SIGNATURE_ALGORITHM);
        sigInstance.initSign(priv);
        sigInstance.update(msgBytes, 0, msgBytes.length);
        // Update and sign the data
        sigBytes = sigInstance.sign();

      }
    } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
      e.printStackTrace();
    }

    return ByteString.copyFrom(sigBytes);
  }

  /**
   * Signs a transaction using SignatureMap format with provided private keys.
   * 
   * @param transaction
   * @param privKeyList
   * @return signed transaction
   */
  public static Transaction signTransaction(Transaction transaction, List<PrivateKey> privKeyList) {
    List<Key> keyList = new ArrayList<>();
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    for (PrivateKey pk : privKeyList) {
      byte[] pubKey = ((EdDSAPrivateKey) pk).getAbyte();
      Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
      keyList.add(key);
      String pubKeyHex = Hex.encodeHexString(pubKey);
      pubKey2privKeyMap.put(pubKeyHex, pk);
    }
    try {
      return signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);
    } catch (Exception ignore) {
      ignore.printStackTrace();
    }
    return transaction;
  }

  /**
   * Signs transaction using SignatureList with provided private keys. The generated signatures are contained in a
   * Signature object of type SignatureList. This Signature object becomes the single element in the
   * signature list for the transaction.
   *
   * @param transaction transaction to be singed
   * @param privKeyList private key list for signing
   * @return transaction with signatures
   */
//  public static Transaction signTransactionNew(Transaction transaction,
//      List<List<PrivateKey>> privKeysList) {
//    Transaction rv = null;
//
//    byte[] bodyBytes;
//    if(transaction.hasBody()) {
//    	bodyBytes = transaction.getBody().toByteArray();
//    }else {
//    	bodyBytes = transaction.getBodyBytes().toByteArray();
//    }
//    Builder allSigsBuilder = SignatureList.newBuilder();
//
//    for (List<PrivateKey> privKeyList : privKeysList) {
//      List<Signature> sigs = new ArrayList<>();
//      for (PrivateKey priv : privKeyList) {
//        com.hederahashgraph.api.proto.java.Signature.Builder sigBuilder = Signature.newBuilder();
//        ByteString sigBytes = signBytes(bodyBytes, priv);
//        if (priv instanceof EdDSAPrivateKey) {
//          sigBuilder.setEd25519(sigBytes);
//        } else {
//          sigBuilder.setECDSA384(sigBytes);
//        }
//
//        Signature sig = sigBuilder.build();
//        sigs.add(sig);
//      }
//
//      Signature sigEntry = null;
////			if(privKeyList.size() == 1) {
////				sigEntry = sigs.get(0);
////			} else {
//      sigEntry = Signature.newBuilder()
//          .setSignatureList(SignatureList.newBuilder().addAllSigs(sigs)).build();
////			}
//
//      allSigsBuilder.addSigs(sigEntry);
//    }
//    SignatureList sigs = allSigsBuilder.build();
//    if(transaction.hasBody()) {
//    	rv = Transaction.newBuilder().setBody(transaction.getBody()).setSigs(sigs).build();
//    }else {
//    	rv = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes()).setSigs(sigs).build();
//    }
//    return rv;
//  }

  /**
   * Signs transaction with provided key and public to private key map. The generated signatures are
   * contained in a SignatureMap object.
   *
   * @param transaction transaction to be singed
   * @param keys complex keys for signing
   * @param privKeyList private key list for signing, each key in keys corresponds to a list of
   * private keys.
   * @return transaction with signatures as a SignatureMap object
   */
  public static Transaction signTransactionComplexWithSigMap(Transaction transaction, List<Key> keys,
      Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {
    byte[] bodyBytes = CommonUtils.extractTransactionBodyBytes(transaction).toByteArray();
    SignatureMap sigsMap = signAsSignatureMap(bodyBytes, keys, pubKey2privKeyMap);

    return transaction.toBuilder().setSigMap(sigsMap).build();
  }

  /**
   * Signs a message with provided key and public to private key map. The generated signatures are
   * contained in a SignatureMap object.
   * 
   * @param messageBytes
   * @param keys complex keys for signing
   * @param privKeyList private key list for signing, each key in keys corresponds to a list of
   * private keys.
   * @return transaction with signatures as a SignatureMap object
   * @throws Exception
   */
  public static SignatureMap signAsSignatureMap(byte[] messageBytes, List<Key> keys,
    Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {
    List<Key> expandedKeys = new ArrayList<>();
    Key aKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(keys).build()).build();
    KeyExpansion.expandKeyMinimum4Signing(aKey, 1, expandedKeys);
    Set<Key> uniqueKeys = new HashSet<>(expandedKeys);
    int len = findMinPrefixLength(uniqueKeys);
    
    List<SignaturePair> pairs = new ArrayList<>();
    for (Key key : uniqueKeys) {
       if (key.hasContractID()) {
         // according to Leemon, "for Hedera transactions, we treat this key as never having signatures."
        continue;
      }
      
      SignaturePair sig = KeyExpansion.signBasicAsSignaturePair(key, len, pubKey2privKeyMap, messageBytes);
      pairs.add(sig);
    }
    SignatureMap sigsMap = SignatureMap.newBuilder().addAllSigPair(pairs).build();
    return sigsMap;
  }

  /**
   * Finds the minimum prefix length in term of bytes.
   * @param keys set of keys to process 
   * @return found minimum prefix length
   */
  private static int findMinPrefixLength(Set<Key> keys) {
    if(keys.size() == 1)
      return 0;
    
    int rv = 0;
    int numKeys = keys.size();
    //convert set to list of key hex strings
    //find max string length
    List<String> keyHexes = new ArrayList<>();
    int maxBytes = 0;
    for(Key key : keys) {
      byte[] bytes = key.getEd25519().toByteArray();
      if(bytes.length > maxBytes)
        maxBytes = bytes.length;
      String hex = Hex.encodeHexString(bytes);
      keyHexes.add(hex);
    }
    
    rv = maxBytes;
    
    //starting from first byte (each byte is 2 hex chars) to max/2 and loop with step of 2
    for(int i = 1; i <= maxBytes; i++) {
      // get all the prefixes and form a set (unique ones), check if size of the set is reduced.
      Set<String> prefixSet = new HashSet<>();
      for(String khex : keyHexes) {
        prefixSet.add(khex.substring(0, i * 2));
      }
      // if not reduced, the current prefix size is the answer, stop
      if(prefixSet.size() == numKeys ) {
        rv = i;
        break;
      }
    }
    
    return rv;
  }

  /**
   * Signs transaction using signature map format.
   *
   * @param transaction transaction to be singed
   * @param privKeyList private key list for signing
   * @return transaction with signatures
   * @throws Exception
   */
    public static Transaction signTransactionNewWithSignatureMap(Transaction transaction,
        List<List<PrivateKey>> privKeysList, List<List<PublicKey>> pubKeysList) throws Exception {
      byte[] bodyBytes = CommonUtils.extractTransactionBodyBytes(transaction).toByteArray();

      if(pubKeysList.size() != privKeysList.size()) {
        new Exception("public and private keys size mismtach! pubKeysList size = " + pubKeysList.size() + ", privKeysList size = " + privKeysList.size());
      }

      List<SignaturePair> pairs = new ArrayList<>();
      int i = 0;
      for (List<PrivateKey> privKeyList : privKeysList) {
        List<PublicKey> pubKeyList = pubKeysList.get(i++);
        int j = 0;
        for(PrivateKey privKey : privKeyList) {
          PublicKey pubKey = pubKeyList.get(j++);
          SignaturePair sig = signAsSignaturePair(pubKey, privKey, bodyBytes);
          pairs.add(sig);
        }
      }
      SignatureMap sigsMap = SignatureMap.newBuilder().addAllSigPair(pairs).build();
      return transaction.toBuilder().setSigMap(sigsMap).build();
    }

  private static SignaturePair signAsSignaturePair(PublicKey pubKey, PrivateKey privKey,
      byte[] bodyBytes) throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException, DecoderException {
    byte[] pubKeyBytes = ((EdDSAPublicKey) pubKey).getAbyte();

    String sigHex = SignatureGenerator.signBytes(bodyBytes, privKey);
    SignaturePair rv = SignaturePair.newBuilder()
        .setPubKeyPrefix(ByteString.copyFrom(pubKeyBytes))
        .setEd25519(ByteString.copyFrom(Hex.decodeHex(sigHex))).build();
    return rv;
  }

  /**
   * Signs a transaction using SignatureMap format with provided private keys and corresponding public keys.
   * 
   * @param transaction
   * @param privKeyList
   * @return signed transaction
   * @throws Exception 
   */
  public static Transaction signTransactionWithSignatureMap(Transaction transaction, List<PrivateKey> privKeyList, List<PublicKey> pubKeyList) throws Exception {
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    List<List<PublicKey>> pubKeysList = new ArrayList<>();
    int i = 0;
    for (PrivateKey pk : privKeyList) {
      List<PrivateKey> aList = new ArrayList<>();
      aList.add(pk);
      privKeysList.add(aList);

      List<PublicKey> bList = new ArrayList<>();
      PublicKey pubKey = pubKeyList.get(i++);
      bList.add(pubKey);
      pubKeysList.add(bList);
    }

    return signTransactionNewWithSignatureMap(transaction, privKeysList, pubKeysList);
  }
}
