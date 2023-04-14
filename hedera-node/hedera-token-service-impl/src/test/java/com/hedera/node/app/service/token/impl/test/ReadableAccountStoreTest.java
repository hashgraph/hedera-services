/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.test.handlers.CryptoHandlerTestBase;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.KeyUtils;
import com.swirlds.common.utility.CommonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

// FUTURE: Once we have protobuf generated object need to replace all JKeys.
@ExtendWith(MockitoExtension.class)
class ReadableAccountStoreTest extends CryptoHandlerTestBase {
    private ReadableAccountStore subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = readableStore;
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAccount() {
        // given
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.memo()).willReturn("");
        given(account.key()).willReturn(accountKey);
        given(account.expiry()).willReturn(5L);
        given(account.tinybarBalance()).willReturn(7L * HBARS_TO_TINYBARS);
        given(account.memo()).willReturn("Hello World");
        given(account.deleted()).willReturn(true);
        given(account.receiverSigRequired()).willReturn(true);
        given(account.numberOwnedNfts()).willReturn(11L);
        given(account.maxAutoAssociations()).willReturn(13);
        given(account.usedAutoAssociations()).willReturn(17);
        given(account.numberAssociations()).willReturn(19);
        given(account.numberPositiveBalances()).willReturn(23);
        given(account.ethereumNonce()).willReturn(29L);
        given(account.stakedToMe()).willReturn(31L);
        given(account.stakePeriodStart()).willReturn(37L);
        given(account.stakedToMe()).willReturn(41L);
        given(account.declineReward()).willReturn(true);
        given(account.autoRenewAccountNumber()).willReturn(53L);
        given(account.autoRenewSecs()).willReturn(59L);
        given(account.alias()).willReturn(Bytes.wrap(new byte[] {1, 2, 3}));
        given(account.smartContract()).willReturn(true);

        // when
        final var mappedAccount = subject.getAccountById(id);

        // then
        assertThat(mappedAccount).isNotNull();
        assertThat(mappedAccount.key()).isEqualTo(accountKey);
        assertThat(mappedAccount.expiry()).isEqualTo(5L);
        assertThat(mappedAccount.tinybarBalance()).isEqualTo(7L);
        assertThat(mappedAccount.tinybarBalance()).isEqualTo(7L * HBARS_TO_TINYBARS);
        assertThat(mappedAccount.memo()).isEqualTo("Hello World");
        assertThat(mappedAccount.deleted()).isTrue();
        assertThat(mappedAccount.receiverSigRequired()).isTrue();
        assertThat(mappedAccount.numberOwnedNfts()).isEqualTo(11L);
        assertThat(mappedAccount.maxAutoAssociations()).isEqualTo(13);
        assertThat(mappedAccount.usedAutoAssociations()).isEqualTo(17);
        assertThat(mappedAccount.numberAssociations()).isEqualTo(19);
        assertThat(mappedAccount.numberPositiveBalances()).isEqualTo(23);
        assertThat(mappedAccount.ethereumNonce()).isEqualTo(29L);
        assertThat(mappedAccount.stakedToMe()).isEqualTo(31L);
        assertThat(mappedAccount.stakePeriodStart()).isEqualTo(37L);
        assertThat(mappedAccount.stakedNumber()).isEqualTo(41L);
        assertThat(mappedAccount.declineReward()).isTrue();
        assertThat(mappedAccount.stakeAtStartOfLastRewardedPeriod()).isEqualTo(37L);
        assertThat(mappedAccount.autoRenewAccountNumber()).isEqualTo(53L);
        assertThat(mappedAccount.autoRenewSecs()).isEqualTo(59L);
        assertThat(mappedAccount.accountNumber()).isEqualTo(accountNum);
        assertThat(mappedAccount.alias()).isEqualTo(Bytes.wrap(new byte[] {1, 2, 3}));
        assertThat(mappedAccount.smartContract()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsEmptyAccount() {
        // given
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.key()).willReturn(accountKey);
        given(account.memo()).willReturn("");

        // when
        final var mappedAccount = subject.getAccountById(id);

        // then
        assertThat(mappedAccount).isNotNull();
        assertThat(mappedAccount.key()).isEqualTo(accountKey);
        assertThat(mappedAccount.expiry()).isZero();
        assertThat(mappedAccount.tinybarBalance()).isZero();
        assertThat(mappedAccount.tinybarBalance()).isZero();
        assertThat(mappedAccount.memo()).isEmpty();
        assertThat(mappedAccount.deleted()).isFalse();
        assertThat(mappedAccount.receiverSigRequired()).isFalse();
        assertThat(mappedAccount.numberOwnedNfts()).isZero();
        assertThat(mappedAccount.maxAutoAssociations()).isZero();
        assertThat(mappedAccount.usedAutoAssociations()).isZero();
        assertThat(mappedAccount.numberAssociations()).isZero();
        assertThat(mappedAccount.numberPositiveBalances()).isZero();
        assertThat(mappedAccount.ethereumNonce()).isZero();
        assertThat(mappedAccount.stakedToMe()).isZero();
        assertThat(mappedAccount.stakePeriodStart()).isZero();
        assertThat(mappedAccount.stakedNumber()).isZero();
        assertThat(mappedAccount.declineReward()).isFalse();
        assertThat(mappedAccount.stakeAtStartOfLastRewardedPeriod()).isZero();
        assertThat(mappedAccount.autoRenewAccountNumber()).isZero();
        assertThat(mappedAccount.autoRenewSecs()).isZero();
        assertThat(mappedAccount.accountNumber()).isEqualTo(accountNum);
        assertThat(mappedAccount.alias()).isEqualTo(Bytes.EMPTY);
        assertThat(mappedAccount.smartContract()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsNullIfMissingAccount() {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(null);
        final var result = subject.getAccountById(id);
        assertThat(result).isNull();
    }
}
