package com.hedera.services.legacy.core.jproto;

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

import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JThresholdKeyTest {
  @Test
  public void isEmpty() {
    final var cut = new JThresholdKey(new JKeyList(), 0);

    assertTrue(cut.isEmpty());
  }

  @Test
  public void isEmptySubkeys() {
    final var cut = new JThresholdKey(new JKeyList(List.of(new JEd25519Key(new byte[0]))), 1);

    assertTrue(cut.isEmpty());
  }

  @Test
  public void isNotEmpty() {
    final var cut = new JThresholdKey(new JKeyList(List.of(new JEd25519Key(new byte[1]))), 1);

    assertFalse(cut.isEmpty());
  }

  private Key thresholdKey(KeyList keyList, int threshold) {
    return Key.newBuilder().setThresholdKey(
            ThresholdKey.newBuilder().setKeys(keyList).setThreshold(threshold)
    ).build();
  }

  private JKey jThresholdKey(KeyList keyList, int threshold) throws Exception {
    return JKey.convertKey(thresholdKey(keyList, threshold), 1);
  }

  @Test
  public void JThresholdKeyWithVariousThresholdTest() throws Exception {
    Key validContractIDKey = Key.newBuilder().setContractID(
            ContractID.newBuilder().setContractNum(1L).build()
    ).build();
    Key validRSA3072Key = Key.newBuilder().setRSA3072(
            TxnUtils.randomUtf8ByteString(16)
    ).build();
    KeyList validKeyList = KeyList.newBuilder().addKeys(validContractIDKey).addKeys(validRSA3072Key).build();

    assertFalse(jThresholdKey(validKeyList, 0).isValid());
    assertTrue(jThresholdKey(validKeyList, 1).isValid());
    assertTrue(jThresholdKey(validKeyList, 2).isValid());
    assertFalse(jThresholdKey(validKeyList, 3).isValid());
  }

  @Test
  public void invalidJThresholdKeyTest() throws Exception {
    Key validED25519Key = Key.newBuilder().setEd25519(
            TxnUtils.randomUtf8ByteString(JEd25519Key.ED25519_BYTE_LENGTH)
    ).build();
    Key validECDSA384Key = Key.newBuilder().setECDSA384(
            TxnUtils.randomUtf8ByteString(24)
    ).build();
    KeyList invalidKeyList1 = KeyList.newBuilder().build();
    Key invalidKey1 = thresholdKey(invalidKeyList1, 1);
    KeyList invalidKeyList2 = KeyList.newBuilder().addKeys(validED25519Key).addKeys(invalidKey1).build();
    Key invalidKey2 = thresholdKey(invalidKeyList2, 2);
    KeyList invalidKeyList3 = KeyList.newBuilder().addKeys(validECDSA384Key).addKeys(invalidKey2).build();

    JKey jThresholdKey1 = JKey.convertKey(invalidKey1, 1);
    assertFalse(jThresholdKey1.isValid());

    JKey jThresholdKey2 = JKey.convertKey(invalidKey2, 1);
    assertFalse(jThresholdKey2.isValid());

    assertFalse(jThresholdKey(invalidKeyList3, 1).isValid());
  }

  @Test
  public void delegatesScheduledScope() {
    // setup:
    var ed25519Key = new JEd25519Key("ed25519".getBytes());
    var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
    var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
    var contractKey = new JContractIDKey(0, 0, 75231);
    // and:
    List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey);
    var delegate = new JKeyList(keys);

    // given:
    var subject = new JThresholdKey(delegate, 1);
    // and:
    assertFalse(subject.isForScheduledTxn());

    // expect:
    for (JKey key : keys) {
      key.setForScheduledTxn(true);
      assertTrue(subject.isForScheduledTxn());
      key.setForScheduledTxn(false);
    }
  }

  @Test
  public void propagatesSettingScheduledScope() {
    // setup:
    var ed25519Key = new JEd25519Key("ed25519".getBytes());
    var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
    var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
    var contractKey = new JContractIDKey(0, 0, 75231);
    // and:
    List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey);

    // given:
    var subject = new JThresholdKey(new JKeyList(keys), 1);

    // when:
    subject.setForScheduledTxn(true);
    // then:
    for (JKey key : keys) {
      assertTrue(key.isForScheduledTxn());
    }
    // and when:
    subject.setForScheduledTxn(false);
    // then:
    assertFalse(subject.isForScheduledTxn());
  }
}
