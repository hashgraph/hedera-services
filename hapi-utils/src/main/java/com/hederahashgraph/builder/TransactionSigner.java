package com.hederahashgraph.builder;

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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.apache.commons.codec.binary.Hex;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transaction Signing utility.
 *
 * @author hua
 */
public class TransactionSigner {
  /**
   * Signs a transaction using SignatureMap format with provided private keys.
   * 
   * @param transaction transaction
   * @param privKeyList private key list
   *
   * @return signed transaction
   */
  public static Transaction signTransaction(Transaction transaction, List<PrivateKey> privKeyList) {
    return signTransaction(transaction, privKeyList, false);
  }

  public static Transaction signTransaction(Transaction transaction, List<PrivateKey> privKeyList,
          boolean appendSigMap) {
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
      return signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap, appendSigMap);
    } catch (Exception ignore) {
      ignore.printStackTrace();
    }
    return transaction;
  }

  /**
   * Signs transaction with provided key and public to private key map. The generated signatures are
   * contained in a SignatureMap object.
   *
   * @param transaction transaction to be singed
   * @param keys complex keys for signing
   * @param pubKey2privKeyMap public key to private key map
   * @return transaction with signatures as a SignatureMap object
   * @throws Exception when transaction sign fails
   */
  public static Transaction signTransactionComplexWithSigMap(TransactionOrBuilder transaction, List<Key> keys,
      Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {
    return signTransactionComplexWithSigMap(transaction, keys, pubKey2privKeyMap, false);
  }

  public static Transaction signTransactionComplexWithSigMap(TransactionOrBuilder transaction, List<Key> keys,
      Map<String, PrivateKey> pubKey2privKeyMap, boolean appendSigMap) throws Exception {
    byte[] bodyBytes = CommonUtils.extractTransactionBodyBytes(transaction);
    SignatureMap sigsMap = signAsSignatureMap(bodyBytes, keys, pubKey2privKeyMap);

    Transaction.Builder builder = CommonUtils.toTransactionBuilder(transaction);

    if (appendSigMap) {
      SignatureMap currentSigMap = CommonUtils.extractSignatureMapOrUseDefault(transaction);
      SignatureMap sigMapToSet = currentSigMap.toBuilder().addAllSigPair(sigsMap.getSigPairList()).build();
      return builder.setSigMap(sigMapToSet).build();
    }

    return builder.setSigMap(sigsMap).build();
  }

  /**
   * Signs a message with provided key and public to private key map. The generated signatures are
   * contained in a SignatureMap object.
   * 
   * @param messageBytes message bytes
   * @param keys complex keys for signing
   * @param pubKey2privKeyMap public key to private key map
   * @return transaction with signatures as a SignatureMap object
   * @throws Exception when sign fails
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
    if (keys.size() == 1) {
      return 3;
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
    
    return Math.max(3, rv);
  }
}
