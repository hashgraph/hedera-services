/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual.entities;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.legacy.proto.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
import static com.hedera.services.legacy.proto.utils.ByteStringUtils.wrapUnsafely;
import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.virtual.KeyPackingUtils.*;
import static com.hedera.services.state.virtual.utils.EntityIoUtils.readBytes;
import static com.hedera.services.state.virtual.utils.EntityIoUtils.writeBytes;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.SerializationUtils.*;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.annotations.StateSetter;
import com.hedera.services.state.virtual.utils.CheckedConsumer;
import com.hedera.services.state.virtual.utils.CheckedSupplier;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.jetbrains.annotations.Nullable;

public class OnDiskAccount implements VirtualValue, HederaAccount {
    private static final int CURRENT_VERSION = 1;
    private static final long CLASS_ID = 0xc88e3a5c7b497468L;

    private static final String EMPTY_MEMO = "";
    private static final JKey HOLLOW_KEY;

    static {
        HOLLOW_KEY =
                asFcKeyUnchecked(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
    }

    private byte flags;
    private JKey key = HOLLOW_KEY;
    private String memo = EMPTY_MEMO;
    private ByteString alias = ByteString.EMPTY;
    private Map<EntityNum, Long> hbarAllowances = Collections.emptyMap();
    private Map<FcTokenAllowanceId, Long> fungibleAllowances = Collections.emptyMap();
    private Set<FcTokenAllowanceId> nftOperatorApprovals = Collections.emptySet();

    // Null if a non-contract account, or a contract without storage
    private int[] firstStorageKey = null;
    // Number of the low-order bytes in firstStorageKey that contain ones
    private byte firstStorageKeyNonZeroBytes;
    private final int[] ints = new int[IntValues.COUNT];
    private final long[] longs = new long[LongValues.COUNT];

    private boolean immutable = false;

    public OnDiskAccount() {
        // Some non-zero default values
        longs[LongValues.STAKE_PERIOD_START] = -1;
        longs[LongValues.STAKE_AT_START_OF_LAST_REWARDED_PERIOD] = -1;
    }

    public OnDiskAccount(final OnDiskAccount that) {
        this.key = that.key;
        this.memo = that.memo;
        this.flags = that.flags;
        this.alias = that.alias;
        this.hbarAllowances = that.hbarAllowances;
        this.fungibleAllowances = that.fungibleAllowances;
        this.nftOperatorApprovals = that.nftOperatorApprovals;
        System.arraycopy(that.ints, 0, this.ints, 0, IntValues.COUNT);
        System.arraycopy(that.longs, 0, this.longs, 0, LongValues.COUNT);
    }

    public static OnDiskAccount from(final MerkleAccountState inMemoryAccount) {
        final var onDiskAccount = new OnDiskAccount();

        // Objects
        onDiskAccount.setMemo(inMemoryAccount.memo());
        onDiskAccount.setAlias(inMemoryAccount.getAlias());
        onDiskAccount.setHbarAllowances(inMemoryAccount.getCryptoAllowances());
        onDiskAccount.setFungibleAllowances(inMemoryAccount.getFungibleTokenAllowances());
        onDiskAccount.setNftOperatorApprovals(inMemoryAccount.getApproveForAllNftsUnsafe());
        onDiskAccount.setKey(inMemoryAccount.key());
        // Flags
        onDiskAccount.setIsDeleted(inMemoryAccount.isDeleted());
        onDiskAccount.setIsContract(inMemoryAccount.isSmartContract());
        onDiskAccount.setIsDeclineReward(inMemoryAccount.isDeclineReward());
        onDiskAccount.setIsReceiverSigRequired(inMemoryAccount.isReceiverSigRequired());
        // Ints
        onDiskAccount.setNumContractKvPairs(inMemoryAccount.getNumContractKvPairs());
        onDiskAccount.setMaxAutoAssociations(inMemoryAccount.getMaxAutomaticAssociations());
        onDiskAccount.setUsedAutoAssociations(inMemoryAccount.getUsedAutomaticAssociations());
        onDiskAccount.setNumAssociations(inMemoryAccount.getNumAssociations());
        onDiskAccount.setNumPositiveBalances(inMemoryAccount.getNumPositiveBalances());
        onDiskAccount.setNumTreasuryTitles(inMemoryAccount.getNumTreasuryTitles());
        // Longs
        onDiskAccount.setExpiry(inMemoryAccount.expiry());
        onDiskAccount.setHbarBalance(inMemoryAccount.balance());
        onDiskAccount.setAutoRenewSecs(inMemoryAccount.autoRenewSecs());
        onDiskAccount.setNftsOwned(inMemoryAccount.nftsOwned());
        onDiskAccount.setAccountNumber(inMemoryAccount.number());
        onDiskAccount.setHeadTokenId(inMemoryAccount.getHeadTokenId());
        onDiskAccount.setHeadNftId(inMemoryAccount.getHeadNftId());
        onDiskAccount.setHeadNftSerialNum(inMemoryAccount.getHeadNftSerialNum());
        onDiskAccount.setEthereumNonce(inMemoryAccount.ethereumNonce());
        onDiskAccount.setStakedToMe(inMemoryAccount.getStakedToMe());
        onDiskAccount.setStakePeriodStart(inMemoryAccount.getStakePeriodStart());
        onDiskAccount.setStakedNum(inMemoryAccount.getStakedNum());
        onDiskAccount.setStakeAtStartOfLastRewardedPeriod(
                inMemoryAccount.getStakeAtStartOfLastRewardedPeriod());
        if (inMemoryAccount.hasAutoRenewAccount()) {
            onDiskAccount.setAutoRenewAccountNumber(inMemoryAccount.getAutoRenewAccount().num());
        }
        // Complex
        onDiskAccount.setFirstStorageKey(inMemoryAccount.getFirstUint256Key());

        return onDiskAccount;
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
        serializeTo(to::put, to::putInt, to::putLong, data -> to.put(data, 0, data.length));
    }

    @Override
    public void deserialize(final ByteBuffer from, final int version) throws IOException {
        deserializeFrom(from::get, from::getInt, from::getLong, from::get);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeTo(
                out::writeByte,
                out::writeInt,
                out::writeLong,
                data -> out.write(data, 0, data.length));
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        deserializeFrom(in::readByte, in::readInt, in::readLong, in::readFully);
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
            final CheckedConsumer<Long> writeLongFn,
            final CheckedConsumer<byte[]> writeBytesFn)
            throws IOException {
        writeByteFn.accept(flags);
        for (final var v : ints) {
            writeIntFn.accept(v);
        }
        for (final var v : longs) {
            writeLongFn.accept(v);
        }
        writeBytes(serializedVariablePart(), writeIntFn, writeBytesFn);
    }

    private void deserializeFrom(
            final CheckedSupplier<Byte> readByteFn,
            final CheckedSupplier<Integer> readIntFn,
            final CheckedSupplier<Long> readLongFn,
            final CheckedConsumer<byte[]> readBytesFn)
            throws IOException {
        throwIfImmutable();
        flags = readByteFn.get();
        for (var i = 0; i < IntValues.COUNT; i++) {
            ints[i] = readIntFn.get();
        }
        for (var i = 0; i < LongValues.COUNT; i++) {
            longs[i] = readLongFn.get();
        }
        final var variableSource = readBytes(readIntFn, readBytesFn);
        deserializeVariablePart(variableSource);
    }

    private byte[] serializedVariablePart() throws IOException {
        try (final var baos = new ByteArrayOutputStream()) {
            try (final var out = new SerializableDataOutputStream(baos)) {
                out.write(key.serialize());
                out.writeNormalisedString(memo);
                out.writeByteArray(unwrapUnsafelyIfPossible(alias));
                serializeHbarAllowances(out, hbarAllowances);
                serializeFungibleAllowances(out, fungibleAllowances);
                serializeNftOperatorApprovals(out, nftOperatorApprovals);
                if (isContract()) {
                    serializePossiblyMissingKey(firstStorageKey, firstStorageKeyNonZeroBytes, out);
                }
                out.flush();
            }
            baos.flush();
            return baos.toByteArray();
        }
    }

    private void deserializeVariablePart(final byte[] source) throws IOException {
        try (final var bais = new ByteArrayInputStream(source)) {
            try (final var in = new SerializableDataInputStream(bais)) {
                key = JKeySerializer.deserialize(in);
                memo = in.readNormalisedString(Integer.MAX_VALUE);
                alias = wrapUnsafely(in.readByteArray(Integer.MAX_VALUE));
                hbarAllowances = deserializeHbarAllowances(in);
                fungibleAllowances = deserializeFungibleAllowances(in);
                nftOperatorApprovals = deserializeNftOperatorApprovals(in);
                if (isContract()) {
                    final var marker = in.readByte();
                    if (marker != MISSING_KEY_SENTINEL) {
                        firstStorageKeyNonZeroBytes = marker;
                        firstStorageKey =
                                deserializeUint256Key(
                                        firstStorageKeyNonZeroBytes,
                                        in,
                                        SerializableDataInputStream::readByte);
                    }
                }
            }
        }
    }

    // Object getters and setters
    public JKey getKey() {
        return key;
    }

    @StateSetter
    public void setKey(final JKey key) {
        throwIfImmutable("Tried to set the key on an immutable OnDiskAccount");
        this.key = key;
    }

    public String getMemo() {
        return memo;
    }

    @StateSetter
    public void setMemo(final String memo) {
        throwIfImmutable("Tried to set the memo on an immutable OnDiskAccount");
        this.memo = memo;
    }

    public ByteString getAlias() {
        return alias;
    }

    @StateSetter
    public void setAlias(final ByteString alias) {
        throwIfImmutable("Tried to set the alias on an immutable OnDiskAccount");
        this.alias = alias;
    }

    public Map<EntityNum, Long> getHbarAllowances() {
        return hbarAllowances;
    }

    @StateSetter
    public void setHbarAllowances(final Map<EntityNum, Long> hbarAllowances) {
        throwIfImmutable("Tried to set the hbar allowances on an immutable OnDiskAccount");
        this.hbarAllowances = hbarAllowances;
    }

    public Map<FcTokenAllowanceId, Long> getFungibleAllowances() {
        return fungibleAllowances;
    }

    @StateSetter
    public void setFungibleAllowances(final Map<FcTokenAllowanceId, Long> fungibleAllowances) {
        throwIfImmutable("Tried to set the fungible allowances on an immutable OnDiskAccount");
        this.fungibleAllowances = fungibleAllowances;
    }

    public Set<FcTokenAllowanceId> getNftOperatorApprovals() {
        return nftOperatorApprovals;
    }

    @StateSetter
    public void setNftOperatorApprovals(final Set<FcTokenAllowanceId> nftOperatorApprovals) {
        throwIfImmutable("Tried to set the NFT operator approvals on an immutable OnDiskAccount");
        this.nftOperatorApprovals = nftOperatorApprovals;
    }

    // Misc getters and setters
    public int[] getFirstStorageKey() {
        return firstStorageKey;
    }

    @StateSetter
    public void setFirstStorageKey(final int[] firstStorageKey) {
        throwIfImmutable("Tried to set the first storage key on an immutable OnDiskAccount");
        this.firstStorageKey = firstStorageKey;
        if (firstStorageKey != null) {
            firstStorageKeyNonZeroBytes = computeNonZeroBytes(firstStorageKey);
        } else {
            firstStorageKeyNonZeroBytes = 0;
        }
    }

    public byte getFirstStorageKeyNonZeroBytes() {
        return firstStorageKeyNonZeroBytes;
    }

    // Boolean getters and setters
    public boolean isDeleted() {
        return (flags & Masks.IS_DELETED) != 0;
    }

    @StateSetter
    public void setIsDeleted(final boolean flag) {
        throwIfImmutable("Tried to set IS_DELETED on an immutable OnDiskAccount");
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
        throwIfImmutable("Tried to set IS_CONTRACT on an immutable OnDiskAccount");
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
        throwIfImmutable("Tried to set IS_RECEIVER_SIG_REQUIRED on an immutable OnDiskAccount");
        if (flag) {
            flags |= Masks.IS_RECEIVER_SIG_REQUIRED;
        } else {
            flags &= ~Masks.IS_RECEIVER_SIG_REQUIRED;
        }
    }

    public boolean isDeclineReward() {
        return (flags & Masks.IS_DECLINE_REWARD) != 0;
    }

    @StateSetter
    public void setIsDeclineReward(final boolean flag) {
        throwIfImmutable("Tried to set IS_DECLINE_REWARD on an immutable OnDiskAccount");
        if (flag) {
            flags |= Masks.IS_DECLINE_REWARD;
        } else {
            flags &= ~Masks.IS_DECLINE_REWARD;
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
        throwIfImmutable(
                "Tried to set STAKE_AT_START_OF_LAST_REWARDED_PERIOD on an immutable"
                        + " OnDiskAccount");
        longs[LongValues.STAKE_AT_START_OF_LAST_REWARDED_PERIOD] = value;
    }

    public long getAutoRenewAccountNumber() {
        return longs[LongValues.AUTO_RENEW_ACCOUNT_NUMBER];
    }

    @StateSetter
    public void setAutoRenewAccountNumber(final long value) {
        throwIfImmutable("Tried to set AUTO_RENEW_ACCOUNT_NUMBER on an immutable OnDiskAccount");
        longs[LongValues.AUTO_RENEW_ACCOUNT_NUMBER] = value;
    }

    // Backward compatability
    @Override
    public boolean isTokenTreasury() {
        return getNumTreasuryTitles() > 0;
    }

    @Override
    public boolean isSmartContract() {
        return isContract();
    }

    @Override
    public void setSmartContract(final boolean smartContract) {
        setIsContract(smartContract);
    }

    @Override
    public long getHeadNftTokenNum() {
        return getHeadNftId();
    }

    @Override
    public EntityNumPair getHeadNftKey() {
        return EntityNumPair.fromLongs(getHeadNftTokenNum(), getHeadNftSerialNum());
    }

    @Override
    public EntityNumPair getLatestAssociation() {
        return EntityNumPair.fromLongs(getAccountNumber(), getHeadTokenId());
    }

    @Override
    public long getBalance() {
        return getHbarBalance();
    }

    @Override
    public void setBalance(final long balance) throws NegativeAccountBalanceException {
        if (balance < 0) {
            throw new NegativeAccountBalanceException("Illegal balance " + balance);
        }
        throwIfImmutable();
        setHbarBalance(balance);
    }

    @Override
    public void setBalanceUnchecked(final long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Illegal balance " + balance);
        }
        throwIfImmutable();
        setHbarBalance(balance);
    }

    @Override
    public void setReceiverSigRequired(final boolean receiverSigRequired) {
        setIsReceiverSigRequired(receiverSigRequired);
    }

    @Override
    public JKey getAccountKey() {
        return getKey();
    }

    @Override
    public void setAccountKey(final JKey key) {
        setKey(key);
    }

    @Override
    public int number() {
        return codeFromNum(getAccountNumber());
    }

    @Override
    public EntityId getProxy() {
        return MISSING_ENTITY_ID;
    }

    @Override
    public void setProxy(EntityId proxy) {
        // Intentional no-op
    }

    @Override
    public void setDeleted(final boolean deleted) {
        setIsDeleted(deleted);
    }

    @Override
    public int getMaxAutomaticAssociations() {
        return getMaxAutoAssociations();
    }

    @Override
    public void setMaxAutomaticAssociations(final int maxAutomaticAssociations) {
        setMaxAutoAssociations(maxAutomaticAssociations);
    }

    @Override
    public void setUsedAutomaticAssociations(int usedAutoAssociations) {
        setUsedAutoAssociations(usedAutoAssociations);
    }

    @Override
    public ContractKey getFirstContractStorageKey() {
        return firstStorageKey == null
                ? null
                : new ContractKey(getAccountNumber(), firstStorageKey);
    }

    @Override
    public int[] getFirstUint256Key() {
        return getFirstStorageKey();
    }

    @Override
    public void setFirstUint256StorageKey(final int[] firstUint256Key) {
        setFirstStorageKey(firstUint256Key);
    }

    @Override
    public Map<EntityNum, Long> getCryptoAllowances() {
        return getHbarAllowances();
    }

    @Override
    public void setCryptoAllowances(final SortedMap<EntityNum, Long> cryptoAllowances) {
        setHbarAllowances(cryptoAllowances);
    }

    @Override
    public Map<EntityNum, Long> getCryptoAllowancesUnsafe() {
        return getHbarAllowances();
    }

    @Override
    public void setCryptoAllowancesUnsafe(final Map<EntityNum, Long> cryptoAllowances) {
        setHbarAllowances(cryptoAllowances);
    }

    @Override
    public Set<FcTokenAllowanceId> getApproveForAllNfts() {
        return getNftOperatorApprovals();
    }

    @Override
    public void setApproveForAllNfts(final Set<FcTokenAllowanceId> approveForAllNfts) {
        setNftOperatorApprovals(approveForAllNfts);
    }

    @Override
    public Set<FcTokenAllowanceId> getApproveForAllNftsUnsafe() {
        return getNftOperatorApprovals();
    }

    @Override
    public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
        return getFungibleAllowances();
    }

