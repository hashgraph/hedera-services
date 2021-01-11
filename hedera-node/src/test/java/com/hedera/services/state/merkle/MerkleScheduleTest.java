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
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class MerkleScheduleTest {
    final int TX_BYTES = 64;
    byte[] transactionBody, otherTransactionBody;
    EntityId payer, otherPayer;
    EntityId schedulingAccount, otherSchedulingAccount;
    RichInstant schedulingTXValidStart, otherSchedulingTXValidStart;
    JKey adminKey, otherKey;
    JKey signer1, signer2, signer3;
    Set<JKey> signers, otherSigners;

    boolean isDeleted = true, otherIsDeleted = false;

    MerkleSchedule subject;
    MerkleSchedule other;


    @BeforeEach
    public void setup() {
        transactionBody = TxnUtils.randomUtf8Bytes(TX_BYTES * 2);
        otherTransactionBody = TxnUtils.randomUtf8Bytes(TX_BYTES);

        payer = new EntityId(4, 5, 6);
        otherPayer = new EntityId(4, 5, 5);

        schedulingAccount = new EntityId(1, 2, 3);
        otherSchedulingAccount = new EntityId(1, 2, 2);

        schedulingTXValidStart = new RichInstant(123, 456);
        otherSchedulingTXValidStart = new RichInstant(456, 789);

        adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
        otherKey = new JEd25519Key("not-a-real-other-key".getBytes());

        signer1 = new JEd25519Key("not-a-real-signer-key-1".getBytes());
        signer2 = new JEd25519Key("not-a-real-signer-key-2".getBytes());
        signer3 = new JEd25519Key("not-a-real-signer-key-3".getBytes());

        signers = new LinkedHashSet<>();
        signers.add(signer1);
        signers.add(signer2);

        otherSigners = new LinkedHashSet<>();
        otherSigners.add(signer2);
        otherSigners.add(signer3);

        subject = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
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
        assertEquals(payer, subject.payer());
        assertEquals(schedulingAccount, subject.schedulingAccount());
        assertEquals(schedulingTXValidStart, subject.schedulingTXValidStart());
        assertTrue(subject.hasAdminKey());
        assertTrue(equalUpToDecodability(adminKey, subject.adminKey().get()));
        assertTrue(subject.signers().containsAll(signers));
        assertTrue(subject.hasPayer());
    }

    @Test
    public void validPutSigner() {
        var containsBefore = subject.signers().contains(signer3);
        // when:
        subject.addSigner(signer3);

        // expect:
        var containsAfter = subject.signers().contains(signer3);
        assertFalse(containsBefore);
        assertTrue(containsAfter);
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
        inOrder.verify(out).writeBoolean(isDeleted);
        inOrder.verify(out).writeInt(transactionBody.length);
        inOrder.verify(out).writeByteArray(transactionBody);
        inOrder.verify(serdes).writeNullableSerializable(payer, out);
        inOrder.verify(out).writeSerializable(schedulingAccount, true);
        inOrder.verify(out).writeLong(schedulingTXValidStart.getSeconds());
        inOrder.verify(out).writeInt(schedulingTXValidStart.getNanos());
        inOrder.verify(out).writeInt(signers.size());
        inOrder.verify(serdes).serializeKey(signer1, out);
        inOrder.verify(serdes).serializeKey(signer2, out);
        inOrder.verify(serdes).writeNullable(
                argThat(adminKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
    }

    @Test
    public void deserializeWorks() throws IOException {
        // setup:
        SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

        given(serdes.deserializeKey(fin))
                .willReturn(adminKey);
        given(serdes.readNullable(argThat(fin::equals), any(IoReadingFunction.class)))
                .willReturn(adminKey);
        given(fin.readBoolean())
                .willReturn(subject.isDeleted());
        given(fin.readLong())
                .willReturn(schedulingTXValidStart.getSeconds());
        given(fin.readInt())
                .willReturn(transactionBody.length)
                .willReturn(schedulingTXValidStart.getNanos())
                .willReturn(signers.size());
        given(fin.readByteArray(transactionBody.length))
                .willReturn(transactionBody);
        given(serdes.deserializeKey(fin))
                .willReturn(signer1)
                .willReturn(signer2);
        given(serdes.readNullableSerializable(any()))
                .willReturn(payer);
        given(fin.readSerializable())
                .willReturn(schedulingAccount);

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
        other = new MerkleSchedule(otherTransactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSchedulingAccount() {
        // given:
        other = new MerkleSchedule(transactionBody, otherSchedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSchedulingTxValidStart() {
        // given:
        other = new MerkleSchedule(transactionBody, schedulingAccount, otherSchedulingTXValidStart);
        setOptionalElements(other);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }


    @Test
    public void failDifferentSignersLength() {
        // given:
        other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // when:
        other.setSigners(new LinkedHashSet<>());

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentSigners() {
        // given:
        other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // when:
        other.setSigners(otherSigners);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentAdminKey() {
        // given:
        other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // when:
        other.setAdminKey(otherKey);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentPayer() {
        // given:
        other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // when:
        other.setPayer(otherPayer);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentIsDeleted() {
        // given:
        other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(other);

        // when:
        other.setDeleted(otherIsDeleted);

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
                    "payer=" + payer.toAbbrevString() + ", " +
                    "schedulingAccount=" + schedulingAccount + ", " +
                    "schedulingTXValidStart=" + schedulingTXValidStart + ", " +
                    "signers=[" + signersToString() + "], " +
                    "adminKey=" + describe(adminKey) + "}",
                subject.toString());
    }

    @Test
    public void validHashCode() {
        // given:
        var defaultSubject = new MerkleAccountState();
        // and:
        var identicalSubject = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
        setOptionalElements(identicalSubject);

        // and:
        other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);

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
        schedule.setSigners(signers);
        schedule.setPayer(payer);
        schedule.setDeleted(isDeleted);
        schedule.setAdminKey(adminKey);
    }

    private String signersToString() {
        return signers
                .stream()
                .map(MiscUtils::describe)
                .collect(Collectors.joining(", "));
    }
}
