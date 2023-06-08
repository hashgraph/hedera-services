/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hedera.node.app.service.token.impl.validators.AllowanceValidator.*;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.AllowanceValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllowanceValidatorTest extends CryptoTokenHandlerTestBase {
    private AllowanceValidator subject;

    @Mock
    private ConfigProvider configProvider;

    @BeforeEach
    public void setUp() {
        super.setUp();
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(ownerId, ownerAccount)
                .value(payerId, account)
                .value(spenderId, spenderAccount)
                .build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);

        subject = new AllowanceValidator(configProvider);
    }

    @Test
    void aggregatedListCorrectly() {
        List<NftAllowance> list = new ArrayList<>();
        final var Nftid = NftAllowance.newBuilder()
                .spender(spenderId)
                .serialNumbers(List.of(1L, 10L))
                .tokenId(nonFungibleTokenId)
                .owner(ownerId)
                .approvedForAll(Boolean.TRUE)
                .build();
        final var Nftid2 = NftAllowance.newBuilder()
                .spender(spenderId)
                .serialNumbers(List.of(1L, 100L))
                .tokenId(nonFungibleTokenId)
                .owner(ownerId)
                .approvedForAll(Boolean.FALSE)
                .build();
        list.add(Nftid);
        list.add(Nftid2);
        assertThat(aggregateApproveNftAllowances(list)).isEqualTo(4);
    }

    @Test
    void checksFlagIfEnabled() {
        final var trueConfig = new HederaTestConfigBuilder()
                .withValue("hedera.allowances.isEnabled", true)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(trueConfig, 1));
        assertThat(subject.isEnabled()).isTrue();

        final var falseConfig = new HederaTestConfigBuilder()
                .withValue("hedera.allowances.isEnabled", false)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(falseConfig, 1));
        assertThat(subject.isEnabled()).isFalse();
    }

    @Test
    void validatesAllowancesLimit() {
        assertThatNoException().isThrownBy(() -> AllowanceValidator.validateAllowanceLimit(ownerAccount, 100));
        assertThatThrownBy(() -> AllowanceValidator.validateAllowanceLimit(ownerAccount, 0))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MAX_ALLOWANCES_EXCEEDED));
    }

    @Test
    void validatesOwner() {
        assertThat(isValidOwner(nftSl1, spenderId.accountNum(), fungibleToken)).isFalse();
        assertThat(isValidOwner(nftSl1, ownerId.accountNum(), nonFungibleToken)).isTrue();
    }

    @Test
    void getsEffectiveOwnerIfOwnerNullOrZero() {
        assertThat(getEffectiveOwner(null, account, readableAccountStore)).isEqualTo(account);
        assertThat(getEffectiveOwner(AccountID.DEFAULT, account, readableAccountStore))
                .isEqualTo(account);
    }

    @Test
    void getsEffectiveOwnerIfOwnerValid() {
        assertThat(getEffectiveOwner(ownerId, account, readableAccountStore)).isEqualTo(ownerAccount);
    }

    @Test
    void failsIfEffectiveOwnerDoesntExist() {
        final var missingOwner = AccountID.newBuilder().accountNum(1000).build();
        assertThatThrownBy(() -> getEffectiveOwner(missingOwner, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ALLOWANCE_OWNER_ID));
    }
}
