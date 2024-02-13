/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.schedule;

import static com.google.protobuf.ByteString.copyFrom;
import static com.hedera.node.app.hapi.utils.CommonUtils.functionOf;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asTimestamp;
import static com.hedera.node.app.service.mono.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleSchedule;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.utils.ThrowingConsumer;
import com.hedera.node.app.service.mono.state.virtual.utils.ThrowingSupplier;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is currently used in a MerkleMap due to issues with virtual map in the 0.27 release. It
 * should be moved back to VirtualMap in 0.28.
 */
public class ScheduleVirtualValue extends PartialMerkleLeaf
        implements VirtualValue, Keyed<EntityNumVirtualKey>, MerkleLeaf {

    public static final int CURRENT_VERSION = 1;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xadfd7f9e613385fcL;

    @Nullable
    private long number; // that one

    @Nullable
    private Key grpcAdminKey = null;

    @Nullable
    private JKey adminKey = null;

    private String memo;
    private boolean deleted = false;
    private boolean executed = false;
    private boolean calculatedWaitForExpiry = false;
    private boolean waitForExpiryProvided = false;

    @Nullable
    private EntityId payer = null;

    private EntityId schedulingAccount;
    private RichInstant schedulingTXValidStart;

    @Nullable
    private RichInstant expirationTimeProvided = null;

    @Nullable
    private RichInstant calculatedExpirationTime = null;

    @Nullable
    private RichInstant resolutionTime = null;

    private byte[] bodyBytes;
    private TransactionBody ordinaryScheduledTxn;
    private SchedulableTransactionBody scheduledTxn;

    private final List<byte[]> signatories = new ArrayList<>();
    private final Set<ByteString> notary = ConcurrentHashMap.newKeySet();

    public ScheduleVirtualValue() {
        /* RuntimeConstructable */
    }

    public ScheduleVirtualValue(final ScheduleVirtualValue toCopy) {

        /* These fields are all immutable or effectively immutable, we can share them between copies */
        this.grpcAdminKey = toCopy.grpcAdminKey;
        this.adminKey = toCopy.adminKey;
        this.memo = toCopy.memo;
        this.calculatedWaitForExpiry = toCopy.calculatedWaitForExpiry;
        this.waitForExpiryProvided = toCopy.waitForExpiryProvided;
        this.deleted = toCopy.deleted;
        this.executed = toCopy.executed;
        this.payer = toCopy.payer;
        this.schedulingAccount = toCopy.schedulingAccount;
        this.schedulingTXValidStart = toCopy.schedulingTXValidStart;
        this.expirationTimeProvided = toCopy.expirationTimeProvided;
        this.calculatedExpirationTime = toCopy.calculatedExpirationTime;
        this.bodyBytes = toCopy.bodyBytes;
        this.scheduledTxn = toCopy.scheduledTxn;
        this.ordinaryScheduledTxn = toCopy.ordinaryScheduledTxn;
        this.resolutionTime = toCopy.resolutionTime;
        this.number = toCopy.number;

        /* Signatories are mutable */
        for (final byte[] signatory : toCopy.signatories) {
            this.witnessValidSignature(signatory);
        }
    }

    public ScheduleVirtualValue(final MerkleSchedule toCopy) {
        bodyBytes = toCopy.bodyBytes();
        calculatedExpirationTime = new RichInstant(toCopy.expiry(), 0);
        executed = toCopy.isExecuted();
        deleted = toCopy.isDeleted();
        resolutionTime = toCopy.getResolutionTime();
        for (final var sig : toCopy.signatories()) {
            witnessValidSignature(sig);
        }

        initFromBodyBytes();
    }

    public static ScheduleVirtualValue from(final byte[] bodyBytes, final long consensusExpiry) {
        final var to = new ScheduleVirtualValue();
        to.calculatedExpirationTime = new RichInstant(consensusExpiry, 0);
        to.bodyBytes = bodyBytes;
        to.initFromBodyBytes();
        to.calculatedWaitForExpiry = to.waitForExpiryProvided;

        return to;
    }

    @VisibleForTesting
    static ScheduleVirtualValue from(final byte[] bodyBytes, final RichInstant consensusExpiry) {
        final var to = new ScheduleVirtualValue();
        to.calculatedExpirationTime = consensusExpiry;
        to.bodyBytes = bodyBytes;
        to.initFromBodyBytes();
        to.calculatedWaitForExpiry = to.waitForExpiryProvided;

        return to;
    }

    /* Notary functions */
    public boolean witnessValidSignature(final byte[] key) {
        final var usableKey = copyFrom(key);
        if (notary.contains(usableKey)) {
            return false;
        } else {
            signatories.add(key);
            notary.add(usableKey);
            return true;
        }
    }

    public Transaction asSignedTxn() {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(ordinaryScheduledTxn.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    public final TransactionID scheduledTransactionId() {
        if (schedulingAccount == null || schedulingTXValidStart == null) {
            throw new IllegalStateException("Cannot invoke scheduledTransactionId on a content-addressable view!");
        }
        return TransactionID.newBuilder()
                .setAccountID(schedulingAccount.toGrpcAccountId())
                .setTransactionValidStart(asTimestamp(schedulingTXValidStart))
                .setScheduled(true)
                .build();
    }

    public boolean hasValidSignatureFor(final byte[] key) {
        return notary.contains(copyFrom(key));
    }

    /* Object */

    /**
     * Two {@code ScheduleVirtualValue}s are identical as long as they agree on the all the fields
     * of the ScheduleCreate other than the payerAccountID.
     *
     * @param o the object to check for equality
     * @return whether {@code this} and {@code o} are identical
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || ScheduleVirtualValue.class != o.getClass()) {
            return false;
        }

        final var that = (ScheduleVirtualValue) o;
        return Objects.equals(this.memo, that.memo)
                && Objects.equals(this.scheduledTxn, that.scheduledTxn)
                && Objects.equals(this.grpcAdminKey, that.grpcAdminKey)
                && Objects.equals(this.expirationTimeProvided, that.expirationTimeProvided)
                && Objects.equals(this.waitForExpiryProvided, that.waitForExpiryProvided);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memo, grpcAdminKey, scheduledTxn, expirationTimeProvided, waitForExpiryProvided);
    }

    public long equalityCheckKey() {
        return equalityHash().asLong();
    }

    public String equalityCheckValue() {
        return equalityHash().toString();
    }

    private HashCode equalityHash() {
        // if this changes at all, the equality database for scheduled transactions will need to be
        // re-built
        return buildEqualityHash(
                memo != null ? memo.getBytes(StandardCharsets.UTF_8) : new byte[] {},
                grpcAdminKey != null ? grpcAdminKey.toByteArray() : new byte[] {},
                scheduledTxn.toByteArray(),
                expirationTimeProvided != null ? expirationTimeProvided.toGrpc().toByteArray() : new byte[] {},
                waitForExpiryProvided ? new byte[] {1} : new byte[] {0});
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(ScheduleVirtualValue.class)
                .add("scheduledTxn", scheduledTxn)
                .add("expirationTimeProvided", expirationTimeProvided)
                .add("calculatedExpirationTime", calculatedExpirationTime)
                .add("executed", executed)
                .add("waitForExpiryProvided", waitForExpiryProvided)
                .add("calculatedWaitForExpiry", calculatedWaitForExpiry)
                .add("deleted", deleted)
                .add("memo", memo)
                .add("payer", readablePayer())
                .add("schedulingAccount", schedulingAccount)
                .add("schedulingTXValidStart", schedulingTXValidStart)
                .add("signatories", signatories.stream().map(CommonUtils::hex).toList())
                .add("adminKey", describe(adminKey))
                .add("resolutionTime", resolutionTime)
                .add("number", number);
        return helper.toString();
    }

    private String readablePayer() {
        return Optional.ofNullable(effectivePayer())
                .map(EntityId::toAbbrevString)
                .orElse("<N/A>");
    }

    int serializedSizeInBytes() {
        int size = 0;
        size += Integer.BYTES; // bodyBytes length
        size += bodyBytes.length; // bodyBytes
        if (calculatedExpirationTime == null) {
            size += Byte.BYTES;
        } else {
            size += Byte.BYTES;
            size += Long.BYTES; // calculatedExpirationTime seconds
            size += Integer.BYTES; // calculatedExpirationTime nanos
        }
        size += Byte.BYTES; // calculatedWaitForExpiry
        size += Byte.BYTES; // executed
        size += Byte.BYTES; // deleted
        if (resolutionTime == null) {
            size += Byte.BYTES;
        } else {
            size += Byte.BYTES;
            size += Long.BYTES; // resolutionTime seconds
            size += Integer.BYTES; // resolutionTime nanos
        }
        size += Integer.BYTES; // signatories size
        for (final byte[] k : signatories) {
            size += Integer.BYTES; // k length
            size += k.length; // k
        }
        size += Long.BYTES; // number
        return size;
    }

    private <E extends Exception> void serializeTo(
            final ThrowingConsumer<Byte, E> writeByteFn,
            final ThrowingConsumer<Integer, E> writeIntFn,
            final ThrowingConsumer<Long, E> writeLongFn,
            final ThrowingConsumer<byte[], E> writeBytesFn)
            throws E {
        writeIntFn.accept(bodyBytes.length);
        writeBytesFn.accept(bodyBytes);
        if (calculatedExpirationTime == null) {
            writeByteFn.accept((byte) 0);
        } else {
            writeByteFn.accept((byte) 1);
            writeLongFn.accept(calculatedExpirationTime.getSeconds());
            writeIntFn.accept(calculatedExpirationTime.getNanos());
        }
        writeByteFn.accept((byte) (calculatedWaitForExpiry ? 1 : 0));
        writeByteFn.accept((byte) (executed ? 1 : 0));
        writeByteFn.accept((byte) (deleted ? 1 : 0));
        if (resolutionTime == null) {
            writeByteFn.accept((byte) 0);
        } else {
            writeByteFn.accept((byte) 1);
            writeLongFn.accept(resolutionTime.getSeconds());
            writeIntFn.accept(resolutionTime.getNanos());
        }
        writeIntFn.accept(signatories.size());
        for (final byte[] k : signatories) {
            writeIntFn.accept(k.length);
            writeBytesFn.accept(k);
        }
        writeLongFn.accept(number);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeTo(out::writeByte, out::writeInt, out::writeLong, out::write);
    }

    void serialize(final WritableSequentialData out) {
        serializeTo(out::writeByte, out::writeInt, out::writeLong, out::writeBytes);
    }

    @Deprecated
    void serialize(final ByteBuffer buffer) {
        serializeTo(buffer::put, buffer::putInt, buffer::putLong, buffer::put);
    }

    private <E extends Exception> void deserializeFrom(
            final ThrowingSupplier<Byte, E> readByteFn,
            final ThrowingSupplier<Integer, E> readIntFn,
            final ThrowingSupplier<Long, E> readLongFn,
            final ThrowingConsumer<byte[], E> readBytesFn)
            throws E {
        var n = readIntFn.get();
        bodyBytes = new byte[n];
        readBytesFn.accept(bodyBytes);
        if (readByteFn.get() == 1) {
            calculatedExpirationTime = new RichInstant(readLongFn.get(), readIntFn.get());
        } else {
            calculatedExpirationTime = null;
        }
        calculatedWaitForExpiry = readByteFn.get() == 1;
        executed = readByteFn.get() == 1;
        deleted = readByteFn.get() == 1;
        if (readByteFn.get() == 1) {
            resolutionTime = new RichInstant(readLongFn.get(), readIntFn.get());
        } else {
            resolutionTime = null;
        }
        final int k = readIntFn.get();
        for (int x = 0; x < k; ++x) {
            n = readIntFn.get();
            final byte[] bytes = new byte[n];
            readBytesFn.accept(bytes);
            witnessValidSignature(bytes);
        }
        number = readLongFn.get();

        initFromBodyBytes();
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        deserializeFrom(in::readByte, in::readInt, in::readLong, in::readFully);
    }

    void deserialize(final ReadableSequentialData in) {
        deserializeFrom(in::readByte, in::readInt, in::readLong, in::readBytes);
    }

    @Deprecated
    void deserialize(final ByteBuffer buffer, final int version) {
        deserializeFrom(buffer::get, buffer::getInt, buffer::getLong, buffer::get);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    public Optional<String> memo() {
        return Optional.ofNullable(this.memo);
    }

    public boolean waitForExpiryProvided() {
        return this.waitForExpiryProvided;
    }

    public boolean calculatedWaitForExpiry() {
        return this.calculatedWaitForExpiry;
    }

    public void setCalculatedWaitForExpiry(final boolean calculatedWaitForExpiry) {
        this.calculatedWaitForExpiry = calculatedWaitForExpiry;
    }

    public boolean hasAdminKey() {
        return adminKey != null;
    }

    public Optional<JKey> adminKey() {
        return Optional.ofNullable(adminKey);
    }

    @VisibleForTesting
    public final void setAdminKey(final JKey adminKey) {
        throwIfImmutable("Cannot change this schedule's adminKey if it's immutable.");
        this.adminKey = adminKey;
    }

    @VisibleForTesting
    public void setPayer(final EntityId payer) {
        throwIfImmutable("Cannot change this schedule's payer if it's immutable.");
        this.payer = payer;
    }

    public EntityId payer() {
        return payer;
    }

    public EntityId effectivePayer() {
        return hasExplicitPayer() ? payer : schedulingAccount;
    }

    public boolean hasExplicitPayer() {
        return payer != null;
    }

    public EntityId schedulingAccount() {
        return schedulingAccount;
    }

    @VisibleForTesting
    void setSchedulingAccount(final EntityId schedulingAccount) {
        this.schedulingAccount = schedulingAccount;
    }

    public RichInstant schedulingTXValidStart() {
        return this.schedulingTXValidStart;
    }

    public List<byte[]> signatories() {
        return signatories;
    }

    public RichInstant expirationTimeProvided() {
        return expirationTimeProvided;
    }

    public RichInstant calculatedExpirationTime() {
        return calculatedExpirationTime;
    }

    public Set<ByteString> notary() {
        return notary;
    }

    public void setCalculatedExpirationTime(final RichInstant calculatedExpirationTime) {
        throwIfImmutable("Cannot change this schedule's payer if it's immutable.");
        this.calculatedExpirationTime = calculatedExpirationTime;
    }

    public void markDeleted(final Instant at) {
        throwIfImmutable("Cannot change this schedule to deleted if it's immutable.");
        resolutionTime = RichInstant.fromJava(at);
        deleted = true;
    }

    public void markExecuted(final Instant at) {
        throwIfImmutable("Cannot change this schedule to executed if it's immutable.");
        resolutionTime = RichInstant.fromJava(at);
        executed = true;
    }

    public boolean isExecuted() {
        return executed;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Timestamp deletionTime() {
        if (!deleted) {
            throw new IllegalStateException("Schedule not deleted, cannot return deletion time!");
        }
        return resolutionTime.toGrpc();
    }

    public Timestamp executionTime() {
        if (!executed) {
            throw new IllegalStateException("Schedule not executed, cannot return execution time!");
        }
        return resolutionTime.toGrpc();
    }

    @VisibleForTesting
    public RichInstant getResolutionTime() {
        return resolutionTime;
    }

    public HederaFunctionality scheduledFunction() {
        try {
            return functionOf(ordinaryScheduledTxn);
        } catch (final UnknownHederaFunctionality ignore) {
            return NONE;
        }
    }

    public TransactionBody ordinaryViewOfScheduledTxn() {
        return ordinaryScheduledTxn;
    }

    public SchedulableTransactionBody scheduledTxn() {
        return scheduledTxn;
    }

    public byte[] bodyBytes() {
        return bodyBytes;
    }

    public Key grpcAdminKey() {
        return grpcAdminKey;
    }

    private void initFromBodyBytes() {
        try {
            final var parentTxn = TransactionBody.parseFrom(bodyBytes);
            final var creationOp = parentTxn.getScheduleCreate();

            if (!creationOp.getMemo().isEmpty()) {
                memo = creationOp.getMemo();
            }
            expirationTimeProvided =
                    creationOp.hasExpirationTime() ? RichInstant.fromGrpc(creationOp.getExpirationTime()) : null;
            waitForExpiryProvided = creationOp.getWaitForExpiry();
            if (creationOp.hasPayerAccountID()) {
                payer = EntityId.fromGrpcAccountId(creationOp.getPayerAccountID());
            }
            if (creationOp.hasAdminKey()) {
                MiscUtils.asUsableFcKey(creationOp.getAdminKey()).ifPresent(this::setAdminKey);
                if (adminKey != null) {
                    grpcAdminKey = creationOp.getAdminKey();
                }
            }
            scheduledTxn = creationOp.getScheduledTransactionBody();
            schedulingAccount =
                    EntityId.fromGrpcAccountId(parentTxn.getTransactionID().getAccountID());
            schedulingTXValidStart =
                    RichInstant.fromGrpc(parentTxn.getTransactionID().getTransactionValidStart());
            ordinaryScheduledTxn = MiscUtils.asOrdinary(scheduledTxn, scheduledTransactionId());
        } catch (final InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(
                    String.format("Argument bodyBytes=0x%s was not a TransactionBody!", CommonUtils.hex(bodyBytes)));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ScheduleVirtualValue asReadOnly() {
        final var c = new ScheduleVirtualValue(this);
        c.setImmutable(true);
        return c;
    }

    @Override
    public ScheduleVirtualValue copy() {
        final var fc = new ScheduleVirtualValue(this);

        this.setImmutable(true);

        return fc;
    }

    /**
     * Needed until getForModify works on VirtualMap
     *
     * @return a copy of this without marking this as immutable
     */
    public ScheduleVirtualValue asWritable() {
        return new ScheduleVirtualValue(this);
    }

    private static HashCode buildEqualityHash(final byte[]... a) {
        final var hasher = Hashing.sha256().newHasher();

        for (final byte[] bytes : a) {
            hasher.putInt(bytes.length);
            hasher.putBytes(bytes);
        }

        return hasher.hash();
    }

    @Override
    public EntityNumVirtualKey getKey() {
        return new EntityNumVirtualKey(number);
    }

    @Override
    public void setKey(final EntityNumVirtualKey key) {
        this.number = key == null ? -1 : key.getKeyAsLong();
    }
}
