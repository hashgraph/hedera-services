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
package com.hedera.services.state.merkle;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableSerializable;
import static com.hedera.services.utils.MiscUtils.describe;
import static java.util.Collections.unmodifiableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.hedera.services.context.properties.StaticPropertiesHolder;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.serdes.IoUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

public class MerkleToken extends PartialMerkleLeaf implements Keyed<EntityNum>, MerkleLeaf {
    static final int RELEASE_0160_VERSION = 3;
    static final int RELEASE_0180_VERSION = 4;
    static final int RELEASE_0190_VERSION = 5;

    static final int CURRENT_VERSION = RELEASE_0190_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xd23ce8814b35fc2fL;

    private static final long UNUSED_AUTO_RENEW_PERIOD = -1L;
    private static final int UPPER_BOUND_MEMO_UTF8_BYTES = 1024;

    public static final JKey UNUSED_KEY = null;
    public static final int UPPER_BOUND_SYMBOL_UTF8_BYTES = 1024;
    public static final int UPPER_BOUND_TOKEN_NAME_UTF8_BYTES = 1024;

    private TokenType tokenType;
    private TokenSupplyType supplyType;
    private int decimals;
    private long lastUsedSerialNumber;
    private long expiry;
    private long maxSupply;
    private long totalSupply;
    private long autoRenewPeriod = UNUSED_AUTO_RENEW_PERIOD;
    private JKey adminKey = UNUSED_KEY;
    private JKey kycKey = UNUSED_KEY;
    private JKey wipeKey = UNUSED_KEY;
    private JKey supplyKey = UNUSED_KEY;
    private JKey freezeKey = UNUSED_KEY;
    private JKey feeScheduleKey = UNUSED_KEY;
    private JKey pauseKey = UNUSED_KEY;
    private String symbol;
    private String name;
    private String memo = DEFAULT_MEMO;
    private boolean deleted;
    private boolean accountsFrozenByDefault;
    private boolean accountsKycGrantedByDefault;
    private boolean paused;
    private EntityId treasury;
    private EntityId autoRenewAccount = null;
    private List<FcCustomFee> feeSchedule = Collections.emptyList();
    private int number;

    public MerkleToken() {
        /* No-op. */
    }

    public MerkleToken(
            long expiry,
            long totalSupply,
            int decimals,
            String symbol,
            String name,
            boolean accountsFrozenByDefault,
            boolean accountKycGrantedByDefault,
            EntityId treasury) {
        this.expiry = expiry;
        this.totalSupply = totalSupply;
        this.decimals = decimals;
        this.symbol = symbol;
        this.name = name;
        this.accountsFrozenByDefault = accountsFrozenByDefault;
        this.accountsKycGrantedByDefault = accountKycGrantedByDefault;
        this.treasury = treasury;
    }

    public MerkleToken(
            long expiry,
            long totalSupply,
            int decimals,
            String symbol,
            String name,
            boolean accountsFrozenByDefault,
            boolean accountKycGrantedByDefault,
            EntityId treasury,
            int number) {
        this.expiry = expiry;
        this.totalSupply = totalSupply;
        this.decimals = decimals;
        this.symbol = symbol;
        this.name = name;
        this.accountsFrozenByDefault = accountsFrozenByDefault;
        this.accountsKycGrantedByDefault = accountKycGrantedByDefault;
        this.treasury = treasury;
        this.number = number;
    }

