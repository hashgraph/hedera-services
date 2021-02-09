package com.hedera.services.legacy;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.ThresholdSignature;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.List;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.binary.Hex;

/**
 * @author Akshay
 * @Date : 8/20/2018
 */

public class TestHelper {

  public static long DEFAULT_SEND_RECV_RECORD_THRESHOLD = 999;
  private static long DEFAULT_WIND_SEC = -13; // seconds to wind back the UTC clock
  private static volatile long lastNano = 0;


  /**
   * Gets the current UTC timestamp with default winding back seconds.
   */
  public synchronized static Timestamp getDefaultCurrentTimestampUTC() {
    Timestamp rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
    if (rv.getNanos() == lastNano) {
      try {
        Thread.sleep(0, 1);
      } catch (InterruptedException e) {
      }
      rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
      lastNano = rv.getNanos();
    }
    return rv;
  }

  public static Transaction createAccount(AccountID payerAccount, AccountID nodeAccount,
                                          KeyPair pair, long initialBalance) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyStr = Hex.encodeHexString(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = 100;
    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    long receiveRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    boolean receiverSigRequired = false;
    Duration autoRenewPeriod = RequestBuilder.getDuration(5000);
    return RequestBuilder
            .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                    payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                    nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
                    transactionFee, timestamp, transactionDuration, generateRecord,
                    memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
                    receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
  }


  public static Transaction createTransferUnsigned(AccountID fromAccount,
                                                   AccountID toAccount, AccountID payerAccount,  AccountID nodeAccount,
                                                   long amount) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 50, timestamp, transactionDuration,
            false,
            "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
            amount);

    return transferTx;
  }

  /**
   * Generates a random long within a given range
   */
  public static long getRandomLongAccount(long max) {
    long leftLimit = 1L;
    double seed = Math.random();
    if (max <= 0) {
      max = 1000L;
    }
    long generatedLong = leftLimit + (long) (seed * (max - leftLimit));
    return generatedLong;
  }

  public static void genWacl(int numKeys, List<Key> waclPubKeyList,
                             List<PrivateKey> waclPrivKeyList) {
    for (int i = 0; i < numKeys; i++) {
      KeyPair pair = new KeyPairGenerator().generateKeyPair();
      byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
      Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
      waclPubKeyList.add(waclKey);
      waclPrivKeyList.add(pair.getPrivate());
    }
  }

  /**
   * Generate a public key
   * @return
   */
  public static Key genKey() {
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    return Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
  }

  /**
   * Generate a Threshold Key which contains a KeyList of several single keys
   * @param num the number of single keys
   * @param threshold
   * @return
   */
  public static Key genThresholdKey(int num, int threshold) {
    ThresholdKey.Builder thresholdKeyBuilder = ThresholdKey.newBuilder().setThreshold(threshold);
    KeyList.Builder keyListBuilder = KeyList.newBuilder();
    for (int i = 0; i < num; i++) {
      keyListBuilder.addKeys(genKey());
    }
    ThresholdKey thresholdKey = thresholdKeyBuilder.setKeys(keyListBuilder).build();
    return Key.newBuilder().setThresholdKey(thresholdKey).build();
  }

  /**
   * Generate Multi-Layer Threshold Key based on a Threshold Key
   * which contains a KeyList of several single keys
   * @param num the number of layers of threshold key
   * @param thresholdKey
   * @return
   */
  public static Key genMultiLayerThresholdKey(int num, Key thresholdKey) {
    if (num == 1) {
		return thresholdKey;
	}
    KeyList keyList = thresholdKey.getThresholdKey().getKeys();
    Key curr = thresholdKey;
    ThresholdKey.Builder thresholdBuilder;
    for (int i = 2; i <= num; i++ ) {
      thresholdBuilder = ThresholdKey.newBuilder();
      thresholdBuilder.setThreshold(1);
      thresholdBuilder.setKeys(keyList.toBuilder().setKeys(0, curr));
      curr = Key.newBuilder().setThresholdKey(thresholdBuilder).build();
    }
    return curr;
  }

  /**
   * Generate Multi-Layer Threshold Signature based on a Threshold Signature
   * which contains a SignatureList of several single Signatures
   * @param num the number of layers of Threshold Signature
   * @param thresholdSig
   * @return
   */
  public static Signature genMultiLayerThresholdSig(int num, Signature thresholdSig) {
    if (num == 1) {
		return thresholdSig;
	}
    SignatureList sigList = thresholdSig.getThresholdSignature().getSigs();
    Signature curr = thresholdSig;
    ThresholdSignature.Builder thresholdBuilder;
    for (int i = 2; i <= num; i++ ) {
      thresholdBuilder = ThresholdSignature.newBuilder();
      thresholdBuilder.setSigs(sigList.toBuilder().setSigs(0, curr));
      curr = Signature.newBuilder().setThresholdSignature(thresholdBuilder).build();
    }
    return curr;
  }
}
