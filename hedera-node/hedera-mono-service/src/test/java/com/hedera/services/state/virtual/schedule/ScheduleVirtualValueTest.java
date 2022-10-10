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
package com.hedera.services.state.virtual.schedule;

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.describe;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ScheduleVirtualValueTest {

    private static final byte[] fpk = "firstPretendKey".getBytes();
    private static final byte[] spk = "secondPretendKey".getBytes();
    private static final byte[] tpk = "thirdPretendKey".getBytes();
    private static final RichInstant expiry = new RichInstant(1_234_567L, 321);
    private static final RichInstant providedExpiry = new RichInstant(1_234_567L, 321);
    private static final boolean waitForExpiry = true;
    private static final String entityMemo = "Just some memo again";
    private static final String otherEntityMemo = "Yet another memo";
    private static final EntityId payer = new EntityId(4, 5, 6);
    private static final EntityId otherPayer = new EntityId(4, 5, 5);
    private static final EntityId schedulingAccount = new EntityId(1, 2, 3);
    private static final Instant resolutionTime = Instant.ofEpochSecond(1_234_567L);
    private static final Timestamp grpcResolutionTime =
            RichInstant.fromJava(resolutionTime).toGrpc();
    private static final RichInstant schedulingTXValidStart = new RichInstant(123, 456);
    private static final RichInstant otherSchedulingTXValidStart = new RichInstant(456, 789);
    private static final JKey adminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
    private static final JKey otherAdminKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();

    private List<byte[]> signatories;
    private ScheduleVirtualValue subject;

    @BeforeEach
    void setup() {
        signatories = new ArrayList<>();
        signatories.addAll(List.of(fpk, spk, tpk));

        subject = ScheduleVirtualValue.from(bodyBytes, expiry);
        subject.setKey(new EntityNumVirtualKey(3L));
    }

    @Test
    void serializeWorks() throws Exception {
        checkSerialize(
                () -> {
                    final var byteArr = new ByteArrayOutputStream();
                    final var out = new SerializableDataOutputStream(byteArr);
                    subject.serialize(out);

                    var copy = new ScheduleVirtualValue();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(byteArr.toByteArray())),
                            ScheduleVirtualValue.CURRENT_VERSION);

                    assertEqualSchedules(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeWithByteBufferWorks() throws Exception {
        checkSerialize(
                () -> {
                    final var buffer = ByteBuffer.allocate(100000);
                    subject.serialize(buffer);
                    buffer.rewind();
                    var copy = new ScheduleVirtualValue();
                    copy.deserialize(buffer, ScheduleVirtualValue.CURRENT_VERSION);

                    assertEqualSchedules(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeWithMixedWorksBytesFirst() throws Exception {
        checkSerialize(
                () -> {
                    final var buffer = ByteBuffer.allocate(100000);
                    subject.serialize(buffer);

                    var copy = new ScheduleVirtualValue();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(buffer.array())),
                            ScheduleVirtualValue.CURRENT_VERSION);

                    assertEqualSchedules(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeWithMixedWorksBytesSecond() throws Exception {
        checkSerialize(
                () -> {
                    final var byteArr = new ByteArrayOutputStream();
                    final var out = new SerializableDataOutputStream(byteArr);
                    subject.serialize(out);

                    final var buffer = ByteBuffer.wrap(byteArr.toByteArray());
                    var copy = new ScheduleVirtualValue();
                    copy.deserialize(buffer, ScheduleVirtualValue.CURRENT_VERSION);

                    assertEqualSchedules(subject, copy);

                    return copy;
                });
    }

    private void checkSerialize(Callable<ScheduleVirtualValue> check) throws Exception {
        subject.setCalculatedExpirationTime(null);
        subject.setCalculatedWaitForExpiry(false);
        assertNull(subject.calculatedExpirationTime());
        assertNull(subject.getResolutionTime());
        assertFalse(subject.isExecuted());
        assertFalse(subject.isDeleted());
        assertFalse(subject.calculatedWaitForExpiry());
        assertTrue(subject.waitForExpiryProvided());
        assertEquals(0, subject.signatories().size());

        check.call();

        var origSubject = subject;

        subject.setCalculatedExpirationTime(expiry);
        subject.setCalculatedWaitForExpiry(true);

        var copy = check.call();

        copy.markDeleted(Instant.now());
        subject.markExecuted(Instant.now());

        subject = copy;
        check.call();
        subject = origSubject;
        check.call();

        subject.witnessValidSignature(fpk);
        check.call();
        subject.witnessValidSignature(tpk);
        check.call();
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(ScheduleVirtualValue.CURRENT_VERSION, subject.getVersion());
        assertEquals(ScheduleVirtualValue.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    public static void assertEqualSchedules(
            final ScheduleVirtualValue a, final ScheduleVirtualValue b) {
        assertEquals(a.calculatedExpirationTime(), b.calculatedExpirationTime());
        assertArrayEquals(a.bodyBytes(), b.bodyBytes());
        assertEquals(a.isDeleted(), b.isDeleted());
        assertEquals(a.isExecuted(), b.isExecuted());
        assertEquals(a.calculatedWaitForExpiry(), b.calculatedWaitForExpiry());
        assertEquals(a.getResolutionTime(), b.getResolutionTime());
        final var aSigs = a.signatories();
        final var bSigs = b.signatories();
        assertEquals(aSigs.size(), bSigs.size());
        for (int i = 0, n = aSigs.size(); i < n; i++) {
            assertArrayEquals(aSigs.get(i), bSigs.get(i));
        }
    }

    @Test
    void recognizesMissingFunction() {
        final var noneBodyBytes =
                parentTxn.toBuilder()
                        .setScheduleCreate(ScheduleCreateTransactionBody.getDefaultInstance())
                        .build()
                        .toByteArray();

        subject = ScheduleVirtualValue.from(noneBodyBytes, expiry);

        assertEquals(HederaFunctionality.NONE, subject.scheduledFunction());
    }

    @Test
    void factoryWorks() {
        assertFalse(subject.isDeleted());
        assertFalse(subject.isExecuted());
        assertEquals(payer, subject.payer());
        assertEquals(expiry, subject.calculatedExpirationTime());
        assertEquals(waitForExpiry, subject.calculatedWaitForExpiry());
        assertEquals(waitForExpiry, subject.waitForExpiryProvided());
        assertEquals(providedExpiry, subject.expirationTimeProvided());
        assertEquals(schedulingAccount, subject.schedulingAccount());
        assertEquals(entityMemo, subject.memo().get());
        assertEquals(adminKey.toString(), subject.adminKey().get().toString());
        assertEquals(schedulingTXValidStart, subject.schedulingTXValidStart());
        assertEquals(scheduledTxn, subject.scheduledTxn());
        assertEquals(ordinaryVersionOfScheduledTxn, subject.ordinaryViewOfScheduledTxn());
        assertEquals(expectedSignedTxn(), subject.asSignedTxn());
        assertArrayEquals(bodyBytes, subject.bodyBytes());
        assertEquals(HederaFunctionality.CryptoDelete, subject.scheduledFunction());
    }

    @Test
    void factoryTranslatesImpossibleParseError() {
        final var bytes = "NONSENSE".getBytes();
        final var iae =
                assertThrows(
                        IllegalArgumentException.class, () -> ScheduleVirtualValue.from(bytes, 0L));
        assertEquals(
                "Argument bodyBytes=0x4e4f4e53454e5345 was not a TransactionBody!",
                iae.getMessage());
    }

    @Test
    void translatesInvariantFailure() {
        subject = new ScheduleVirtualValue();

        assertThrows(IllegalStateException.class, subject::scheduledTransactionId);

        subject.setSchedulingAccount(schedulingAccount);

        assertThrows(IllegalStateException.class, subject::scheduledTransactionId);
    }

    @Test
    void understandsSchedulerIsFallbackPayer() {
        assertEquals(subject.payer(), subject.effectivePayer());

        subject.setPayer(null);

        assertEquals(subject.schedulingAccount(), subject.effectivePayer());
    }

    @Test
    void checksResolutionAsExpected() {
        assertThrows(IllegalStateException.class, subject::deletionTime);
        assertThrows(IllegalStateException.class, subject::executionTime);

        subject.markExecuted(resolutionTime);
        assertEquals(grpcResolutionTime, subject.executionTime());

        subject.markDeleted(resolutionTime);
        assertEquals(grpcResolutionTime, subject.deletionTime());
    }

    @Test
    void notaryWorks() {
        assertFalse(subject.hasValidSignatureFor(fpk));
        assertFalse(subject.hasValidSignatureFor(spk));
        assertFalse(subject.hasValidSignatureFor(tpk));

        subject.witnessValidSignature(fpk);
        subject.witnessValidSignature(tpk);

        assertTrue(subject.hasValidSignatureFor(fpk));
        assertFalse(subject.hasValidSignatureFor(spk));
        assertTrue(subject.hasValidSignatureFor(tpk));
    }

    @Test
    void witnessOnlyTrueIfNewSignatory() {
        assertTrue(subject.witnessValidSignature(fpk));
        assertFalse(subject.witnessValidSignature(fpk));
    }

    @Test
    void releaseIsNoop() {
        assertDoesNotThrow(subject::release);
    }

    @Test
    void signatoriesArePublished() {
        subject.witnessValidSignature(fpk);
        subject.witnessValidSignature(spk);
        subject.witnessValidSignature(tpk);

        assertTrue(subject.signatories().containsAll(signatories));
    }

    @Test
    void nonessentialFieldsDontAffectIdentity() {
        final var diffBodyBytes =
                parentTxn.toBuilder()
                        .setTransactionID(
                                parentTxn.getTransactionID().toBuilder()
                                        .setAccountID(otherPayer.toGrpcAccountId())
                                        .setTransactionValidStart(
                                                MiscUtils.asTimestamp(
                                                        otherSchedulingTXValidStart.toJava())))
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setPayerAccountID(otherPayer.toGrpcAccountId()))
                        .build()
                        .toByteArray();
        final var other =
                ScheduleVirtualValue.from(
                        diffBodyBytes, RichInstant.fromJava(expiry.toJava().plusSeconds(1)));
        other.markExecuted(resolutionTime);
        other.markDeleted(resolutionTime);
        other.witnessValidSignature(fpk);

        assertEquals(subject, other);
        assertEquals(subject.hashCode(), other.hashCode());
        assertEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentAdminKeysNotIdentical() {
        final var bodyBytesDiffAdminKey =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setAdminKey(MiscUtils.asKeyUnchecked(otherAdminKey)))
                        .build()
                        .toByteArray();
        final var other = ScheduleVirtualValue.from(bodyBytesDiffAdminKey, expiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertNotEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertNotEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentMemosNotIdentical() {
        final var bodyBytesDiffMemo =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder().setMemo(otherEntityMemo))
                        .build()
                        .toByteArray();
        final var other = ScheduleVirtualValue.from(bodyBytesDiffMemo, expiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertNotEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertNotEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentMemosNotIdenticalViaNull() {
        final var bodyBytesDiffMemo =
                parentTxn.toBuilder()
                        .setScheduleCreate(parentTxn.getScheduleCreate().toBuilder().clearMemo())
                        .build()
                        .toByteArray();
        final var other = ScheduleVirtualValue.from(bodyBytesDiffMemo, expiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertNotEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertNotEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentScheduledTxnNotIdentical() {
        final var bodyBytesDiffScheduledTxn =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setScheduledTransactionBody(
                                                scheduledTxn.toBuilder()
                                                        .setMemo("Slightly different!")))
                        .build()
                        .toByteArray();
        final var other = ScheduleVirtualValue.from(bodyBytesDiffScheduledTxn, expiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertNotEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertNotEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentProvidedExpirationTimeNotIdentical() {
        final var bodyBytesDiffMemo =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setExpirationTime(
                                                RichInstant.fromJava(
                                                                providedExpiry
                                                                        .toJava()
                                                                        .plusNanos(1))
                                                        .toGrpc()))
                        .build()
                        .toByteArray();
        final var other =
                ScheduleVirtualValue.from(
                        bodyBytesDiffMemo,
                        RichInstant.fromJava(providedExpiry.toJava().plusNanos(1)));

        assertEquals(subject.calculatedExpirationTime(), subject.expirationTimeProvided());
        assertEquals(other.expirationTimeProvided(), other.expirationTimeProvided());

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertNotEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertNotEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentCalculatedExpirationTimeIdentical() {
        final var bodyBytesDiffMemo =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setExpirationTime(providedExpiry.toGrpc()))
                        .build()
                        .toByteArray();
        final var other =
                ScheduleVirtualValue.from(
                        bodyBytesDiffMemo,
                        RichInstant.fromJava(providedExpiry.toJava().plusNanos(1)));

        assertEquals(other.expirationTimeProvided(), subject.expirationTimeProvided());

        assertNotEquals(other.calculatedExpirationTime(), subject.calculatedExpirationTime());
        assertEquals(subject, other);
        assertEquals(subject.hashCode(), other.hashCode());
        assertEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentProvidedWaitForExpiryNotIdentical() {
        final var bodyBytesDiffMemo =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setWaitForExpiry(!waitForExpiry))
                        .build()
                        .toByteArray();
        final var other = ScheduleVirtualValue.from(bodyBytesDiffMemo, expiry);

        assertEquals(subject.calculatedWaitForExpiry(), subject.waitForExpiryProvided());
        assertEquals(other.calculatedWaitForExpiry(), other.waitForExpiryProvided());

        assertNotEquals(other.waitForExpiryProvided(), subject.waitForExpiryProvided());
        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertNotEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertNotEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void differentCalculatedWaitForExpiryIdentical() {
        final var bodyBytesDiffMemo =
                parentTxn.toBuilder()
                        .setScheduleCreate(
                                parentTxn.getScheduleCreate().toBuilder()
                                        .setWaitForExpiry(waitForExpiry))
                        .build()
                        .toByteArray();
        final var other = ScheduleVirtualValue.from(bodyBytesDiffMemo, expiry);

        other.setCalculatedWaitForExpiry(!subject.calculatedWaitForExpiry());
        assertEquals(other.waitForExpiryProvided(), subject.waitForExpiryProvided());

        assertNotEquals(other.calculatedWaitForExpiry(), subject.calculatedWaitForExpiry());
        assertEquals(subject, other);
        assertEquals(subject.hashCode(), other.hashCode());
        assertEquals(subject.equalityCheckKey(), other.equalityCheckKey());
        assertEquals(subject.equalityCheckValue(), other.equalityCheckValue());
    }

    @Test
    void validToString() {
        subject.witnessValidSignature(fpk);
        subject.witnessValidSignature(spk);
        subject.witnessValidSignature(tpk);
        subject.markDeleted(resolutionTime);

        final var expected =
                "ScheduleVirtualValue{"
                        + "scheduledTxn="
                        + scheduledTxn
                        + ", "
                        + "expirationTimeProvided="
                        + providedExpiry
                        + ", "
                        + "calculatedExpirationTime="
                        + expiry
                        + ", "
                        + "executed="
                        + false
                        + ", "
                        + "waitForExpiryProvided="
                        + waitForExpiry
                        + ", "
                        + "calculatedWaitForExpiry="
                        + waitForExpiry
                        + ", "
                        + "deleted="
                        + true
                        + ", "
                        + "memo="
                        + entityMemo
                        + ", "
                        + "payer="
                        + payer.toAbbrevString()
                        + ", "
                        + "schedulingAccount="
                        + schedulingAccount
                        + ", "
                        + "schedulingTXValidStart="
                        + schedulingTXValidStart
                        + ", "
                        + "signatories=["
                        + signatoriesToString()
                        + "], "
                        + "adminKey="
                        + describe(adminKey)
                        + ", "
                        + "resolutionTime="
                        + RichInstant.fromJava(resolutionTime).toString()
                        + ", number=3}";

        assertEquals(expected, subject.toString());
    }

    @Test
    void validEqualityChecks() {
        assertEquals(subject, subject);
        assertNotEquals(null, subject);
        assertNotEquals(new Object(), subject);
        assertNotEquals(subject, new Object());
        assertNotEquals(null, subject);
    }

    @Test
    void validVersion() {
        assertEquals(ScheduleVirtualValue.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void validRuntimeConstructableID() {
        assertEquals(ScheduleVirtualValue.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void copyWorks() {
        subject.markDeleted(resolutionTime);
        subject.witnessValidSignature(tpk);
        final var copySubject = subject.copy();

        assertTrue(copySubject.isDeleted());
        assertFalse(copySubject.isExecuted());
        assertTrue(copySubject.hasValidSignatureFor(tpk));

        assertEquals(subject.toString(), copySubject.toString());
        assertNotSame(subject.signatories(), copySubject.signatories());

        assertEquals(grpcResolutionTime, copySubject.deletionTime());
        assertEquals(payer, copySubject.payer());
        assertEquals(expiry, copySubject.calculatedExpirationTime());
        assertEquals(waitForExpiry, copySubject.calculatedWaitForExpiry());
        assertEquals(waitForExpiry, copySubject.waitForExpiryProvided());
        assertEquals(schedulingAccount, copySubject.schedulingAccount());
        assertEquals(entityMemo, copySubject.memo().get());
        assertEquals(adminKey.toString(), copySubject.adminKey().get().toString());
        assertEquals(MiscUtils.asKeyUnchecked(adminKey), copySubject.grpcAdminKey());
        assertEquals(schedulingTXValidStart, copySubject.schedulingTXValidStart());
        assertEquals(scheduledTxn, copySubject.scheduledTxn());
        assertEquals(expectedSignedTxn(), copySubject.asSignedTxn());
        assertArrayEquals(bodyBytes, copySubject.bodyBytes());
        assertTrue(subject.isImmutable());
        assertFalse(copySubject.isImmutable());
        assertTrue(subject.hasAdminKey());
    }

    @Test
    void asWritableWorks() {
        subject.markDeleted(resolutionTime);
        subject.witnessValidSignature(tpk);
        final var copySubject = subject.asWritable();

        assertTrue(copySubject.isDeleted());
        assertFalse(copySubject.isExecuted());
        assertTrue(copySubject.hasValidSignatureFor(tpk));

        assertEquals(subject.toString(), copySubject.toString());
        assertNotSame(subject.signatories(), copySubject.signatories());

        assertEquals(grpcResolutionTime, copySubject.deletionTime());
        assertEquals(payer, copySubject.payer());
        assertEquals(expiry, copySubject.calculatedExpirationTime());
        assertEquals(waitForExpiry, copySubject.calculatedWaitForExpiry());
        assertEquals(waitForExpiry, copySubject.waitForExpiryProvided());
        assertEquals(schedulingAccount, copySubject.schedulingAccount());
        assertEquals(entityMemo, copySubject.memo().get());
        assertEquals(adminKey.toString(), copySubject.adminKey().get().toString());
        assertEquals(MiscUtils.asKeyUnchecked(adminKey), copySubject.grpcAdminKey());
        assertEquals(schedulingTXValidStart, copySubject.schedulingTXValidStart());
        assertEquals(scheduledTxn, copySubject.scheduledTxn());
        assertEquals(expectedSignedTxn(), copySubject.asSignedTxn());
        assertArrayEquals(bodyBytes, copySubject.bodyBytes());
        assertFalse(subject.isImmutable());
        assertFalse(copySubject.isImmutable());
        assertTrue(subject.hasAdminKey());
    }

    @Test
    void asReadOnlyWorks() {
        subject.markDeleted(resolutionTime);
        subject.witnessValidSignature(tpk);
        final var copySubject = subject.asReadOnly();

        assertTrue(copySubject.isDeleted());
        assertFalse(copySubject.isExecuted());
        assertTrue(copySubject.hasValidSignatureFor(tpk));

        assertEquals(subject.toString(), copySubject.toString());
        assertNotSame(subject.signatories(), copySubject.signatories());

        assertEquals(grpcResolutionTime, copySubject.deletionTime());
        assertEquals(payer, copySubject.payer());
        assertEquals(expiry, copySubject.calculatedExpirationTime());
        assertEquals(waitForExpiry, copySubject.calculatedWaitForExpiry());
        assertEquals(waitForExpiry, copySubject.waitForExpiryProvided());
        assertEquals(schedulingAccount, copySubject.schedulingAccount());
        assertEquals(entityMemo, copySubject.memo().get());
        assertEquals(adminKey.toString(), copySubject.adminKey().get().toString());
        assertEquals(MiscUtils.asKeyUnchecked(adminKey), copySubject.grpcAdminKey());
        assertEquals(schedulingTXValidStart, copySubject.schedulingTXValidStart());
        assertEquals(scheduledTxn, copySubject.scheduledTxn());
        assertEquals(expectedSignedTxn(), copySubject.asSignedTxn());
        assertArrayEquals(bodyBytes, copySubject.bodyBytes());
        assertFalse(subject.isImmutable());
        assertTrue(copySubject.isImmutable());
        assertTrue(subject.hasAdminKey());
    }

    private String signatoriesToString() {
        return signatories.stream().map(CommonUtils::hex).collect(Collectors.joining(", "));
    }

    private static final long fee = 123L;
    private static final String scheduledTxnMemo = "Wait for me!";

    private static final TransactionID parentTxnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(
                            MiscUtils.asTimestamp(schedulingTXValidStart.toJava()))
                    .setAccountID(schedulingAccount.toGrpcAccountId())
                    .build();
    private static final SchedulableTransactionBody scheduledTxn =
            SchedulableTransactionBody.newBuilder()
                    .setTransactionFee(fee)
                    .setMemo(scheduledTxnMemo)
                    .setCryptoDelete(
                            CryptoDeleteTransactionBody.newBuilder()
                                    .setDeleteAccountID(IdUtils.asAccount("0.0.2"))
                                    .setTransferAccountID(IdUtils.asAccount("0.0.75231")))
                    .build();

    private static final TransactionBody ordinaryVersionOfScheduledTxn =
            MiscUtils.asOrdinary(scheduledTxn, parentTxnId);

    private static final ScheduleCreateTransactionBody creation =
            ScheduleCreateTransactionBody.newBuilder()
                    .setAdminKey(MiscUtils.asKeyUnchecked(adminKey))
                    .setPayerAccountID(payer.toGrpcAccountId())
                    .setExpirationTime(providedExpiry.toGrpc())
                    .setWaitForExpiry(waitForExpiry)
                    .setMemo(entityMemo)
                    .setScheduledTransactionBody(scheduledTxn)
                    .build();
    private static final TransactionBody parentTxn =
            TransactionBody.newBuilder()
                    .setTransactionID(parentTxnId)
                    .setScheduleCreate(creation)
                    .build();
    private static final byte[] bodyBytes = parentTxn.toByteArray();

    private static Transaction expectedSignedTxn() {
        final var expectedId =
                TransactionID.newBuilder()
                        .setAccountID(schedulingAccount.toGrpcAccountId())
                        .setTransactionValidStart(asTimestamp(schedulingTXValidStart.toJava()))
                        .setScheduled(true);
        return Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(
                                        TransactionBody.newBuilder()
                                                .mergeFrom(
                                                        MiscUtils.asOrdinary(
                                                                scheduledTxn, parentTxnId))
                                                .setTransactionID(expectedId)
                                                .build()
                                                .toByteString())
                                .build()
                                .toByteString())
                .build();
    }

    public static TransactionBody scheduleCreateTxnWith(
            final Key scheduleAdminKey,
            final String scheduleMemo,
            final AccountID payer,
            final AccountID scheduler,
            final Timestamp validStart) {
        return scheduleCreateTxnWith(
                scheduleAdminKey, scheduleMemo, payer, scheduler, validStart, null, null);
    }

    public static TransactionBody scheduleCreateTxnWith(
            final Key scheduleAdminKey,
            final String scheduleMemo,
            final AccountID payer,
            final AccountID scheduler,
            final Timestamp validStart,
            final Timestamp expirationTime,
            final Boolean waitForExpiry) {
        final var creation =
                ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(scheduleAdminKey)
                        .setPayerAccountID(payer)
                        .setMemo(scheduleMemo)
                        .setScheduledTransactionBody(scheduledTxn);
        if (expirationTime != null) {
            creation.setExpirationTime(expirationTime);
        }
        if (waitForExpiry != null) {
            creation.setWaitForExpiry(waitForExpiry);
        }
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(validStart)
                                .setAccountID(scheduler)
                                .build())
                .setScheduleCreate(creation)
                .build();
    }
}
