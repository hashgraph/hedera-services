/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.legacy.core.jproto;

import static com.hedera.services.legacy.core.jproto.JKeyListTest.randomValidECDSASecp256K1Key;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class JThresholdKeyTest {
    @Test
    void isEmpty() {
        final var cut = new JThresholdKey(new JKeyList(), 0);

        assertTrue(cut.isEmpty());
    }

    @Test
    void isEmptySubkeys() {
        final var cut = new JThresholdKey(new JKeyList(List.of(new JEd25519Key(new byte[0]))), 1);

        assertTrue(cut.isEmpty());

        final var cut1 =
                new JThresholdKey(new JKeyList(List.of(new JECDSASecp256k1Key(new byte[0]))), 1);

        assertTrue(cut1.isEmpty());
    }

    @Test
    void isNotEmpty() {
        final var cut = new JThresholdKey(new JKeyList(List.of(new JEd25519Key(new byte[1]))), 1);

        assertFalse(cut.isEmpty());
    }

    private Key thresholdKey(KeyList keyList, int threshold) {
        return Key.newBuilder()
                .setThresholdKey(ThresholdKey.newBuilder().setKeys(keyList).setThreshold(threshold))
                .build();
    }

    private JKey jThresholdKey(KeyList keyList, int threshold) throws Exception {
        return JKey.convertKey(thresholdKey(keyList, threshold), 1);
    }

    @Test
    void JThresholdKeyWithVariousThresholdTest() throws Exception {
        Key validContractIDKey =
                Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(1L).build())
                        .build();
        Key validRSA3072Key =
                Key.newBuilder().setRSA3072(TxnUtils.randomUtf8ByteString(16)).build();
        KeyList validKeyList =
                KeyList.newBuilder().addKeys(validContractIDKey).addKeys(validRSA3072Key).build();

        assertFalse(jThresholdKey(validKeyList, 0).isValid());
        assertTrue(jThresholdKey(validKeyList, 1).isValid());
        assertTrue(jThresholdKey(validKeyList, 2).isValid());
        assertFalse(jThresholdKey(validKeyList, 3).isValid());
    }

    @Test
    void invalidJThresholdKeyTest() throws Exception {
        Key validED25519Key =
                Key.newBuilder()
                        .setEd25519(TxnUtils.randomUtf8ByteString(JEd25519Key.ED25519_BYTE_LENGTH))
                        .build();
        Key validECDSA384Key =
                Key.newBuilder().setECDSA384(TxnUtils.randomUtf8ByteString(24)).build();
        Key validECDSASecp256Key = randomValidECDSASecp256K1Key();

        KeyList invalidKeyList1 = KeyList.newBuilder().build();
        Key invalidKey1 = thresholdKey(invalidKeyList1, 1);
        KeyList invalidKeyList2 =
                KeyList.newBuilder().addKeys(validED25519Key).addKeys(invalidKey1).build();
        Key invalidKey2 = thresholdKey(invalidKeyList2, 2);
        KeyList invalidKeyList3 =
                KeyList.newBuilder().addKeys(validECDSA384Key).addKeys(invalidKey2).build();
        Key invalidKey3 = thresholdKey(invalidKeyList2, 2);
        KeyList invalidKeyList4 =
                KeyList.newBuilder().addKeys(validECDSASecp256Key).addKeys(invalidKey3).build();

        JKey jThresholdKey1 = JKey.convertKey(invalidKey1, 1);
        assertFalse(jThresholdKey1.isValid());

        JKey jThresholdKey2 = JKey.convertKey(invalidKey2, 1);
        assertFalse(jThresholdKey2.isValid());

        assertFalse(jThresholdKey(invalidKeyList3, 1).isValid());
        assertFalse(jThresholdKey(invalidKeyList4, 1).isValid());
    }

    @Test
    void degenerateKeyNotForScheduledTxn() {
        // given:
        var subject = new JThresholdKey(null, 0);

        // expect:
        assertFalse(subject.isForScheduledTxn());
    }

    @Test
    void delegatesScheduledScope() {
        // setup:
        var ed25519Key = new JEd25519Key("ed25519".getBytes());
        var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        var ecdsasecp256k1Key = new JECDSASecp256k1Key("ecdsasecp256k1".getBytes());
        var contractKey = new JContractIDKey(0, 0, 75231);
        // and:
        List<JKey> keys =
                List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey, ecdsasecp256k1Key);
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
    void propagatesSettingScheduledScope() {
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
