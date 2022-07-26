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

import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.validation.UsageLimits;
import com.hederahashgraph.api.proto.java.TokenID;

/**
 * Minimal interceptor to update token utilization statistics when a token is created. (Expired
 * tokens are removed directly from the backing map, not as part of a ledger transaction.)
 */
public class TokensCommitInterceptor
        implements CommitInterceptor<TokenID, MerkleToken, TokenProperty> {
    private final UsageLimits usageLimits;
    private boolean creation;

    public TokensCommitInterceptor(UsageLimits usageLimits) {
        this.usageLimits = usageLimits;
    }

    /** {@inheritDoc} */
    @Override
    public void preview(final EntityChangeSet<TokenID, MerkleToken, TokenProperty> pendingChanges) {
        creation = false;
        final var n = pendingChanges.size();
        if (n == 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            if (pendingChanges.entity(i) == null) {
                creation = true;
                break;
            }
        }
    }

    @Override
    public void postCommit() {
        if (creation) {
            usageLimits.refreshTokens();
        }
    }
}
