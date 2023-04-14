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
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.KeyUtils;
import com.swirlds.common.utility.CommonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// FUTURE: Once we have protobuf generated object need to replace all JKeys.
@ExtendWith(MockitoExtension.class)
class ReadableAccountStoreTest {
    @Mock
    private ReadableKVState aliases;

    @Mock
    private ReadableKVState accounts;

    @Mock
    private MerkleAccount account;

    @Mock
    private ReadableStates states;

    private final Key payerKey = KeyUtils.A_COMPLEX_KEY;
    private final Key contractKey = KeyUtils.A_COMPLEX_KEY;
    private final HederaKey payerHederaKey = asHederaKey(payerKey).get();
    private final HederaKey contractHederaKey = asHederaKey(contractKey).get();
    private final AccountID payerAlias =
            AccountID.newBuilder().alias(Bytes.wrap("testAlias")).build();
    private final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
    private final ContractID contractAlias =
            ContractID.newBuilder().evmAddress(Bytes.wrap(evmAddress)).build();
    private final ContractID contract =
            ContractID.newBuilder().contractNum(1234).build();
    private final AccountID payer = AccountID.newBuilder().accountNum(3).build();
    private final Long payerNum = 3L;
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    private ReadableAccountStore subject;

    @BeforeEach
    public void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        subject = new ReadableAccountStore(states);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAccount() {
        // given
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(account);
        given(account.getMemo()).willReturn("");
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.getExpiry()).willReturn(5L);
        given(account.getBalance()).willReturn(7L * HBARS_TO_TINYBARS);
        given(account.getMemo()).willReturn("Hello World");
        given(account.isDeleted()).willReturn(true);
        given(account.isReceiverSigRequired()).willReturn(true);
        given(account.getNftsOwned()).willReturn(11L);
        given(account.getMaxAutomaticAssociations()).willReturn(13);
        given(account.getUsedAutoAssociations()).willReturn(17);
        given(account.getNumAssociations()).willReturn(19);
        given(account.getNumPositiveBalances()).willReturn(23);
        given(account.getEthereumNonce()).willReturn(29L);
        given(account.getStakedToMe()).willReturn(31L);
        given(account.getStakePeriodStart()).willReturn(37L);
        given(account.totalStake()).willReturn(41L);
        given(account.isDeclinedReward()).willReturn(true);
        given(account.getAutoRenewAccount()).willReturn(new EntityId(43L, 47L, 53L));
        given(account.getAutoRenewSecs()).willReturn(59L);
        given(account.getAlias()).willReturn(ByteString.copyFrom(new byte[] {1, 2, 3}));
        given(account.isSmartContract()).willReturn(true);

        // when
        final var mappedAccount = subject.getAccountById(payer);

        // then
        assertThat(mappedAccount).isNotNull();
        assertThat(mappedAccount.getKey()).isEqualTo(payerHederaKey);
        assertThat(mappedAccount.expiry()).isEqualTo(5L);
        assertThat(mappedAccount.balanceInHbar()).isEqualTo(7L);
        assertThat(mappedAccount.balanceInTinyBar()).isEqualTo(7L * HBARS_TO_TINYBARS);
        assertThat(mappedAccount.memo()).isEqualTo("Hello World");
        assertThat(mappedAccount.isDeleted()).isTrue();
        assertThat(mappedAccount.isReceiverSigRequired()).isTrue();
        assertThat(mappedAccount.numberOfOwnedNfts()).isEqualTo(11L);
        assertThat(mappedAccount.maxAutoAssociations()).isEqualTo(13);
        assertThat(mappedAccount.usedAutoAssociations()).isEqualTo(17);
        assertThat(mappedAccount.numAssociations()).isEqualTo(19);
        assertThat(mappedAccount.numPositiveBalances()).isEqualTo(23);
        assertThat(mappedAccount.ethereumNonce()).isEqualTo(29L);
        assertThat(mappedAccount.stakedToMe()).isEqualTo(31L);
        assertThat(mappedAccount.stakePeriodStart()).isEqualTo(37L);
        assertThat(mappedAccount.stakedNum()).isEqualTo(41L);
        assertThat(mappedAccount.declineReward()).isTrue();
        assertThat(mappedAccount.stakeAtStartOfLastRewardedPeriod()).isEqualTo(37L);
        assertThat(mappedAccount.autoRenewAccountNumber()).isEqualTo(53L);
        assertThat(mappedAccount.autoRenewSecs()).isEqualTo(59L);
        assertThat(mappedAccount.accountNumber()).isEqualTo(payer.accountNum());
        assertThat(mappedAccount.alias()).isEqualTo(Bytes.wrap(new byte[] {1, 2, 3}));
        assertThat(mappedAccount.isSmartContract()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsEmptyAccount() {
        // given
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.getMemo()).willReturn("");

        // when
        final var mappedAccount = subject.getAccountById(payer);

        // then
        assertThat(mappedAccount).isNotNull();
        assertThat(mappedAccount.getKey()).isEqualTo(payerHederaKey);
        assertThat(mappedAccount.expiry()).isZero();
        assertThat(mappedAccount.balanceInHbar()).isZero();
        assertThat(mappedAccount.balanceInTinyBar()).isZero();
        assertThat(mappedAccount.memo()).isEmpty();
        assertThat(mappedAccount.isDeleted()).isFalse();
        assertThat(mappedAccount.isReceiverSigRequired()).isFalse();
        assertThat(mappedAccount.numberOfOwnedNfts()).isZero();
        assertThat(mappedAccount.maxAutoAssociations()).isZero();
        assertThat(mappedAccount.usedAutoAssociations()).isZero();
        assertThat(mappedAccount.numAssociations()).isZero();
        assertThat(mappedAccount.numPositiveBalances()).isZero();
        assertThat(mappedAccount.ethereumNonce()).isZero();
        assertThat(mappedAccount.stakedToMe()).isZero();
        assertThat(mappedAccount.stakePeriodStart()).isZero();
        assertThat(mappedAccount.stakedNum()).isZero();
        assertThat(mappedAccount.declineReward()).isFalse();
        assertThat(mappedAccount.stakeAtStartOfLastRewardedPeriod()).isZero();
        assertThat(mappedAccount.autoRenewAccountNumber()).isZero();
        assertThat(mappedAccount.autoRenewSecs()).isZero();
        assertThat(mappedAccount.accountNumber()).isEqualTo(payer.accountNum());
        assertThat(mappedAccount.alias()).isEqualTo(Bytes.EMPTY);
        assertThat(mappedAccount.isSmartContract()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsNullIfMissingAccount() {
        given(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).willReturn(null);
        final var result = subject.getAccountById(payer);
        assertThat(result).isNull();
    }
}
