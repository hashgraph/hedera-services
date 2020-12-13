package com.hedera.services.state.merkle;

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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.MerkleSchedule.SIGNATURE_BYTES;
import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class MerkleScheduleTest {
    byte[] otherSignature = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
    byte[] transactionBody, otherTransactionBody;
    int signersThreshold, otherSignersThreshold;
    JKey adminKey, otherKey;
    EntityId signer1, signer2, signer3;
    boolean signer1submission, signer2submission, signer3submission;
    byte[] signature1, signature2, signature3;
    Map<EntityId, Boolean> signers, otherSigners;
    Map<EntityId, byte[]> signatures, otherSignatures;

    boolean isDeleted = true, otherIsDeleted = false;

    MerkleSchedule subject;
    MerkleSchedule other;


    @BeforeEach
    public void setup() {
        transactionBody = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES * 2);
        otherTransactionBody = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);

        signersThreshold = 2;
        otherSignersThreshold = 3;

        adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
        otherKey = new JEd25519Key("not-a-real-other-key".getBytes());

        signature1 = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
        signature2 = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
        signature3 = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);

        signer1 = new EntityId(1, 2, 3);
        signer2 = new EntityId(2, 3, 4);
        signer3 = new EntityId(3, 4, 5);

        signers = new HashMap<>();
        signers.put(signer1, signer1submission);
        signers.put(signer2, signer2submission);

        otherSigners = new HashMap<>();
        otherSigners.put(signer2, signer2submission);
        otherSigners.put(signer3, signer3submission);

        signatures = new HashMap<>();
        signatures.put(signer1, signature1);
        signatures.put(signer2, signature2);
        otherSignatures = new HashMap<>();
        otherSignatures.put(signer2, signature2);
        otherSignatures.put(signer3, signature3);

        subject = new MerkleSchedule(transactionBody, signersThreshold, signers, signatures);
        this.setOptionalElements(subject);

        serdes = mock(DomainSerdes.class);
        MerkleSchedule.serdes = serdes;
    }

    @AfterEach
    public void cleanup() {
        MerkleSchedule.serdes = new DomainSerdes();
    }

    @Test
    public void deleteIsNoop() {
        // expect:
        assertDoesNotThrow(subject::release);
    }

    @Test
    public void validGetters() {
        // expect:
        assertEquals(transactionBody, subject.transactionBody());
        assertEquals(isDeleted, subject.isDeleted());
        assertEquals(signers, subject.signers());
        assertEquals(signersThreshold, subject.signersThreshold());
        assertTrue(subject.hasAdminKey());
        assertTrue(equalUpToDecodability(adminKey, subject.adminKey().get()));
        assertTrue(subject.signatures().containsKey(signer1));
        assertTrue(subject.signatures().containsKey(signer2));
        assertTrue(subject.signers().containsKey(signer1));
        assertTrue(subject.signers().containsKey(signer2));
    }

    @Test
    public void validPutSignature() {
        var oldSignature = subject.signatures().get(signer1);
        // when:
        subject.putSignature(signer1, otherSignature);

        // expect:
        var newSignature = subject.signatures().get(signer1);
        assertNotEquals(oldSignature, newSignature);
        assertEquals(otherSignature, newSignature);
    }

    @Test
    public void throwPutSignature() {
        // expect:
        assertThrows(IllegalArgumentException.class,
                () -> subject.putSignature(signer1, new byte[1]));
    }

    @Test
    public void serializeWorks() throws IOException {
        // setup:
        var out = mock(SerializableDataOutputStream.class);
        // and:
        InOrder inOrder = inOrder(serdes, out);

        // when:
        subject.serialize(out);

        // then:
        inOrder.verify(out).writeBoolean(true);
        inOrder.verify(out).writeInt(transactionBody.length);
        inOrder.verify(out).writeByteArray(transactionBody);
        inOrder.verify(out, times(2)).writeInt(signers.size());
        inOrder.verify(out).writeSerializable(signer1, true);
        inOrder.verify(out).writeBoolean(signer1submission);
        inOrder.verify(out).writeSerializable(signer2, true);
        inOrder.verify(out).writeBoolean(signer2submission);
        inOrder.verify(out).writeInt(signatures.size());
        inOrder.verify(out).writeSerializable(signer1, true);
        inOrder.verify(out).writeByteArray(signature1);
        inOrder.verify(out).writeSerializable(signer2, true);
        inOrder.verify(out).writeByteArray(signature2);
        inOrder.verify(serdes).writeNullable(
                argThat(adminKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
    }

    @Test
    public void deserializeWorks() throws IOException {
        // setup:
        SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

        given(serdes.deserializeKey(fin)).willReturn(adminKey);
        given(serdes.readNullable(argThat(fin::equals), any(IoReadingFunction.class)))
                .willReturn(adminKey);
        given(fin.readBoolean())
                .willReturn(subject.isDeleted())
                .willReturn(signer1submission)
                .willReturn(signer2submission);
        given(fin.readInt())
                .willReturn(transactionBody.length)
                .willReturn(signersThreshold)
                .willReturn(signers.size())
                .willReturn(signatures.size());
        given(fin.readByteArray(transactionBody.length))
                .willReturn(transactionBody);
        given(fin.readByteArray(SIGNATURE_BYTES))
                .willReturn(signature1)
                .willReturn(signature2);
        given(fin.readSerializable())
                .willReturn(signer1)
                .willReturn(signer2)
                .willReturn(signer1)
                .willReturn(signer2);
        System.out.println(transactionBody.toString());
        // and:
        var read = new MerkleSchedule();

        // when:
        read.deserialize(fin, MerkleToken.MERKLE_VERSION);

        // then:
        assertEquals(subject, read);
    }

    @Test
    public void failDifferentTransactionBody() {
        // given:
        other = new MerkleSchedule(otherTransactionBody, signersThreshold, signers, signatures);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSignersThreshold() {
        // given:
        other = new MerkleSchedule(transactionBody, otherSignersThreshold, signers, signatures);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }


    @Test
    public void failDifferentSignersLength() {
        // given:
        other = new MerkleSchedule(transactionBody, signersThreshold, new HashMap<>(), signatures);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSigners() {
        // given:
        other = new MerkleSchedule(transactionBody, signersThreshold, otherSigners, signatures);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentAdminKey() {
        // given:
        other = new MerkleSchedule(transactionBody, signersThreshold, signers, signatures);
        setOptionalElements(other);

        // when:
        other.setAdminKey(otherKey);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentIsDeleted() {
        // given:
        other = new MerkleSchedule(transactionBody, signersThreshold, signers, signatures);
        setOptionalElements(other);

        // when:
        other.setDeleted(otherIsDeleted);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSignaturesLength() {
        // given:
        other = new MerkleSchedule(transactionBody, signersThreshold, signers, new HashMap<>());
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSignaturesKeys() {
        // given:
        other = new MerkleSchedule(transactionBody, signersThreshold, signers, otherSignatures);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSignatures() {
        // given:
        otherSignatures = new HashMap<>();
        otherSignatures.put(signer1, signature1);
        otherSignatures.put(signer2, new byte[10]);
        // and:
        other = new MerkleSchedule(transactionBody, signersThreshold, signers, otherSignatures);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void validToString() {
        // expect:
        assertEquals("MerkleSchedule{" +
                    "deleted=" + isDeleted + ", " +
                    "transactionBody=" + hex(transactionBody) + ", " +
                    "signersThreshold=" + signersThreshold + ", " +
                    "adminKey=" + describe(adminKey) + ", " +
                    "signers=[" + signersToString() + "], " +
                    "signatures=[" + signaturesToString() +
                    "]}",
                subject.toString());
    }

    @Test
    public void validHashCode() {
        // given:
        var defaultSubject = new MerkleAccountState();
        // and:
        var identicalSubject = new MerkleSchedule(transactionBody, signersThreshold, signers, signatures);
        setOptionalElements(identicalSubject);
        identicalSubject.setDeleted(isDeleted);

        // and:
        other = new MerkleSchedule(transactionBody, signersThreshold, signers, signatures);

        // expect:
        assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertEquals(subject.hashCode(), identicalSubject.hashCode());
    }

    @Test
    public void validEqualityChecks() {
        // expect:
        assertEquals(subject, subject);
        // and:
        assertNotEquals(subject, null);
        // and:
        assertNotEquals(subject, new Object());
    }

    @Test
    public void validVersion() {
        // expect:
        assertEquals(MerkleSchedule.MERKLE_VERSION, subject.getVersion());
    }

    @Test
    public void validRuntimeConstructableID() {
        // expect:
        assertEquals(MerkleSchedule.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    public void validIsLeaf() {
        // expect:
        assertTrue(subject.isLeaf());
    }

    @Test
    public void throwsLegacyProvider() {
        // expect:
        assertThrows(UnsupportedOperationException.class,
                () -> MerkleSchedule.LEGACY_PROVIDER.deserialize(null));
    }

    @Test
    public void validCopy() {
        // given:
        var copySubject = subject.copy();

        // expect:
        assertNotSame(copySubject, subject);
        assertEquals(subject, copySubject);
    }

    private void setOptionalElements(MerkleSchedule schedule) {
        schedule.setDeleted(isDeleted);
        schedule.setAdminKey(adminKey);
    }

    private String signersToString() {
        return signers
                .entrySet()
                .stream()
                .map(s -> s.getKey() + " : " + s.getValue())
                .collect(Collectors.joining(", "));
    }

    private String signaturesToString() {
        return signatures
                .entrySet()
                .stream()
                .map(s -> s.getKey() + " : " + hex(s.getValue()))
                .collect(Collectors.joining(", "));
    }
}