    @Override
    public void setFungibleTokenAllowances(
            final SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        setFungibleAllowances(fungibleTokenAllowances);
    }

    @Override
    public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowancesUnsafe() {
        return getFungibleAllowances();
    }

    @Override
    public void setFungibleTokenAllowancesUnsafe(
            final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        setFungibleAllowances(fungibleTokenAllowances);
    }

    @Override
    public boolean isDeclinedReward() {
        return isDeclineReward();
    }

    @Override
    public void setDeclineReward(final boolean declineReward) {
        setIsDeclineReward(declineReward);
    }

    @Override
    public boolean hasBeenRewardedSinceLastStakeMetaChange() {
        return getStakeAtStartOfLastRewardedPeriod() != -1L;
    }

    @Override
    public long totalStakeAtStartOfLastRewardedPeriod() {
        return getStakeAtStartOfLastRewardedPeriod();
    }

    @Override
    public long totalStake() {
        return getHbarBalance() + getStakedToMe();
    }

    @Override
    public long getStakedId() {
        return getStakedNum();
    }

    @Override
    public void setStakedId(final long stakedId) {
        setStakedNum(stakedId);
    }

    @Override
    public boolean mayHavePendingReward() {
        return getStakedId() < 0 && !isDeclinedReward();
    }

    @Override
    public long getStakedNodeAddressBookId() {
        if (getStakedNum() >= 0) {
            throw new IllegalStateException("Account is not staked to a node");
        }
        return -getStakedNum() - 1;
    }

