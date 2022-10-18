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
package com.hedera.services.ledger.properties;

import static com.hedera.services.ledger.properties.TokenProperty.ACC_FROZEN_BY_DEFAULT;
import static com.hedera.services.ledger.properties.TokenProperty.ACC_KYC_GRANTED_BY_DEFAULT;
import static com.hedera.services.ledger.properties.TokenProperty.ADMIN_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.AUTO_RENEW_ACCOUNT;
import static com.hedera.services.ledger.properties.TokenProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.TokenProperty.DECIMALS;
import static com.hedera.services.ledger.properties.TokenProperty.EXPIRY;
import static com.hedera.services.ledger.properties.TokenProperty.FEE_SCHEDULE;
import static com.hedera.services.ledger.properties.TokenProperty.FEE_SCHEDULE_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.FREEZE_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.TokenProperty.IS_PAUSED;
import static com.hedera.services.ledger.properties.TokenProperty.KYC_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.LAST_USED_SERIAL_NUMBER;
import static com.hedera.services.ledger.properties.TokenProperty.MAX_SUPPLY;
import static com.hedera.services.ledger.properties.TokenProperty.MEMO;
import static com.hedera.services.ledger.properties.TokenProperty.NAME;
import static com.hedera.services.ledger.properties.TokenProperty.PAUSE_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.SUPPLY_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.SUPPLY_TYPE;
import static com.hedera.services.ledger.properties.TokenProperty.SYMBOL;
import static com.hedera.services.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.services.ledger.properties.TokenProperty.TOTAL_SUPPLY;
import static com.hedera.services.ledger.properties.TokenProperty.TREASURY;
import static com.hedera.services.ledger.properties.TokenProperty.WIPE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenPropertyTest {
    final long totalSupply = 2L;
    final int decimals = 10;
    final JKey adminKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
    final JKey freezeKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012346".getBytes());
    final JKey kycKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012347".getBytes());
    final JKey pauseKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012348".getBytes());
    final JKey supplyKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012349".getBytes());
    final JKey feeScheduleKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012340".getBytes());
    final JKey wipeKey = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012340".getBytes());
    final boolean deleted = false;
    final boolean paused = false;
    final String symbol = "TEST";
    final String name = "testName";
    final EntityId treasury = new EntityId(0, 0, 0);
    final boolean accountsFrozenByDefault = false;
    final boolean accountsKycGrantedByDefault = false;
    final long expiry = 1L;
    final long autoRenewPeriod = 3L;
    final EntityId autoRenewAccount = new EntityId(0, 0, 1);
    final String memo = "testMemo";
    final long lastUsedSerialNumber = 4L;
    final TokenType tokenType = TokenType.FUNGIBLE_COMMON;
    final TokenSupplyType supplyType = TokenSupplyType.INFINITE;
    final long maxSupply = 5L;
    final List<FcCustomFee> feeSchedule = List.of(new FcCustomFee());

    @Test
    void gettersWork() {
        // given:
        final MerkleToken target = new MerkleToken();
        TOTAL_SUPPLY.setter().accept(target, totalSupply);
        DECIMALS.setter().accept(target, decimals);
        ADMIN_KEY.setter().accept(target, adminKey);
        FREEZE_KEY.setter().accept(target, freezeKey);
        KYC_KEY.setter().accept(target, kycKey);
        PAUSE_KEY.setter().accept(target, pauseKey);
        SUPPLY_KEY.setter().accept(target, supplyKey);
        FEE_SCHEDULE_KEY.setter().accept(target, feeScheduleKey);
        WIPE_KEY.setter().accept(target, wipeKey);
        IS_DELETED.setter().accept(target, deleted);
        IS_PAUSED.setter().accept(target, paused);
        SYMBOL.setter().accept(target, symbol);
        NAME.setter().accept(target, name);
        TREASURY.setter().accept(target, treasury);
        ACC_FROZEN_BY_DEFAULT.setter().accept(target, accountsFrozenByDefault);
        ACC_KYC_GRANTED_BY_DEFAULT.setter().accept(target, accountsKycGrantedByDefault);
        EXPIRY.setter().accept(target, expiry);
        AUTO_RENEW_PERIOD.setter().accept(target, autoRenewPeriod);
        AUTO_RENEW_ACCOUNT.setter().accept(target, autoRenewAccount);
        MEMO.setter().accept(target, memo);
        LAST_USED_SERIAL_NUMBER.setter().accept(target, lastUsedSerialNumber);
        TOKEN_TYPE.setter().accept(target, tokenType);
        SUPPLY_TYPE.setter().accept(target, supplyType);
        MAX_SUPPLY.setter().accept(target, maxSupply);
        FEE_SCHEDULE.setter().accept(target, feeSchedule);
        // and:
        final var totalSupplyGetter = TOTAL_SUPPLY.getter();
        final var adminKeyGetter = ADMIN_KEY.getter();
        final var freezeKeyGetter = FREEZE_KEY.getter();
        final var kycKeyGetter = KYC_KEY.getter();
        final var pauseKeyGetter = PAUSE_KEY.getter();
        final var supplyKeyGetter = SUPPLY_KEY.getter();
        final var feeScheduleKeyGetter = FEE_SCHEDULE_KEY.getter();
        final var wipeKeyGetter = WIPE_KEY.getter();
        final var deletedGetter = IS_DELETED.getter();
        final var pausedGetter = IS_PAUSED.getter();
        final var symbolGetter = SYMBOL.getter();
        final var nameGetter = NAME.getter();
        final var treasuryGetter = TREASURY.getter();
        final var accountsFrozenByDefaultGetter = ACC_FROZEN_BY_DEFAULT.getter();
        final var accountsKycGrantedByDefaultGetter = ACC_KYC_GRANTED_BY_DEFAULT.getter();
        final var expiryGetter = EXPIRY.getter();
        final var autoRenewPeriodGetter = AUTO_RENEW_PERIOD.getter();
        final var autoRenewAccountGetter = AUTO_RENEW_ACCOUNT.getter();
        final var memoGetter = MEMO.getter();
        final var lastUsedSerialNumberGetter = LAST_USED_SERIAL_NUMBER.getter();
        final var tokenTypeGetter = TOKEN_TYPE.getter();
        final var supplyTypeGetter = SUPPLY_TYPE.getter();
        final var maxSupplyGetter = MAX_SUPPLY.getter();
        final var feeScheduleGetter = FEE_SCHEDULE.getter();
        final var decimalsGetter = DECIMALS.getter();
        // expect:
        assertEquals(totalSupply, totalSupplyGetter.apply(target));
        assertEquals(adminKey, adminKeyGetter.apply(target));
        assertEquals(freezeKey, freezeKeyGetter.apply(target));
        assertEquals(kycKey, kycKeyGetter.apply(target));
        assertEquals(pauseKey, pauseKeyGetter.apply(target));
        assertEquals(supplyKey, supplyKeyGetter.apply(target));
        assertEquals(feeScheduleKey, feeScheduleKeyGetter.apply(target));
        assertEquals(wipeKey, wipeKeyGetter.apply(target));
        assertEquals(deleted, deletedGetter.apply(target));
        assertEquals(paused, pausedGetter.apply(target));
        assertEquals(symbol, symbolGetter.apply(target));
        assertEquals(name, nameGetter.apply(target));
        assertEquals(treasury, treasuryGetter.apply(target));
        assertEquals(accountsFrozenByDefault, accountsFrozenByDefaultGetter.apply(target));
        assertEquals(accountsKycGrantedByDefault, accountsKycGrantedByDefaultGetter.apply(target));
        assertEquals(expiry, expiryGetter.apply(target));
        assertEquals(autoRenewPeriod, autoRenewPeriodGetter.apply(target));
        assertEquals(autoRenewAccount, autoRenewAccountGetter.apply(target));
        assertEquals(memo, memoGetter.apply(target));
        assertEquals(lastUsedSerialNumber, lastUsedSerialNumberGetter.apply(target));
        assertEquals(tokenType, tokenTypeGetter.apply(target));
        assertEquals(supplyType, supplyTypeGetter.apply(target));
        assertEquals(maxSupply, maxSupplyGetter.apply(target));
        assertEquals(feeSchedule, feeScheduleGetter.apply(target));
        assertEquals(decimals, decimalsGetter.apply(target));
    }

    @Test
    void settersWorks() {
        // given:
        final MerkleToken target = new MerkleToken();
        // and:
        final var totalSupplySetter = TOTAL_SUPPLY.setter();
        final var decimalsSetter = DECIMALS.setter();
        final var adminKeySetter = ADMIN_KEY.setter();
        final var freezeKeySetter = FREEZE_KEY.setter();
        final var kycKeySetter = KYC_KEY.setter();
        final var pauseKeySetter = PAUSE_KEY.setter();
        final var supplyKeySetter = SUPPLY_KEY.setter();
        final var feeScheduleKeySetter = FEE_SCHEDULE_KEY.setter();
        final var wipeKeySetter = WIPE_KEY.setter();
        final var deletedSetter = IS_DELETED.setter();
        final var pausedSetter = IS_PAUSED.setter();
        final var symbolSetter = SYMBOL.setter();
        final var nameSetter = NAME.setter();
        final var treasurySetter = TREASURY.setter();
        final var accountsFrozenByDefaultSetter = ACC_FROZEN_BY_DEFAULT.setter();
        final var accountsKycGrantedByDefaultSetter = ACC_KYC_GRANTED_BY_DEFAULT.setter();
        final var expirySetter = EXPIRY.setter();
        final var autoRenewPeriodSetter = AUTO_RENEW_PERIOD.setter();
        final var autoRenewAccountSetter = AUTO_RENEW_ACCOUNT.setter();
        final var memoSetter = MEMO.setter();
        final var lastUsedSerialNumberSetter = LAST_USED_SERIAL_NUMBER.setter();
        final var tokenTypeSetter = TOKEN_TYPE.setter();
        final var supplyTypeSetter = SUPPLY_TYPE.setter();
        final var maxSupplySetter = MAX_SUPPLY.setter();
        final var feeScheduleSetter = FEE_SCHEDULE.setter();
        // when:
        totalSupplySetter.accept(target, totalSupply);
        decimalsSetter.accept(target, decimals);
        adminKeySetter.accept(target, adminKey);
        freezeKeySetter.accept(target, freezeKey);
        kycKeySetter.accept(target, kycKey);
        pauseKeySetter.accept(target, pauseKey);
        supplyKeySetter.accept(target, supplyKey);
        feeScheduleKeySetter.accept(target, feeScheduleKey);
        wipeKeySetter.accept(target, wipeKey);
        deletedSetter.accept(target, deleted);
        pausedSetter.accept(target, paused);
        symbolSetter.accept(target, symbol);
        nameSetter.accept(target, name);
        treasurySetter.accept(target, treasury);
        accountsFrozenByDefaultSetter.accept(target, accountsFrozenByDefault);
        accountsKycGrantedByDefaultSetter.accept(target, accountsKycGrantedByDefault);
        expirySetter.accept(target, expiry);
        autoRenewPeriodSetter.accept(target, autoRenewPeriod);
        autoRenewAccountSetter.accept(target, autoRenewAccount);
        memoSetter.accept(target, memo);
        lastUsedSerialNumberSetter.accept(target, lastUsedSerialNumber);
        tokenTypeSetter.accept(target, tokenType);
        supplyTypeSetter.accept(target, supplyType);
        maxSupplySetter.accept(target, maxSupply);
        feeScheduleSetter.accept(target, feeSchedule);
        // expect:
        assertEquals(totalSupply, target.totalSupply());
        assertEquals(decimals, target.decimals());
        assertEquals(adminKey, target.getAdminKey());
        assertEquals(freezeKey, target.getFreezeKey());
        assertEquals(kycKey, target.getKycKey());
        assertEquals(pauseKey, target.getPauseKey());
        assertEquals(supplyKey, target.getSupplyKey());
        assertEquals(feeScheduleKey, target.getFeeScheduleKey());
        assertEquals(wipeKey, target.getWipeKey());
        assertEquals(deleted, target.isDeleted());
        assertEquals(paused, target.isPaused());
        assertEquals(symbol, target.symbol());
        assertEquals(name, target.name());
        assertEquals(treasury, target.treasury());
        assertEquals(accountsFrozenByDefault, target.accountsAreFrozenByDefault());
        assertEquals(accountsKycGrantedByDefault, target.accountsKycGrantedByDefault());
        assertEquals(expiry, target.expiry());
        assertEquals(autoRenewPeriod, target.autoRenewPeriod());
        assertEquals(autoRenewAccount, target.autoRenewAccount());
        assertEquals(tokenType, target.tokenType());
        assertEquals(supplyType, target.supplyType());
        assertEquals(maxSupply, target.maxSupply());
        assertEquals(feeSchedule, target.customFeeSchedule());
    }
}
