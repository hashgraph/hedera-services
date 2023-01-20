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

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.token.entity.Account.HBARS_TO_TINYBARS;
import static com.hedera.test.utils.IdUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// FUTURE: Once we have protobuf generated object need to replace all JKeys.
@ExtendWith(MockitoExtension.class)
class ReadableAccountStoreTest {
    @Mock private ReadableKVState aliases;
    @Mock private ReadableKVState accounts;
    @Mock private MerkleAccount account;
    @Mock private ReadableStates states;
    private final Key payerKey = KeyUtils.A_COMPLEX_KEY;
    private final Key contractKey = KeyUtils.A_COMPLEX_KEY;
    private final HederaKey payerHederaKey = asHederaKey(payerKey).get();
    private final HederaKey contractHederaKey = asHederaKey(contractKey).get();
    private final AccountID payerAlias = asAliasAccount(ByteString.copyFromUtf8("testAlias"));
    final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
    final ContractID contractAlias = ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(evmAddress)).build();
    private final ContractID contract = asContract("0.0.1234");
    private final AccountID payer = asAccount("0.0.3");
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

    @Test
    void getsKeyIfAlias() {
        given(aliases.get(payerAlias.getAlias().toStringUtf8())).willReturn(payerNum);
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);

        final var result = subject.getKey(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey, result.key());
    }

    @Test
    void getsKeyIfEvmAddress() {
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) contractHederaKey);
        given(account.isSmartContract()).willReturn(true);

        final var result = subject.getKey(contractAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(contractHederaKey, result.key());
    }

    @Test
    void getsNullKeyIfMissingEvmAddress() {
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(null);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyIfMissingContract() {
        given(accounts.get(contract.getContractNum())).willReturn(null);

        var result = subject.getKey(contract);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contract);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfNotSmartContract() {
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);

        var result = subject.getKey(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());
    }

    @Test
    void failsIfContractDeleted() {
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);
        given(account.isDeleted()).willReturn(true);

        var result = subject.getKey(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());
    }

    @Test
    void getsKeyIfAccount() {
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);

        final var result = subject.getKey(payer);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey, result.key());
    }

    @Test
    void getsNullKeyIfMissingAlias() {
        given(aliases.get(payerAlias.getAlias().toStringUtf8())).willReturn(null);

        final var result = subject.getKey(payerAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyIfMissingAccount() {
        given(accounts.get(payerNum)).willReturn(null);

        final var result = subject.getKey(payer);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsMirrorAddress() {
        final var num = EntityNum.fromLong(payerNum);
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount =
                asAliasAccount(ByteString.copyFrom(mirrorAddress.toArrayUnsafe()));

        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);

        final var result = subject.getKey(mirrorAccount);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey, result.key());
    }

    @Test
    void failsIfMirrorAddressDoesntExist() {
        final var num = EntityNum.fromLong(payerNum);
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount =
                asAliasAccount(ByteString.copyFrom(mirrorAddress.toArrayUnsafe()));

        given(accounts.get(payerNum)).willReturn(null);

        final var result = subject.getKey(mirrorAccount);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsKeyIfPayerAliasAndReceiverSigRequired() {
        given(aliases.get(payerAlias.getAlias().toStringUtf8())).willReturn(payerNum);
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.isReceiverSigRequired()).willReturn(true);

        final var result = subject.getKeyIfReceiverSigRequired(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey, result.key());
    }

    @Test
    void getsKeyIfPayerAccountAndReceiverSigRequired() {
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.isReceiverSigRequired()).willReturn(true);

        final var result = subject.getKeyIfReceiverSigRequired(payer);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey, result.key());
    }

    @Test
    void getsNullKeyFromReceiverSigRequiredIfMissingAlias() {
        given(aliases.get(payerAlias.getAlias().toStringUtf8())).willReturn(null);

        final var result = subject.getKeyIfReceiverSigRequired(payerAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyFromReceiverSigRequiredIfMissingAccount() {
        given(accounts.get(payerNum)).willReturn(null);

        final var result = subject.getKeyIfReceiverSigRequired(payer);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyIfAndReceiverSigNotRequired() {
        given(aliases.get(payerAlias.getAlias().toStringUtf8())).willReturn(payerNum);
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.isReceiverSigRequired()).willReturn(false);

        final var result = subject.getKeyIfReceiverSigRequired(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyFromAccountIfReceiverKeyNotRequired() {
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.isReceiverSigRequired()).willReturn(false);

        final var result = subject.getKeyIfReceiverSigRequired(payer);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyFromContractIfReceiverKeyNotRequired() {
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) contractHederaKey);
        given(account.isSmartContract()).willReturn(true);
        given(account.isReceiverSigRequired()).willReturn(false);

        final var result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfKeyIsJContractIDKey() {
        final var mockKey = mock(JContractIDKey.class);

        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);
        given(account.getAccountKey()).willReturn(mockKey);
        given(account.isSmartContract()).willReturn(true);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfKeyIsEmpty() {
        final var key = new JEd25519Key(new byte[0]);
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);
        given(account.getAccountKey()).willReturn(key);
        given(account.isSmartContract()).willReturn(true);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfKeyIsNull() {
        given(aliases.get(contractAlias.getEvmAddress().toStringUtf8())).willReturn(contract.getContractNum());
        given(accounts.get(contract.getContractNum())).willReturn(account);
        given(account.getAccountKey()).willReturn(null);
        given(account.isSmartContract()).willReturn(true);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsKeyValidationWhenKeyReturnedIsNull() {
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn(null);

        var result = subject.getKey(payer);
        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(payer);
        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsKeyValidationWhenKeyReturnedIsEmpty() {
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn(new JKeyList());

        var result = subject.getKey(payer);

        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(payer);

        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAccount() {
        // given
        given(accounts.get(payerNum)).willReturn(account);
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
        final var result = subject.getAccount(payer);

        // then
        assertThat(result).isNotEmpty();
        final var mappedAccount = result.get();
        assertThat(mappedAccount.getKey()).hasValue(payerHederaKey);
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
        assertThat(mappedAccount.accountNumber()).isEqualTo(payer.getAccountNum());
        assertThat(mappedAccount.alias()).hasValue(new byte[] {1, 2, 3});
        assertThat(mappedAccount.isSmartContract()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsEmptyAccount() {
        // given
        given(accounts.get(payerNum)).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey);
        given(account.getMemo()).willReturn("");

        // when
        final var result = subject.getAccount(payer);

        // then
        assertThat(result).isNotEmpty();
        final var mappedAccount = result.get();
        assertThat(mappedAccount.getKey()).hasValue(payerHederaKey);
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
        assertThat(mappedAccount.accountNumber()).isEqualTo(payer.getAccountNum());
        assertThat(mappedAccount.alias()).isEmpty();
        assertThat(mappedAccount.isSmartContract()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsEmptyOptionalIfMissingAccount() {
        given(accounts.get(payerNum)).willReturn(null);

        final Optional<Account> result = subject.getAccount(payer);

        assertThat(result).isEmpty();
    }
}