    /* Object */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleToken.class != o.getClass()) {
            return false;
        }

        var that = (MerkleToken) o;
        return this.tokenType == that.tokenType
                && this.supplyType == that.supplyType
                && this.expiry == that.expiry
                && this.autoRenewPeriod == that.autoRenewPeriod
                && this.deleted == that.deleted
                && this.maxSupply == that.maxSupply
                && this.totalSupply == that.totalSupply
                && this.decimals == that.decimals
                && this.lastUsedSerialNumber == that.lastUsedSerialNumber
                && this.accountsFrozenByDefault == that.accountsFrozenByDefault
                && this.accountsKycGrantedByDefault == that.accountsKycGrantedByDefault
                && this.number == that.number
                && this.paused == that.paused
                && Objects.equals(this.symbol, that.symbol)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.memo, that.memo)
                && Objects.equals(this.treasury, that.treasury)
                && Objects.equals(this.autoRenewAccount, that.autoRenewAccount)
                && equalUpToDecodability(this.wipeKey, that.wipeKey)
                && equalUpToDecodability(this.supplyKey, that.supplyKey)
                && equalUpToDecodability(this.adminKey, that.adminKey)
                && equalUpToDecodability(this.freezeKey, that.freezeKey)
                && equalUpToDecodability(this.kycKey, that.kycKey)
                && equalUpToDecodability(this.feeScheduleKey, that.feeScheduleKey)
                && equalUpToDecodability(this.pauseKey, that.pauseKey)
                && Objects.equals(this.feeSchedule, that.feeSchedule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                tokenType,
                supplyType,
                expiry,
                deleted,
                maxSupply,
                totalSupply,
                decimals,
                lastUsedSerialNumber,
                number,
                adminKey,
                freezeKey,
                kycKey,
                wipeKey,
                supplyKey,
                pauseKey,
                symbol,
                name,
                memo,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                paused,
                treasury,
                autoRenewAccount,
                autoRenewPeriod,
                feeSchedule,
                feeScheduleKey);
    }

    /* --- Bean --- */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleToken.class)
                .omitNullValues()
                .add("number", number + " <-> " + EntityIdUtils.asIdLiteral(number))
                .add("tokenType", tokenType)
                .add("supplyType", supplyType)
                .add("deleted", deleted)
                .add("expiry", expiry)
                .add("symbol", symbol)
                .add("name", name)
                .add("memo", memo)
                .add("treasury", readableEntityId(treasury))
                .add("maxSupply", maxSupply)
                .add("totalSupply", totalSupply)
                .add("decimals", decimals)
                .add("lastUsedSerialNumber", lastUsedSerialNumber)
                .add("autoRenewAccount", readableEntityId(autoRenewAccount))
                .add("autoRenewPeriod", autoRenewPeriod)
                .add("adminKey", describe(adminKey))
                .add("kycKey", describe(kycKey))
                .add("wipeKey", describe(wipeKey))
                .add("supplyKey", describe(supplyKey))
                .add("freezeKey", describe(freezeKey))
                .add("pauseKey", describe(pauseKey))
                .add("accountsKycGrantedByDefault", accountsKycGrantedByDefault)
                .add("accountsFrozenByDefault", accountsFrozenByDefault)
                .add("pauseStatus", paused)
                .add("feeSchedules", feeSchedule)
                .add("feeScheduleKey", feeScheduleKey)
                .toString();
    }

    private String readableEntityId(@Nullable final EntityId id) {
        return Optional.ofNullable(id).map(EntityId::toAbbrevString).orElse("<N/A>");
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        deleted = in.readBoolean();
        expiry = in.readLong();
        autoRenewAccount = readNullableSerializable(in);
        autoRenewPeriod = in.readLong();
        symbol = in.readNormalisedString(UPPER_BOUND_SYMBOL_UTF8_BYTES);
        name = in.readNormalisedString(UPPER_BOUND_TOKEN_NAME_UTF8_BYTES);
        treasury = in.readSerializable();
        totalSupply = in.readLong();
        decimals = in.readInt();
        accountsFrozenByDefault = in.readBoolean();
        accountsKycGrantedByDefault = in.readBoolean();
        adminKey = readNullable(in, JKeySerializer::deserialize);
        freezeKey = readNullable(in, JKeySerializer::deserialize);
        kycKey = readNullable(in, JKeySerializer::deserialize);
        supplyKey = readNullable(in, JKeySerializer::deserialize);
        wipeKey = readNullable(in, JKeySerializer::deserialize);
        /* Memo present since 0.12.0 */
        memo = in.readNormalisedString(UPPER_BOUND_MEMO_UTF8_BYTES);
        // Added in 0.16
        tokenType = TokenType.values()[in.readInt()];
        supplyType = TokenSupplyType.values()[in.readInt()];
        maxSupply = in.readLong();
        lastUsedSerialNumber = in.readLong();
        feeSchedule =
                unmodifiableList(
                        in.readSerializableList(Integer.MAX_VALUE, true, FcCustomFee::new));
        feeScheduleKey = readNullable(in, JKeySerializer::deserialize);
        // Added in 0.18
        number = in.readInt();
        // Added in 0.19
        pauseKey = readNullable(in, JKeySerializer::deserialize);
        paused = in.readBoolean();
        if (tokenType == null) {
            tokenType = TokenType.FUNGIBLE_COMMON;
        }
        if (supplyType == null) {
            supplyType = TokenSupplyType.INFINITE;
        }
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(deleted);
        out.writeLong(expiry);
        writeNullableSerializable(autoRenewAccount, out);
        out.writeLong(autoRenewPeriod);
        out.writeNormalisedString(symbol);
        out.writeNormalisedString(name);
        out.writeSerializable(treasury, true);
        out.writeLong(totalSupply);
        out.writeInt(decimals);
        out.writeBoolean(accountsFrozenByDefault);
        out.writeBoolean(accountsKycGrantedByDefault);
        writeNullable(adminKey, out, IoUtils::serializeKey);
        writeNullable(freezeKey, out, IoUtils::serializeKey);
        writeNullable(kycKey, out, IoUtils::serializeKey);
        writeNullable(supplyKey, out, IoUtils::serializeKey);
        writeNullable(wipeKey, out, IoUtils::serializeKey);
        out.writeNormalisedString(memo);
        out.writeInt(tokenType.ordinal());
        out.writeInt(supplyType.ordinal());
        out.writeLong(maxSupply);
        out.writeLong(lastUsedSerialNumber);
        out.writeSerializableList(feeSchedule, true, true);
        writeNullable(feeScheduleKey, out, IoUtils::serializeKey);
        out.writeInt(number);
        writeNullable(pauseKey, out, IoUtils::serializeKey);
        out.writeBoolean(paused);
    }

    /* --- FastCopyable --- */
    @Override
    public MerkleToken copy() {
        setImmutable(true);
        var fc =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        accountsFrozenByDefault,
                        accountsKycGrantedByDefault,
                        treasury,
                        number);
        fc.setMemo(memo);
        fc.setDeleted(deleted);
        fc.setFeeSchedule(feeSchedule);
        fc.setAutoRenewPeriod(autoRenewPeriod);
        fc.setAutoRenewAccount(autoRenewAccount);
        fc.lastUsedSerialNumber = lastUsedSerialNumber;
        fc.setTokenType(tokenType);
        fc.setSupplyType(supplyType);
        fc.setMaxSupply(maxSupply);
        fc.setPaused(paused);
        if (adminKey != UNUSED_KEY) {
            fc.setAdminKey(adminKey);
        }
        if (freezeKey != UNUSED_KEY) {
            fc.setFreezeKey(freezeKey);
        }
        if (kycKey != UNUSED_KEY) {
            fc.setKycKey(kycKey);
        }
        if (wipeKey != UNUSED_KEY) {
            fc.setWipeKey(wipeKey);
        }
        if (supplyKey != UNUSED_KEY) {
            fc.setSupplyKey(supplyKey);
        }
        if (feeScheduleKey != UNUSED_KEY) {
            fc.setFeeScheduleKey(feeScheduleKey);
        }
        if (pauseKey != UNUSED_KEY) {
            fc.setPauseKey(pauseKey);
        }
        return fc;
    }

    /* --- Bean --- */
    public long totalSupply() {
        return totalSupply;
    }

    public int decimals() {
        return decimals;
    }

    public boolean hasAdminKey() {
        return adminKey != UNUSED_KEY;
    }

    public Optional<JKey> adminKey() {
        return Optional.ofNullable(adminKey);
    }

    public Optional<JKey> freezeKey() {
        return Optional.ofNullable(freezeKey);
    }

    public JKey freezeKeyUnsafe() {
        return freezeKey;
    }

    public boolean hasFreezeKey() {
        return freezeKey != UNUSED_KEY;
    }

    public Optional<JKey> kycKey() {
        return Optional.ofNullable(kycKey);
    }

    public boolean hasKycKey() {
        return kycKey != UNUSED_KEY;
    }

    public Optional<JKey> pauseKey() {
        return Optional.ofNullable(pauseKey);
    }

    public boolean hasPauseKey() {
        return pauseKey != UNUSED_KEY;
    }

    public void setPauseKey(JKey pauseKey) {
        throwIfImmutable("Cannot change this token's pause key if it's immutable.");
        this.pauseKey = pauseKey;
    }

    public void setFreezeKey(JKey freezeKey) {
        throwIfImmutable("Cannot change this token's freeze key if it's immutable.");
        this.freezeKey = freezeKey;
    }

    public void setKycKey(JKey kycKey) {
        throwIfImmutable("Cannot change this token's kyc key if it's immutable.");
        this.kycKey = kycKey;
    }

    public Optional<JKey> supplyKey() {
        return Optional.ofNullable(supplyKey);
    }

    public Optional<JKey> feeScheduleKey() {
        return Optional.ofNullable(feeScheduleKey);
    }

    public boolean hasSupplyKey() {
        return supplyKey != UNUSED_KEY;
    }

    public void setSupplyKey(JKey supplyKey) {
        throwIfImmutable("Cannot change this token's supply key if it's immutable.");
        this.supplyKey = supplyKey;
    }

    public Optional<JKey> wipeKey() {
        return Optional.ofNullable(wipeKey);
    }

    public boolean hasWipeKey() {
        return wipeKey != UNUSED_KEY;
    }

    public void setWipeKey(JKey wipeKey) {
        throwIfImmutable("Cannot change this token's wipe key if it's immutable.");
        this.wipeKey = wipeKey;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        throwIfImmutable("Cannot change this token's to be deleted if it's immutable.");
        this.deleted = deleted;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        throwIfImmutable("Cannot change this token's freeze key if it's immutable.");
        this.paused = paused;
    }

    public String symbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        throwIfImmutable("Cannot change this token's symbol if it's immutable.");
        this.symbol = symbol;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        throwIfImmutable("Cannot change this token's name if it's immutable.");
        this.name = name;
    }

    public void setDecimals(int decimals) {
        throwIfImmutable("Cannot change this token's decimals if it's immutable.");
        this.decimals = decimals;
    }

    public void setTreasury(EntityId treasury) {
        throwIfImmutable("Cannot change this token's treasure account if it's immutable.");
        this.treasury = treasury;
    }

    public void setAdminKey(JKey adminKey) {
        throwIfImmutable("Cannot change this token's admin key if it's immutable.");
        this.adminKey = adminKey;
    }

    public boolean accountsAreFrozenByDefault() {
        return accountsFrozenByDefault;
    }

    public boolean accountsKycGrantedByDefault() {
        return accountsKycGrantedByDefault;
    }

    public EntityId treasury() {
        return treasury;
    }

    public EntityNum treasuryNum() {
        return treasury.asNum();
    }

    public long expiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        throwIfImmutable("Cannot change this token's expiry time if it's immutable.");
        this.expiry = expiry;
    }

    public long autoRenewPeriod() {
        return autoRenewPeriod;
    }

    public void setAutoRenewPeriod(long autoRenewPeriod) {
        throwIfImmutable("Cannot change this token's auto renewal period if it's immutable.");
        this.autoRenewPeriod = autoRenewPeriod;
    }

    public EntityId autoRenewAccount() {
        return autoRenewAccount;
    }

    public boolean hasAutoRenewAccount() {
        return autoRenewAccount != null;
    }

    public void setAutoRenewAccount(EntityId autoRenewAccount) {
        throwIfImmutable("Cannot change this token's auto renewal account if it's immutable.");
        this.autoRenewAccount = autoRenewAccount;
    }

    public void adjustTotalSupplyBy(long amount) {
        throwIfImmutable("Cannot adjust this token's total supply if it's immutable.");
        var newTotalSupply = totalSupply + amount;
        if (newTotalSupply < 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Argument 'amount=%d' would negate totalSupply=%d!",
                            amount, totalSupply));
        }
        if (maxSupply != 0 && maxSupply < newTotalSupply) {
            throw new IllegalArgumentException(
                    String.format(
                            "Argument 'amount=%d' would exceed maxSupply=%d!", amount, maxSupply));
        }
        totalSupply += amount;
    }

    public long entityNum() {
        return BitPackUtils.numFromCode(number);
    }

    public TokenID grpcId() {
        return StaticPropertiesHolder.STATIC_PROPERTIES.scopedTokenWith(entityNum());
    }

    public JKey getSupplyKey() {
        return supplyKey;
    }

    public JKey getWipeKey() {
        return wipeKey;
    }

    public JKey getAdminKey() {
        return adminKey;
    }

    public JKey getKycKey() {
        return kycKey;
    }

    public JKey getFreezeKey() {
        return freezeKey;
    }

    public JKey getPauseKey() {
        return pauseKey;
    }

    public void setTotalSupply(long totalSupply) {
        throwIfImmutable("Cannot change this token's total supply if it's immutable.");
        this.totalSupply = totalSupply;
    }

    public String memo() {
        return memo;
    }

    public void setMemo(String memo) {
        throwIfImmutable("Cannot change this token's memo if it's immutable.");
        this.memo = memo;
    }

    public void setAccountsFrozenByDefault(boolean accountsFrozenByDefault) {
        throwIfImmutable("Cannot change this token's default frozen status if it's immutable.");
        this.accountsFrozenByDefault = accountsFrozenByDefault;
    }

    public void setAccountsKycGrantedByDefault(boolean accountsKycGrantedByDefault) {
        throwIfImmutable("Cannot change this token's default Kyc status if it's immutable.");
        this.accountsKycGrantedByDefault = accountsKycGrantedByDefault;
    }

    public long getLastUsedSerialNumber() {
        return lastUsedSerialNumber;
    }

    public void setLastUsedSerialNumber(long serialNum) {
        throwIfImmutable("Cannot change this token's last used serial number if it's immutable.");
        this.lastUsedSerialNumber = serialNum;
    }

    public TokenType tokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        throwIfImmutable("Cannot change this token's token type if it's immutable.");
        this.tokenType = tokenType;
    }

    public void setTokenType(int tokenTypeInt) {
        throwIfImmutable("Cannot change this token's token type through value if it's immutable.");
        this.tokenType = TokenType.values()[tokenTypeInt];
    }

    public TokenSupplyType supplyType() {
        return supplyType;
    }

    public void setSupplyType(TokenSupplyType supplyType) {
        throwIfImmutable("Cannot change this token's supply type if it's immutable.");
        this.supplyType = supplyType;
    }

    public void setSupplyType(int supplyTypeInt) {
        throwIfImmutable("Cannot change this token's supply type through value if it's immutable.");
        this.supplyType = TokenSupplyType.values()[supplyTypeInt];
    }

    public long maxSupply() {
        return maxSupply;
    }

    public void setMaxSupply(long maxSupply) {
        throwIfImmutable("Cannot change this token's max supply if it's immutable.");
        this.maxSupply = maxSupply;
    }

    public List<FcCustomFee> customFeeSchedule() {
        return feeSchedule;
    }

    public void setFeeSchedule(List<FcCustomFee> feeSchedule) {
        throwIfImmutable("Cannot change this token's fee schedule if it's immutable.");
        this.feeSchedule = feeSchedule;
    }

    public List<CustomFee> grpcFeeSchedule() {
        final List<CustomFee> grpcList = new ArrayList<>();
        for (var customFee : feeSchedule) {
            grpcList.add(customFee.asGrpc());
        }
        return grpcList;
    }

    @VisibleForTesting
    public void setFeeScheduleFrom(List<CustomFee> grpcFeeSchedule) {
        throwIfImmutable("Cannot change this token's fee schedule from grpc if it's immutable.");
        feeSchedule = grpcFeeSchedule.stream().map(FcCustomFee::fromGrpc).toList();
    }

    public void setFeeScheduleKey(final JKey feeScheduleKey) {
        throwIfImmutable("Cannot change this token's fee schedule key if it's immutable.");
        this.feeScheduleKey = feeScheduleKey;
    }

    public JKey getFeeScheduleKey() {
        return feeScheduleKey;
    }

    public boolean hasFeeScheduleKey() {
        return feeScheduleKey != UNUSED_KEY;
    }

    @Override
    public EntityNum getKey() {
        return new EntityNum(number);
    }

    @Override
    public void setKey(EntityNum phi) {
        this.number = phi.intValue();
    }
}
