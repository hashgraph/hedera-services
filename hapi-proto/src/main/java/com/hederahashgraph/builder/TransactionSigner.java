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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
  public enum SIGNATURE_FORMAT_ENUM {
    SignatureList,SignatureMap,Random
  }
  public static SIGNATURE_FORMAT_ENUM SIGNATURE_FORMAT = SIGNATURE_FORMAT_ENUM.SignatureMap;

  public enum TX_BODY_FORMAT_ENUM {
    Body,BodyBytes,Random
  }
  public static TX_BODY_FORMAT_ENUM TX_BODY_FORMAT = TX_BODY_FORMAT_ENUM.BodyBytes;
  private static final Random rand = new Random();

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
   * Signs a transaction using SignatureList format with provided private keys.
   * 
   * @param transaction
   * @param privKeyList
   * @return signed transaction
   */
  public static Transaction signTransaction(Transaction transaction, List<PrivateKey> privKeyList) {
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    for (PrivateKey pk : privKeyList) {
      List<PrivateKey> aList = new ArrayList<>();
      aList.add(pk);
      privKeysList.add(aList);
    }

    return signTransactionNew(transaction, privKeysList);
  }

  /**
   * Signs transaction using SignatureList with provided private keys. The generated signatures are contained in a
   * Signature object of type SignatureList. This Signature object becomes the single element in the
   * signature list for the transaction.
   *
   * @param transaction transaction to be singed
   * @param privKeysList private key list for signing
   * @return transaction with signatures
   */
  public static Transaction signTransactionNew(Transaction transaction,
      List<List<PrivateKey>> privKeysList) {
    Transaction rv = null;

    byte[] bodyBytes;
    if(transaction.hasBody()) {
    	bodyBytes = transaction.getBody().toByteArray();
    }else {
    	bodyBytes = transaction.getBodyBytes().toByteArray();
    }
    Builder allSigsBuilder = SignatureList.newBuilder();

    for (List<PrivateKey> privKeyList : privKeysList) {
      List<Signature> sigs = new ArrayList<>();
      for (PrivateKey priv : privKeyList) {
        com.hederahashgraph.api.proto.java.Signature.Builder sigBuilder = Signature.newBuilder();
        ByteString sigBytes = signBytes(bodyBytes, priv);
        if (priv instanceof EdDSAPrivateKey) {
          sigBuilder.setEd25519(sigBytes);
        } else {
          sigBuilder.setECDSA384(sigBytes);
        }

        Signature sig = sigBuilder.build();
        sigs.add(sig);
      }

      Signature sigEntry = null;
      sigEntry = Signature.newBuilder()
          .setSignatureList(SignatureList.newBuilder().addAllSigs(sigs)).build();

      allSigsBuilder.addSigs(sigEntry);
    }
    SignatureList sigs = allSigsBuilder.build();
    if(transaction.hasBody()) {
      rv = Transaction.newBuilder().setBody(transaction.getBody()).setSigs(sigs).build();
    } else {
      rv = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes()).setSigs(sigs).build();
    }
    return rv;
  }

  /**
   * Signs transaction with provided key and public to private key map. The generated signatures are
   * contained in a Signature object of type SignatureList. This Signature object becomes the single
   * element in the signature list for the transaction.
   *
   * @param transaction transaction to be singed
   * @param keys complex keys for signing
   * @param pubKey2privKeyMap
   * @return transaction with signatures
   */
  public static Transaction signTransactionComplex(Transaction transaction, List<Key> keys,
      Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {
    if(SIGNATURE_FORMAT_ENUM.SignatureMap.equals(SIGNATURE_FORMAT)) {
      return signTransactionComplexWithSigMap(transaction, keys, pubKey2privKeyMap);
    } else if(SIGNATURE_FORMAT_ENUM.Random.equals(SIGNATURE_FORMAT)) {
      int coin = rand.nextInt(2);
      if(coin == 0) {
        return signTransactionComplexWithSigMap(transaction, keys, pubKey2privKeyMap);
      }
    }
    
    Transaction rv = null;
    byte[] bodyBytes;
    if(transaction.hasBody()) {
      bodyBytes = transaction.getBody().toByteArray();
    } else {
      bodyBytes = transaction.getBodyBytes().toByteArray();
    }
    
    List<Signature> sigs = new ArrayList<>();
    for (Key key : keys) {
      Signature sig = KeyExpansion.sign(key, bodyBytes, pubKey2privKeyMap, 1);
      sigs.add(sig);
    }
    SignatureList sigsList = SignatureList.newBuilder().addAllSigs(sigs).build();

    // tx has bodybytes
    if(transaction.hasBody()) {
      if (TX_BODY_FORMAT_ENUM.Body.equals(TX_BODY_FORMAT)) {
        rv = Transaction.newBuilder().setBody(transaction.getBody()).setSigs(sigsList).build();
      } else if (TX_BODY_FORMAT_ENUM.BodyBytes.equals(TX_BODY_FORMAT)) {
        rv = Transaction.newBuilder().setBodyBytes(ByteString.copyFrom(bodyBytes)).setSigs(sigsList).build();
      } else {//random
        int coin = rand.nextInt(2);
        if (coin == 0) {
          rv = Transaction.newBuilder().setBody(transaction.getBody()).setSigs(sigsList).build();
        } else {
          rv = Transaction.newBuilder().setBodyBytes(ByteString.copyFrom(bodyBytes)).setSigs(sigsList).build();
        }
      }
    } else if (TX_BODY_FORMAT_ENUM.Body.equals(TX_BODY_FORMAT)) {
      TransactionBody reconstructedBody = TransactionBody.parseFrom(bodyBytes);
      rv = Transaction.newBuilder().setBody(reconstructedBody).setSigs(sigsList).build();
    } else if (TX_BODY_FORMAT_ENUM.BodyBytes.equals(TX_BODY_FORMAT)) {
      rv = Transaction.newBuilder().setBodyBytes(ByteString.copyFrom(bodyBytes)).setSigs(sigsList).build();
    } else {//random
      int coin = rand.nextInt(2);
      if (coin == 0) {
        TransactionBody reconstructedBody = TransactionBody.parseFrom(bodyBytes);
        rv = Transaction.newBuilder().setBody(reconstructedBody).setSigs(sigsList).build();
      } else {
        rv = Transaction.newBuilder().setBodyBytes(ByteString.copyFrom(bodyBytes)).setSigs(sigsList).build();
      }
    }
    return rv;
  }

  /**
   * Signs transaction with provided key and public to private key map. The generated signatures are
   * contained in a SignatureMap object.
   *
   * @param transaction transaction to be singed
   * @param keys complex keys for signing
   * @param pubKey2privKeyMap
   * @return transaction with signatures as a SignatureMap object
   */
  public static Transaction signTransactionComplexWithSigMap(Transaction transaction, List<Key> keys,
      Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {
    Transaction rv = null;
    byte[] bodyBytes;
    if(transaction.hasBody()) {
      bodyBytes = transaction.getBody().toByteArray();
    } else {
      bodyBytes = transaction.getBodyBytes().toByteArray();
    }
    
    SignatureMap sigsMap = signAsSignatureMap(bodyBytes, keys, pubKey2privKeyMap);
    if(transaction.hasBody()) {
      rv = Transaction.newBuilder().setBody(transaction.getBody()).setSigMap(sigsMap).build();
    } else {
      rv = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes()).setSigMap(sigsMap).build();
    }
    return rv;
  }

  /**
   * Signs a message with provided key and public to private key map. The generated signatures are
   * contained in a SignatureMap object.
   * 
   * @param messageBytes
   * @param keys complex keys for signing
   * @param pubKey2privKeyMap
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
    if(keys.size() == 1) {
      return 0;
    }

    int rv = 0;
    int numKeys = keys.size();
    //convert set to list of key hex strings
    //find max string length
    List<String> keyHexes = new ArrayList<>();
    int maxBytes = 0;
    for(Key key : keys) {
      byte[] bytes = key.getEd25519().toByteArray();
      if(bytes.length > maxBytes) {
        maxBytes = bytes.length;
      }
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
   * @param privKeysList private key list for signing
   * @return transaction with signatures
   * @throws Exception
   */
    public static Transaction signTransactionNewWithSignatureMap(Transaction transaction,
        List<List<PrivateKey>> privKeysList, List<List<PublicKey>> pubKeysList) throws Exception {
      Transaction rv = null;
  
      byte[] bodyBytes;
      if(transaction.hasBody()) {
        bodyBytes = transaction.getBody().toByteArray();
      } else {
        bodyBytes = transaction.getBodyBytes().toByteArray();
      }

      if(pubKeysList.size() != privKeysList.size()) {
        throw new Exception("public and private keys size mismtach! pubKeysList size = " +
                pubKeysList.size() +
                ", privKeysList size = " +
                privKeysList.size());
      }

      final List<SignaturePair> pairs = buildSignaturePairs(privKeysList, pubKeysList, bodyBytes);

      SignatureMap sigsMap = SignatureMap.newBuilder().addAllSigPair(pairs).build();
      if(transaction.hasBody()) {
        rv = Transaction.newBuilder().setBody(transaction.getBody()).setSigMap(sigsMap).build();
      }else {
        rv = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes()).setSigMap(sigsMap).build();
      }
      return rv;
    }

  private static List<SignaturePair> buildSignaturePairs(final List<List<PrivateKey>> privKeysList,
          final List<List<PublicKey>> pubKeysList,
          final byte[] bodyBytes) throws DecoderException, SignatureException, NoSuchAlgorithmException,
          InvalidKeyException, UnsupportedEncodingException {
    final List<SignaturePair> pairs = new ArrayList<>();
    int i = 0;

    for (List<PrivateKey> privKeyList : privKeysList) {
      List<PublicKey> pubKeyList = pubKeysList.get(i++);
      for(PrivateKey privKey : privKeyList) {
        for(PublicKey pubKey : pubKeyList) {
          SignaturePair sig = signAsSignaturePair(pubKey, privKey, bodyBytes);
          pairs.add(sig);
        }
      }
    }

    return pairs;
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
