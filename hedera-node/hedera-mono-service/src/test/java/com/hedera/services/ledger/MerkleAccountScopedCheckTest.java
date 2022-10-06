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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerkleAccountScopedCheckTest {
    @Mock private OptionValidator validator;
    @Mock private BalanceChange balanceChange;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    @Mock private MerkleAccount account;
    @Mock private Map<AccountProperty, Object> changeSet;
    @Mock private Function<AccountProperty, Object> extantProps;

    private MerkleAccountScopedCheck subject;

    @BeforeEach
    void setUp() {
        subject = new MerkleAccountScopedCheck(validator, nftsLedger);
        subject.setBalanceChange(balanceChange);
    }

    @Test
    void failsAsExpectedForDeletedAccount() {
        when(balanceChange.isForHbar()).thenReturn(true);
        when(account.isDeleted()).thenReturn(true);
        assertEquals(ACCOUNT_DELETED, subject.checkUsing(account, changeSet));

        given(extantProps.apply(IS_DELETED)).willReturn(true);
        assertEquals(ACCOUNT_DELETED, subject.checkUsing(extantProps, changeSet));
    }

    @Test
    void failAsExpectedForDeletedAccountInChangeSet() {
        when(balanceChange.isForHbar()).thenReturn(true);
        Map<AccountProperty, Object> changes = new HashMap<>();
        changes.put(IS_DELETED, true);

        assertEquals(ACCOUNT_DELETED, subject.checkUsing(account, changes));
    }

    @Test
    void failsAsExpectedForExpiredAccount() {

        when(balanceChange.isForHbar()).thenReturn(true);
        when(account.isDeleted()).thenReturn(false);
        when(account.getBalance()).thenReturn(0L);
        when(account.getExpiry()).thenReturn(expiry);
        when(account.isSmartContract()).thenReturn(false);
        when(validator.expiryStatusGiven(0L, expiry, false))
                .thenReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, subject.checkUsing(account, changeSet));
    }

    @Test
    void failsAsExpectedWhenInsufficientBalance() {
        when(balanceChange.isForHbar()).thenReturn(true);
        when(account.isDeleted()).thenReturn(false);
        when(account.getExpiry()).thenReturn(expiry);
        when(account.getBalance()).thenReturn(5L);
        when(validator.expiryStatusGiven(5L, expiry, false)).thenReturn(OK);
        when(balanceChange.getAggregatedUnits()).thenReturn(-6L);
        when(balanceChange.codeForInsufficientBalance()).thenReturn(INSUFFICIENT_ACCOUNT_BALANCE);

        assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, subject.checkUsing(account, changeSet));
    }

    @Test
    void failsAsExpectedWhenSpenderIsNotGrantedAllowance() {
        when(account.isDeleted()).thenReturn(false);
        when(account.getExpiry()).thenReturn(expiry);
        when(account.getBalance()).thenReturn(10L);
        when(balanceChange.isForHbar()).thenReturn(true);
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getCryptoAllowances()).thenReturn(CRYPTO_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(revokedSpender);
        when(validator.expiryStatusGiven(10L, expiry, false)).thenReturn(OK);

        assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkUsing(account, changeSet));
    }

    @Test
    void failsAsExpectedWhenSpenderHasInsufficientAllowance() {
        when(account.isDeleted()).thenReturn(false);
        when(account.getBalance()).thenReturn(110L);
        when(account.getExpiry()).thenReturn(expiry);
        when(balanceChange.getAllowanceUnits()).thenReturn(-105L);
        when(balanceChange.isForHbar()).thenReturn(true);
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getCryptoAllowances()).thenReturn(CRYPTO_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(payerID);
        when(validator.expiryStatusGiven(110L, expiry, false)).thenReturn(OK);

        assertEquals(AMOUNT_EXCEEDS_ALLOWANCE, subject.checkUsing(account, changeSet));
    }

    @Test
    void failsAsExpectedWhenSpenderIsNotGrantedAllowanceOnFungible() {
        when(balanceChange.isForFungibleToken()).thenReturn(true);
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getFungibleTokenAllowances()).thenReturn(FUNGIBLE_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(revokedSpender);
        when(balanceChange.getToken()).thenReturn(Id.fromGrpcToken(fungibleTokenID));

        assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkUsing(account, changeSet));
    }

    @Test
    void failsAsExpectedWhenSpenderIsHasInsufficientAllowanceOnFungible() {
        when(balanceChange.getAllowanceUnits()).thenReturn(-105L);
        when(balanceChange.isForFungibleToken()).thenReturn(true);
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getFungibleTokenAllowances()).thenReturn(FUNGIBLE_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(payerID);
        when(balanceChange.getToken()).thenReturn(Id.fromGrpcToken(fungibleTokenID));

        assertEquals(AMOUNT_EXCEEDS_ALLOWANCE, subject.checkUsing(account, changeSet));
    }

    @Test
    void happyPathWithSpenderIsHasAllowanceOnFungible() {
        when(balanceChange.isForFungibleToken()).thenReturn(true);
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getFungibleTokenAllowances()).thenReturn(FUNGIBLE_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(payerID);
        when(balanceChange.getToken()).thenReturn(Id.fromGrpcToken(fungibleTokenID));

        assertEquals(OK, subject.checkUsing(account, changeSet));
    }

    @Test
    void failsAsExpectedWhenSpenderIsNotGrantedAllowanceOnNFT() {
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getApproveForAllNfts()).thenReturn(NFT_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(payerID);
        when(balanceChange.getToken()).thenReturn(Id.fromGrpcToken(nonFungibleTokenID));
        when(balanceChange.nftId()).thenReturn(nftId1);
        when(nftsLedger.get(nftId1, NftProperty.SPENDER))
                .thenReturn(EntityId.fromGrpcAccountId(revokedSpender));

        assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkUsing(account, changeSet));
    }

    @Test
    void happyPathWithSpenderIsHasAllowanceOnAllNFT() {
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getApproveForAllNfts()).thenReturn(NFT_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(payerID);
        when(balanceChange.getToken()).thenReturn(Id.fromGrpcToken(fungibleTokenID));

        assertEquals(OK, subject.checkUsing(account, changeSet));
    }

    @Test
    void happyPathWithSpenderIsHasAllowanceOnSpecificNFT() {
        when(balanceChange.isApprovedAllowance()).thenReturn(true);
        when(account.getApproveForAllNfts()).thenReturn(NFT_ALLOWANCES);
        when(balanceChange.getPayerID()).thenReturn(payerID);
        when(balanceChange.getToken()).thenReturn(Id.fromGrpcToken(nonFungibleTokenID));
        when(balanceChange.nftId()).thenReturn(nftId1);
        when(nftsLedger.get(nftId1, NftProperty.SPENDER))
                .thenReturn(EntityId.fromGrpcAccountId(payerID));

        assertEquals(OK, subject.checkUsing(account, changeSet));
    }

    @Test
    void happyPath() {
        when(balanceChange.isForHbar()).thenReturn(true);
        when(account.isDeleted()).thenReturn(false);
        given(account.getExpiry()).willReturn(expiry);
        when(validator.expiryStatusGiven(0L, expiry, false)).thenReturn(OK);
        when(account.getBalance()).thenReturn(0L);
        when(balanceChange.getAggregatedUnits()).thenReturn(5L);

        assertEquals(OK, subject.checkUsing(account, changeSet));
    }

    @Test
    void throwsAsExpected() {
        var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.getEffective(AUTO_RENEW_PERIOD, account, null, changeSet));
        assertEquals(
                "Invalid Property " + AUTO_RENEW_PERIOD + " cannot be validated in scoped check",
                iae.getMessage());
    }

    private static final AccountID revokedSpender =
            AccountID.newBuilder().setAccountNum(123L).build();
    private static final AccountID payerID = AccountID.newBuilder().setAccountNum(12345L).build();
    private static final EntityNum payerNum = EntityNum.fromAccountId(payerID);
    private static final TokenID fungibleTokenID = TokenID.newBuilder().setTokenNum(1234L).build();
    private static final TokenID nonFungibleTokenID =
            TokenID.newBuilder().setTokenNum(1235L).build();
    private static final NftId nftId1 =
            NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 1L);
    private static final NftId nftId2 =
            NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 2L);
    private static final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(fungibleTokenID), payerNum);
    private static final FcTokenAllowanceId nftAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(nonFungibleTokenID), payerNum);
    private static final Map<EntityNum, Long> CRYPTO_ALLOWANCES = new HashMap<>();
    private static final Map<FcTokenAllowanceId, Long> FUNGIBLE_ALLOWANCES = new HashMap<>();
    private static final Set<FcTokenAllowanceId> NFT_ALLOWANCES = new TreeSet<>();

    static {
        CRYPTO_ALLOWANCES.put(payerNum, 100L);
        FUNGIBLE_ALLOWANCES.put(fungibleAllowanceId, 100L);
        NFT_ALLOWANCES.add(fungibleAllowanceId);
    }

    private static final long expiry = 1234L;
}