    @Override
    public boolean hasAlias() {
        return !getAlias().isEmpty();
    }

    @Override
    public boolean hasAutoRenewAccount() {
        return getAutoRenewAccountNumber() > 0L;
    }

    @Nullable
    @Override
    public EntityId getAutoRenewAccount() {
        return hasAutoRenewAccount()
                ? STATIC_PROPERTIES.scopedEntityIdWith(getAutoRenewAccountNumber())
                : null;
    }

    @Override
    public void setAutoRenewAccount(@Nullable final EntityId autoRenewAccount) {
        final var effNum = (autoRenewAccount == null) ? 0L : autoRenewAccount.num();
        setAutoRenewAccountNumber(effNum);
    }

    @Override
    public void setEntityNum(final EntityNum num) {
        setAccountNumber(num.longValue());
    }

    // Generated code
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OnDiskAccount that = (OnDiskAccount) o;
        return flags == that.flags
                && firstStorageKeyNonZeroBytes == that.firstStorageKeyNonZeroBytes
                && equalUpToDecodability(this.key, that.key)
                && memo.equals(that.memo)
                && alias.equals(that.alias)
                && hbarAllowances.equals(that.hbarAllowances)
                && fungibleAllowances.equals(that.fungibleAllowances)
                && nftOperatorApprovals.equals(that.nftOperatorApprovals)
                && Arrays.equals(firstStorageKey, that.firstStorageKey)
                && Arrays.equals(ints, that.ints)
                && Arrays.equals(longs, that.longs);
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(
                        flags,
                        key,
                        memo,
                        alias,
                        hbarAllowances,
                        fungibleAllowances,
                        nftOperatorApprovals,
                        firstStorageKeyNonZeroBytes);
        result = 31 * result + Arrays.hashCode(firstStorageKey);
        result = 31 * result + Arrays.hashCode(ints);
        result = 31 * result + Arrays.hashCode(longs);
        return result;
    }

    private static final class Masks {
        private static final byte IS_DELETED = 1;
        private static final byte IS_CONTRACT = 1 << 1;
        private static final byte IS_RECEIVER_SIG_REQUIRED = 1 << 2;
        private static final byte IS_DECLINE_REWARD = 1 << 3;
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
        private static final int AUTO_RENEW_ACCOUNT_NUMBER = 13;
        private static final int COUNT = 14;
    }
}
