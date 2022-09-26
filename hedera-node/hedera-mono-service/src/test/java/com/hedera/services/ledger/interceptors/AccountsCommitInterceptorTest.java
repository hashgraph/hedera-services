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
package com.hedera.services.ledger.interceptors;

import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.validation.AccountUsageTracking;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountsCommitInterceptorTest {

    @Mock private AccountUsageTracking usageTracking;
    @Mock private SideEffectsTracker sideEffectsTracker;

    private AccountsCommitInterceptor subject;

    @BeforeEach
    void setUp() {
        subject = new AccountsCommitInterceptor(usageTracking, sideEffectsTracker);
    }

    @Test
    void recordsNewlyCreatedContractsAndRefreshesWithNewAccounts() {
        subject.preview(pendingChanges(true, true));

        subject.postCommit();

        verify(usageTracking).recordContracts(1);
        verify(usageTracking).refreshAccounts();
    }

    @Test
    void doesNothingIfNoContractsOrAccounts() {
        subject.preview(pendingChanges(false, false));

        subject.postCommit();

        verifyNoInteractions(usageTracking);
    }

    private EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges(
            final boolean includeContract, final boolean includeAccounts) {
        final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges =
                new EntityChangeSet<>();
        if (includeAccounts) {
            pendingChanges.include(idWith(1234L), null, Map.of(IS_SMART_CONTRACT, false));
            pendingChanges.include(idWith(1236L), null, Map.of());
        }
        if (includeContract) {
            pendingChanges.include(idWith(1235L), null, Map.of(IS_SMART_CONTRACT, true));
        }
        pendingChanges.include(idWith(1236L), new MerkleAccount(), Map.of());
        return pendingChanges;
    }

    private static AccountID idWith(final long num) {
        return AccountID.newBuilder().setAccountNum(num).build();
    }
}
