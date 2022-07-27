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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JKeyListTest {
    @Test
    void requiresNonNullKeys() {
        // expect:
        Assertions.assertThrows(IllegalArgumentException.class, () -> new JKeyList(null));
    }

    @Test
    void emptyWaclSerdeWorks() throws IOException {
        // given:
        byte[] repr = JKeySerializer.serialize(StateView.EMPTY_WACL);

        // when:
        JKey recovered =
                JKeySerializer.deserialize(
                        new SerializableDataInputStream(new ByteArrayInputStream(repr)));

        // then:
        assertTrue(recovered.isEmpty());
    }

    @Test
    void defaultConstructor() {
        final var cut = new JKeyList();

        assertEquals(0, cut.getKeysList().size());
    }

    @Test
    void isEmptySubkeys() {
        final var cut = new JKeyList(List.of(new JEd25519Key(new byte[0])));

        assertTrue(cut.isEmpty());
    }

    @Test
    void isNotEmpty() {
        final var cut = new JKeyList(List.of(new JECDSA_384Key(new byte[1])));

        assertFalse(cut.isEmpty());
    }

    @Test
    void invalidJKeyListTest() throws Exception {
        Key validED25519Key =
                Key.newBuilder()
                        .setEd25519(TxnUtils.randomUtf8ByteString(JEd25519Key.ED25519_BYTE_LENGTH))
                        .build();

        Key invalidECDSAsecp256k1Key = randomInvalidECDSASecp256K1Key();
        KeyList invalidKeyList1 = KeyList.newBuilder().build();
        Key invalidKey1 = Key.newBuilder().setKeyList(invalidKeyList1).build();
        KeyList invalidKeyList2 =
                KeyList.newBuilder().addKeys(validED25519Key).addKeys(invalidKey1).build();
        Key invalidKey2 = Key.newBuilder().setKeyList(invalidKeyList2).build();
        KeyList invalidKeyList3 =
                KeyList.newBuilder().addKeys(validED25519Key).addKeys(invalidKey2).build();
        Key invalidKey3 = Key.newBuilder().setKeyList(invalidKeyList3).build();
        KeyList invalidKeyList4 =
                KeyList.newBuilder().addKeys(invalidECDSAsecp256k1Key).addKeys(invalidKey3).build();
        Key invalidKey4 = Key.newBuilder().setKeyList(invalidKeyList4).build();

        JKey jKeyList1 = JKey.convertKey(invalidKey1, 1);
        assertFalse(jKeyList1.isValid());

        JKey jKeyList2 = JKey.convertKey(invalidKey2, 1);
        assertFalse(jKeyList2.isValid());

        JKey jKeyList3 = JKey.convertKey(invalidKey3, 1);
        assertFalse(jKeyList3.isValid());

        JKey jKeyList4 = JKey.convertKey(invalidKey4, 1);
        assertFalse(jKeyList4.isValid());
    }

    @Test
    void validJKeyListTest() throws Exception {
        Key validED25519Key =
                Key.newBuilder()
                        .setEd25519(TxnUtils.randomUtf8ByteString(JEd25519Key.ED25519_BYTE_LENGTH))
                        .build();
        Key validECDSAsecp256k1Key = randomValidECDSASecp256K1Key();
        Key validECDSA384Key =
                Key.newBuilder().setECDSA384(TxnUtils.randomUtf8ByteString(24)).build();
        KeyList validKeyList1 =
                KeyList.newBuilder().addKeys(validECDSA384Key).addKeys(validED25519Key).build();
        Key validKey1 = Key.newBuilder().setKeyList(validKeyList1).build();
        KeyList validKeyList2 =
                KeyList.newBuilder().addKeys(validED25519Key).addKeys(validKey1).build();
        Key validKey2 = Key.newBuilder().setKeyList(validKeyList2).build();
        KeyList validKeyList3 =
                KeyList.newBuilder().addKeys(validED25519Key).addKeys(validKey2).build();
        Key validKey3 = Key.newBuilder().setKeyList(validKeyList3).build();
        KeyList validKeyList4 =
                KeyList.newBuilder().addKeys(validECDSAsecp256k1Key).addKeys(validKey3).build();
        Key validKey4 = Key.newBuilder().setKeyList(validKeyList4).build();

        JKey jKeyList1 = JKey.convertKey(validKey1, 1);
        assertTrue(jKeyList1.isValid());

        JKey jKeyList2 = JKey.convertKey(validKey2, 1);
        assertTrue(jKeyList2.isValid());

        JKey jKeyList3 = JKey.convertKey(validKey3, 1);
        assertTrue(jKeyList3.isValid());

        JKey jKeyList4 = JKey.convertKey(validKey4, 1);
        assertTrue(jKeyList4.isValid());
    }

    @Test
    void requiresAnExplicitScheduledChild() {
        // setup:
        var ed25519Key = new JEd25519Key("ed25519".getBytes());
        var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        var contractKey = new JContractIDKey(0, 0, 75231);
        var ecdsasecp256k1Key = new JECDSASecp256k1Key("ecdsasecp256k1".getBytes());
        // and:
        List<JKey> keys =
                List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey, ecdsasecp256k1Key);

        // given:
        var subject = new JKeyList(keys);
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
    void propagatesScheduleScope() {
        // setup:
        var ed25519Key = new JEd25519Key("ed25519".getBytes());
        var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        var contractKey = new JContractIDKey(0, 0, 75231);
        // and:
        List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey);

        // given:
        var subject = new JKeyList(keys);

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

    public static Key randomValidECDSASecp256K1Key() {
        ByteString edcsaSecp256K1Bytes =
                ByteString.copyFrom(new byte[] {0x02})
                        .concat(
                                TxnUtils.randomUtf8ByteString(
                                        JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH
                                                - 1));
        return Key.newBuilder().setECDSASecp256K1(edcsaSecp256K1Bytes).build();
    }

    public static Key randomInvalidECDSASecp256K1Key() {
        ByteString edcsaSecp256K1Bytes =
                ByteString.copyFrom(new byte[] {0x06})
                        .concat(
                                TxnUtils.randomUtf8ByteString(
                                        JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH
                                                - 1));
        return Key.newBuilder().setECDSASecp256K1(edcsaSecp256K1Bytes).build();
    }
}
