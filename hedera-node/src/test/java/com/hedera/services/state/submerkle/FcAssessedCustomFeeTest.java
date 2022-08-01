/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.FcAssessedCustomFee.assessedHbarFeeFrom;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcAssessedCustomFeeTest {
    private final EntityId account = new EntityId(4, 5, 6);
    private final long units = -1_234L;
    private final EntityId token = new EntityId(1, 2, 3);
    private final long[] effectivePayers = new long[] {1234L, 4321L};

    @Mock private SerializableDataInputStream din;
    @Mock private SerializableDataOutputStream dos;

    @Test
    void objectContractSanityChecks() {
        // given:
        final var hbarChange =
                assessedHbarFeeFrom(
                        IdUtils.adjustFrom(account.toGrpcAccountId(), units), effectivePayers);
        final var tokenChange =
                FcAssessedCustomFee.assessedHtsFeeFrom(
                        token,
                        IdUtils.adjustFrom(account.toGrpcAccountId(), units),
                        effectivePayers);
        // and:
        final var hbarRepr =
                "FcAssessedCustomFee{token=ℏ, account=EntityId{shard=4, realm=5, num=6},"
                        + " units=-1234, effective payer accounts=[1234, 4321]}";
        final var tokenRepr =
                "FcAssessedCustomFee{token=EntityId{shard=1, realm=2, num=3},"
                        + " account=EntityId{shard=4, realm=5, num=6}, units=-1234, effective payer"
                        + " accounts=[1234, 4321]}";

        // expect:
        assertNotEquals(hbarChange, tokenChange);
        assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
        // and:
        assertEquals(hbarRepr, hbarChange.toString());
        assertEquals(tokenRepr, tokenChange.toString());
        // and:
        assertEquals(account, hbarChange.account());
        assertEquals(units, hbarChange.units());
        assertEquals(token, tokenChange.token());
    }

    @Test
    void liveFireSerdeWorksForHtsFeeCurrentVersion()
            throws IOException, ConstructableRegistryException {
        // setup:
        final var account = new EntityId(1, 2, 3);
        final var token = new EntityId(2, 3, 4);
        final var amount = 345L;
        final var subject = new FcAssessedCustomFee(account, token, amount, effectivePayers);
        // and:
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(EntityId.class, EntityId::new));
        // and:
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);

        // given:
        subject.serialize(dos);
        dos.flush();
        // and:
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        // when:
        final var newSubject = new FcAssessedCustomFee();
        newSubject.deserialize(din, FcAssessedCustomFee.CURRENT_VERSION);

        // then:
        assertEquals(subject, newSubject);
    }

    @Test
    void liveFireSerdeWorksForHtsFee0170Version()
            throws IOException, ConstructableRegistryException {
        // setup:
        final var account = new EntityId(1, 2, 3);
        final var token = new EntityId(2, 3, 4);
        final var amount = 345L;
        final var subject =
                new FcAssessedCustomFee(
                        account,
                        token,
                        amount,
                        FcAssessedCustomFee.UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS);
        // and:
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(EntityId.class, EntityId::new));
        // and:
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);

        // given:
        subject.serialize(dos);
        dos.flush();
        // and:
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        // when:
        final var newSubject = new FcAssessedCustomFee();
        newSubject.deserialize(din, FcAssessedCustomFee.RELEASE_0170_VERSION);

        // then:
        assertEquals(subject, newSubject);
    }

    @Test
    void liveFireSerdeWorksForHbarFeeCurrentVersion()
            throws IOException, ConstructableRegistryException {
        // setup:
        final var account = new EntityId(1, 2, 3);
        final var amount = 345L;
        final var subject = new FcAssessedCustomFee(account, amount, effectivePayers);
        // and:
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(EntityId.class, EntityId::new));
        // and:
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);

        // given:
        subject.serialize(dos);
        dos.flush();
        // and:
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        // when:
        final var newSubject = new FcAssessedCustomFee();
        newSubject.deserialize(din, FcAssessedCustomFee.CURRENT_VERSION);

        // then:
        assertEquals(subject, newSubject);
    }

    @Test
    void liveFireSerdeWorksForHbarFee0170Version()
            throws IOException, ConstructableRegistryException {
        // setup:
        final var account = new EntityId(1, 2, 3);
        final var amount = 345L;
        final var subject =
                new FcAssessedCustomFee(
                        account, amount, FcAssessedCustomFee.UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS);
        // and:
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(EntityId.class, EntityId::new));
        // and:
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);

        // given:
        subject.serialize(dos);
        dos.flush();
        // and:
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        // when:
        final var newSubject = new FcAssessedCustomFee();
        newSubject.deserialize(din, FcAssessedCustomFee.RELEASE_0170_VERSION);

        // then:
        assertEquals(subject, newSubject);
    }

    @Test
    void serializeWorksAsExpected() throws IOException {
        // setup:
        InOrder inOrder = Mockito.inOrder(dos);

        // given:
        final var subject = new FcAssessedCustomFee(account, token, units, effectivePayers);

        // when:
        subject.serialize(dos);

        // then:
        inOrder.verify(dos).writeSerializable(account, true);
        inOrder.verify(dos).writeSerializable(token, true);
        inOrder.verify(dos).writeLong(units);
        inOrder.verify(dos).writeLongArray(effectivePayers);
    }

    @Test
    void deserializeWorksAsExpectedFor0170() throws IOException {
        // setup:
        final var expectedBalanceChange =
                new FcAssessedCustomFee(
                        account,
                        token,
                        units,
                        FcAssessedCustomFee.UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS);

        given(din.readSerializable()).willReturn(account).willReturn(token);
        given(din.readLong()).willReturn(units);

        // given:
        final var subject = new FcAssessedCustomFee();

        // when:
        subject.deserialize(din, FcAssessedCustomFee.RELEASE_0170_VERSION);

        // then:
        assertNotNull(subject);
        assertEquals(expectedBalanceChange.account(), subject.account());
        assertEquals(expectedBalanceChange.token(), subject.token());
        assertEquals(expectedBalanceChange.units(), subject.units());
        assertSame(expectedBalanceChange.effPayerAccountNums(), subject.effPayerAccountNums());
    }

    @Test
    void deserializeWorksAsExpectedFor0171() throws IOException {
        // setup:
        final var expectedBalanceChange =
                new FcAssessedCustomFee(account, token, units, effectivePayers);

        given(din.readSerializable()).willReturn(account).willReturn(token);
        given(din.readLong()).willReturn(units);
        given(din.readLongArray(Integer.MAX_VALUE)).willReturn(effectivePayers);

        // given:
        final var subject = new FcAssessedCustomFee();

        // when:
        subject.deserialize(din, FcAssessedCustomFee.RELEASE_0171_VERSION);

        // then:
        assertNotNull(subject);
        assertEquals(expectedBalanceChange.account(), subject.account());
        assertEquals(expectedBalanceChange.token(), subject.token());
        assertEquals(expectedBalanceChange.units(), subject.units());
        assertSame(expectedBalanceChange.effPayerAccountNums(), subject.effPayerAccountNums());
    }

    @Test
    void gettersWork() {
        // given
        final var subject = new FcAssessedCustomFee(account, token, units, effectivePayers);
        // expect:
        assertEquals(account, subject.account());
        assertEquals(token, subject.token());
        assertEquals(units, subject.units());
    }

    @Test
    void recognizesIfForHbar() {
        // given:
        final var hbarChange =
                FcAssessedCustomFee.assessedHbarFeeFrom(
                        IdUtils.adjustFrom(account.toGrpcAccountId(), units), effectivePayers);
        final var tokenChange =
                FcAssessedCustomFee.assessedHtsFeeFrom(
                        token,
                        IdUtils.adjustFrom(account.toGrpcAccountId(), units),
                        effectivePayers);

        assertTrue(hbarChange.isForHbar());
        assertFalse(tokenChange.isForHbar());
    }

    @Test
    void testToGrpc() {
        // given:
        final var subject = new FcAssessedCustomFee(account, token, units, effectivePayers);
        // then:
        AssessedCustomFee grpc = subject.toGrpc();

        // expect:
        assertEquals(subject.account().toGrpcAccountId(), grpc.getFeeCollectorAccountId());
        assertEquals(subject.token().toGrpcTokenId(), grpc.getTokenId());
        assertEquals(subject.units(), grpc.getAmount());
        assertArrayEquals(
                effectivePayers,
                grpc.getEffectivePayerAccountIdList().stream()
                        .mapToLong(account -> account.getAccountNum())
                        .toArray());
    }

    @Test
    void testToGrpcForHbar() {
        // given:
        final var subject = new FcAssessedCustomFee(account, units, effectivePayers);
        // then:
        AssessedCustomFee grpc = subject.toGrpc();

        // expect:
        assertEquals(subject.account().toGrpcAccountId(), grpc.getFeeCollectorAccountId());
        assertFalse(grpc.hasTokenId());
        assertEquals(subject.units(), grpc.getAmount());
        assertArrayEquals(
                effectivePayers,
                grpc.getEffectivePayerAccountIdList().stream()
                        .mapToLong(account -> account.getAccountNum())
                        .toArray());
    }

    @Test
    void testFromGrpc() {
        // given:
        final var grpc =
                AssessedCustomFee.newBuilder()
                        .setTokenId(token.toGrpcTokenId())
                        .setFeeCollectorAccountId(account.toGrpcAccountId())
                        .addEffectivePayerAccountId(
                                AccountID.newBuilder().setAccountNum(effectivePayers[0]))
                        .addEffectivePayerAccountId(
                                AccountID.newBuilder().setAccountNum(effectivePayers[1]))
                        .setAmount(units)
                        .build();

        // expect:
        final var fcFee = FcAssessedCustomFee.fromGrpc(grpc);
        assertEquals(account, fcFee.account());
        assertEquals(token, fcFee.token());
        assertEquals(units, fcFee.units());
        assertArrayEquals(effectivePayers, fcFee.effPayerAccountNums());
    }

    @Test
    void testFromGrpcFails() {
        // given:
        final var grpc =
                AssessedCustomFee.newBuilder()
                        .setTokenId(token.toGrpcTokenId())
                        .setFeeCollectorAccountId(account.toGrpcAccountId())
                        .setAmount(units)
                        .build();

        // expect:
        final var fcFee = FcAssessedCustomFee.fromGrpc(grpc);
        assertEquals(account, fcFee.account());
        assertEquals(token, fcFee.token());
        assertEquals(units, fcFee.units());
        assertArrayEquals(new long[0], fcFee.effPayerAccountNums());
    }

    @Test
    void testFromGrpcForHbarAdjust() {
        // given:
        final var grpc =
                AssessedCustomFee.newBuilder()
                        .setFeeCollectorAccountId(account.toGrpcAccountId())
                        .addEffectivePayerAccountId(
                                AccountID.newBuilder().setAccountNum(effectivePayers[0]))
                        .addEffectivePayerAccountId(
                                AccountID.newBuilder().setAccountNum(effectivePayers[1]))
                        .setAmount(units)
                        .build();

        // expect:
        final var fcFee = FcAssessedCustomFee.fromGrpc(grpc);
        assertEquals(account, fcFee.account());
        assertEquals(null, fcFee.token());
        assertEquals(units, fcFee.units());
        assertArrayEquals(effectivePayers, fcFee.effPayerAccountNums());
    }

    @Test
    void merkleMethodsWork() {
        // given:
        final var subject = new FcAssessedCustomFee();

        assertEquals(FcAssessedCustomFee.CURRENT_VERSION, subject.getVersion());
        assertEquals(FcAssessedCustomFee.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }
}
