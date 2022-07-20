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
package com.hedera.services.ledger.interceptors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.validation.UsageLimits;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokensCommitInterceptorTest {
    @Mock private UsageLimits usageLimits;

    private TokensCommitInterceptor subject;

    @BeforeEach
    void setUp() {
        subject = new TokensCommitInterceptor(usageLimits);
    }

    @Test
    void noCreationsMeansNoRefresh() {
        final var subject = new TokensCommitInterceptor(usageLimits);

        subject.preview(pendingChanges(false));
        subject.postCommit();

        verifyNoInteractions(usageLimits);
    }

    @Test
    void refreshesOnCreation() {
        final var subject = new TokensCommitInterceptor(usageLimits);

        subject.preview(pendingChanges(true));
        subject.postCommit();
        subject.preview(new EntityChangeSet<>());
        subject.postCommit();

        verify(usageLimits, times(1)).refreshTokens();
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultFinishIsNoop() {
        final var entity = new Object();
        final var subject = mock(CommitInterceptor.class);

        doCallRealMethod().when(subject).finish(0, entity);
        doCallRealMethod().when(subject).postCommit();

        assertDoesNotThrow(() -> subject.finish(0, entity));
        assertDoesNotThrow(subject::postCommit);
    }

    private EntityChangeSet<TokenID, MerkleToken, TokenProperty> pendingChanges(
            final boolean includeCreation) {
        final EntityChangeSet<TokenID, MerkleToken, TokenProperty> pendingChanges =
                new EntityChangeSet<>();
        if (includeCreation) {
            pendingChanges.include(TokenID.newBuilder().setTokenNum(1234).build(), null, Map.of());
        }
        pendingChanges.include(
                TokenID.newBuilder().setTokenNum(1235).build(), new MerkleToken(), Map.of());
        return pendingChanges;
    }
}
