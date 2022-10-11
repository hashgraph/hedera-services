package com.hedera.services.state.virtual.entities;

import com.hedera.services.state.virtual.annotations.StateSetter;
import com.hedera.services.state.virtual.utils.CheckedConsumer;
import com.hedera.services.state.virtual.utils.CheckedSupplier;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class OnDiskAccount implements VirtualValue {
    private static final int CURRENT_VERSION = 1;
    private static final long CLASS_ID = 0xc88e3a5c7b497468L;

    private byte flags;
    private final int[] ints = new int[IntValues.COUNT];
    private final long[] longs = new long[LongValues.COUNT];

    private boolean immutable = false;

    public OnDiskAccount() {
        // Intentional no-op
    }

    public OnDiskAccount(final OnDiskAccount that) {
        this.flags = that.flags;
        System.arraycopy(that.ints, 0, this.ints, 0, IntValues.COUNT);
        System.arraycopy(that.longs, 0, this.longs, 0, LongValues.COUNT);
    }


    @Override
    public OnDiskAccount copy() {
        this.immutable = true;
        return new OnDiskAccount(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        final var copy = new OnDiskAccount(this);
        copy.immutable = true;
        return copy;
    }

    @Override
    public void serialize(final ByteBuffer to) throws IOException {
        serializeTo(to::put, to::putInt, to::putLong);
    }

    @Override
    public void deserialize(final ByteBuffer from, final int version) throws IOException {
        deserializeFrom(from::get, from::getInt, from::getLong);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeTo(out::writeByte, out::writeInt, out::writeLong);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        deserializeFrom(in::readByte, in::readInt, in::readLong);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }

    private void serializeTo(
            final CheckedConsumer<Byte> writeByteFn,
            final CheckedConsumer<Integer> writeIntFn,
            final CheckedConsumer<Long> writeLongFn) throws IOException {
        writeByteFn.accept(flags);
        for (final var v : ints) {
            writeIntFn.accept(v);
        }
        for (final var v : longs) {
            writeLongFn.accept(v);
        }
    }

    private void deserializeFrom(
            final CheckedSupplier<Byte> readByteFn,
            final CheckedSupplier<Integer> readIntFn,
            final CheckedSupplier<Long> readLongFn) throws IOException {
        throwIfImmutable();
        flags = readByteFn.get();
        for (var i = 0; i < IntValues.COUNT; i++) {
            ints[i] = readIntFn.get();
        }
        for (var i = 0; i < LongValues.COUNT; i++) {
            longs[i] = readLongFn.get();
        }
    }

    // Boolean getters and setters
    public boolean isDeleted() {
        return (flags & Masks.IS_DELETED) != 0;
    }

    @StateSetter
    public void setIsDeleted(final boolean flag) {
        throwIfImmutable("Tried to setIsDeleted on an immutable OnDiskAccount");
        if (flag) {
            flags |= Masks.IS_DELETED;
        } else {
            flags &= ~Masks.IS_DELETED;
        }
    }

    public boolean isContract() {
        return (flags & Masks.IS_CONTRACT) != 0;
    }

    @StateSetter
    public void setIsContract(final boolean flag) {
        throwIfImmutable("Tried to setIsContract on an immutable OnDiskAccount");
        if (flag) {
            flags |= Masks.IS_CONTRACT;
        } else {
            flags &= ~Masks.IS_CONTRACT;
        }
    }

    public boolean isReceiverSigRequired() {
        return (flags & Masks.IS_RECEIVER_SIG_REQUIRED) != 0;
    }

    @StateSetter
    public void setIsReceiverSigRequired(final boolean flag) {
        throwIfImmutable("Tried to setIsReceiverSigRequired on an immutable OnDiskAccount");
        if (flag) {
            flags |= Masks.IS_RECEIVER_SIG_REQUIRED;
        } else {
            flags &= ~Masks.IS_RECEIVER_SIG_REQUIRED;
        }
    }

    // Int getters and setters
    public int getNumContractKvPairs() {
        return ints[IntValues.NUM_CONTRACT_KV_PAIRS];
    }

    @StateSetter
    public void setNumContractKvPairs(final int value) {
        throwIfImmutable("Tried to set NUM_CONTRACT_KV_PAIRS on an immutable OnDiskAccount");
        ints[IntValues.NUM_CONTRACT_KV_PAIRS] = value;
    }

    public int getMaxAutoAssociations() {
        return ints[IntValues.MAX_AUTO_ASSOCIATIONS];
    }

    @StateSetter
    public void setMaxAutoAssociations(final int value) {
        throwIfImmutable("Tried to set MAX_AUTO_ASSOCIATIONS on an immutable OnDiskAccount");
        ints[IntValues.MAX_AUTO_ASSOCIATIONS] = value;
    }

    public int getUsedAutoAssociations() {
        return ints[IntValues.USED_AUTO_ASSOCIATIONS];
    }

    @StateSetter
    public void setUsedAutoAssociations(final int value) {
        throwIfImmutable("Tried to set USED_AUTO_ASSOCIATIONS on an immutable OnDiskAccount");
        ints[IntValues.USED_AUTO_ASSOCIATIONS] = value;
    }

    public int getNumAssociations() {
        return ints[IntValues.NUM_ASSOCIATIONS];
    }

    @StateSetter
    public void setNumAssociations(final int value) {
        throwIfImmutable("Tried to set NUM_ASSOCIATIONS on an immutable OnDiskAccount");
        ints[IntValues.NUM_ASSOCIATIONS] = value;
    }

    public int getNumPositiveBalances() {
        return ints[IntValues.NUM_POSITIVE_BALANCES];
    }

    @StateSetter
    public void setNumPositiveBalances(final int value) {
        throwIfImmutable("Tried to set NUM_POSITIVE_BALANCES on an immutable OnDiskAccount");
        ints[IntValues.NUM_POSITIVE_BALANCES] = value;
    }

    public int getNumTreasuryTitles() {
        return ints[IntValues.NUM_TREASURY_TITLES];
    }

    @StateSetter
    public void setNumTreasuryTitles(final int value) {
        throwIfImmutable("Tried to set NUM_TREASURY_TITLES on an immutable OnDiskAccount");
        ints[IntValues.NUM_TREASURY_TITLES] = value;
    }

    // Long getters and setters
    public long getExpiry() {
        return longs[LongValues.EXPIRY];
    }

    @StateSetter
    public void setExpiry(final long value) {
        throwIfImmutable("Tried to set ]; on an immutable OnDiskAccount");
        longs[LongValues.EXPIRY] = value;
    }

    public long getHbarBalance() {
        return longs[LongValues.HBAR_BALANCE];
    }

    @StateSetter
    public void setHbarBalance(final long value) {
        throwIfImmutable("Tried to set HBAR_BALANCE on an immutable OnDiskAccount");
        longs[LongValues.HBAR_BALANCE] = value;
    }

    public long getAutoRenewSecs() {
        return longs[LongValues.AUTO_RENEW_SECS];
    }

    @StateSetter
    public void setAutoRenewSecs(final long value) {
        throwIfImmutable("Tried to set AUTO_RENEW_SECS on an immutable OnDiskAccount");
        longs[LongValues.AUTO_RENEW_SECS] = value;
    }

    public long getNftsOwned() {
        return longs[LongValues.NFTS_OWNED];
    }

    @StateSetter
    public void setNftsOwned(final long value) {
        throwIfImmutable("Tried to set NFTS_OWNED on an immutable OnDiskAccount");
        longs[LongValues.NFTS_OWNED] = value;
    }

    public long getAccountNumber() {
        return longs[LongValues.ACCOUNT_NUMBER];
    }

    @StateSetter
    public void setAccountNumber(final long value) {
        throwIfImmutable("Tried to set ACCOUNT_NUMBER on an immutable OnDiskAccount");
        longs[LongValues.ACCOUNT_NUMBER] = value;
    }

    public long getHeadTokenId() {
        return longs[LongValues.HEAD_TOKEN_ID];
    }

    @StateSetter
    public void setHeadTokenId(final long value) {
        throwIfImmutable("Tried to set HEAD_TOKEN_ID on an immutable onDiskAccount");
        longs[LongValues.HEAD_TOKEN_ID] = value;
    }

    public long getHeadNftId() {
        return longs[LongValues.HEAD_NFT_ID];
    }

    @StateSetter
    public void setHeadNftId(final long value) {
        throwIfImmutable("Tried to set HEAD_NFT_ID on an immutable OnDiskAccount");
        longs[LongValues.HEAD_NFT_ID] = value;
    }

    public long getHeadNftSerialNum() {
        return longs[LongValues.HEAD_NFT_SERIAL_NUM];
    }

    @StateSetter
    public void setHeadNftSerialNum(final long value) {
        throwIfImmutable("Tried to set HEAD_NFT_SERIAL_NUM on an immutable OnDiskAccount");
        longs[LongValues.HEAD_NFT_SERIAL_NUM] = value;
    }

    public long getEthereumNonce() {
        return longs[LongValues.ETHEREUM_NONCE];
    }

    @StateSetter
    public void setEthereumNonce(final long value) {
        throwIfImmutable("Tried to set ETHEREUM_NONCE on an immutable OnDiskAccount");
        longs[LongValues.ETHEREUM_NONCE] = value;
    }

    public long getStakedToMe() {
        return longs[LongValues.STAKED_TO_ME];
    }

    @StateSetter
    public void setStakedToMe(final long value) {
        throwIfImmutable("Tried to set STAKED_TO_ME on an immutable OnDiskAccount");
        longs[LongValues.STAKED_TO_ME] = value;
    }

    public long getStakePeriodStart() {
        return longs[LongValues.STAKE_PERIOD_START];
    }

    @StateSetter
    public void setStakePeriodStart(final long value) {
        throwIfImmutable("Tried to set STAKE_PERIOD_START on an immutable OnDiskAccount");
        longs[LongValues.STAKE_PERIOD_START] = value;
    }

    public long getStakedNum() {
        return longs[LongValues.STAKED_NUM];
    }

    @StateSetter
    public void setStakedNum(final long value) {
        throwIfImmutable("Tried to set STAKED_NUM on an immutable OnDiskAccount");
        longs[LongValues.STAKED_NUM] = value;
    }

    public long getStakeAtStartOfLastRewardedPeriod() {
        return longs[LongValues.STAKE_AT_START_OF_LAST_REWARDED_PERIOD];
    }

    @StateSetter
    public void setStakeAtStartOfLastRewardedPeriod(final long value) {
        throwIfImmutable("Tried to set STAKE_AT_START_OF_LAST_REWARDED_PERIOD on an immutable OnDiskAccount");
        longs[LongValues.STAKE_AT_START_OF_LAST_REWARDED_PERIOD] = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OnDiskAccount that = (OnDiskAccount) o;
        return flags == that.flags && Arrays.equals(ints, that.ints) && Arrays.equals(longs, that.longs);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(flags);
        result = 31 * result + Arrays.hashCode(ints);
        result = 31 * result + Arrays.hashCode(longs);
        return result;
    }

    private static final class Masks {
        private static final byte IS_DELETED = 1;
        private static final byte IS_CONTRACT = 2;
        private static final byte IS_RECEIVER_SIG_REQUIRED = 3;
    }

    private static final class IntValues {
        private static final int NUM_CONTRACT_KV_PAIRS = 0;
        private static final int MAX_AUTO_ASSOCIATIONS = 1;
        private static final int USED_AUTO_ASSOCIATIONS = 2;
        private static final int NUM_ASSOCIATIONS = 3;
        private static final int NUM_POSITIVE_BALANCES = 4;
        private static final int NUM_TREASURY_TITLES = 5;
        private static final int COUNT = 6;
    }

    private static final class LongValues {
        private static final int EXPIRY = 0;
        private static final int HBAR_BALANCE = 1;
        private static final int AUTO_RENEW_SECS = 2;
        private static final int NFTS_OWNED = 3;
        private static final int ACCOUNT_NUMBER = 4;
        private static final int HEAD_TOKEN_ID = 5;
        private static final int HEAD_NFT_ID = 6;
        private static final int HEAD_NFT_SERIAL_NUM = 7;
        private static final int ETHEREUM_NONCE = 8;
        private static final int STAKED_TO_ME = 9;
        private static final int STAKE_PERIOD_START = 10;
        private static final int STAKED_NUM = 11;
        private static final int STAKE_AT_START_OF_LAST_REWARDED_PERIOD = 12;
        private static final int COUNT = 13;
    }
}
