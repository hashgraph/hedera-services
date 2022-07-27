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
package com.hedera.services.sigs.metadata.lookups;

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;

/** Trivial account signing metadata lookup backed by a {@code FCMap<MapKey, MapValue>}. */
public class DefaultAccountLookup implements AccountSigMetaLookup {
    private final AliasManager aliasManager;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

    public DefaultAccountLookup(
            final AliasManager aliasManager,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts) {
        this.aliasManager = aliasManager;
        this.accounts = accounts;
    }

    @Override
    public SafeLookupResult<AccountSigningMetadata> safeLookup(final AccountID id) {
        return lookupByNumber(fromAccountId(id));
    }

    @Override
    public SafeLookupResult<AccountSigningMetadata> aliasableSafeLookup(final AccountID idOrAlias) {
        if (isAlias(idOrAlias)) {
            final var explicitId = aliasManager.lookupIdBy(idOrAlias.getAlias());
            return (explicitId == EntityNum.MISSING_NUM)
                    ? SafeLookupResult.failure(MISSING_ACCOUNT)
                    : lookupByNumber(explicitId);
        } else {
            return lookupByNumber(fromAccountId(idOrAlias));
        }
    }

    private SafeLookupResult<AccountSigningMetadata> lookupByNumber(final EntityNum id) {
        var account = accounts.get().get(id);
        if (account == null) {
            return SafeLookupResult.failure(MISSING_ACCOUNT);
        } else {
            final var key = account.getAccountKey();
            if (key != null && key.isEmpty()) {
                return SafeLookupResult.failure(IMMUTABLE_ACCOUNT);
            }
            return new SafeLookupResult<>(
                    new AccountSigningMetadata(
                            account.getAccountKey(), account.isReceiverSigRequired()));
        }
    }
}
