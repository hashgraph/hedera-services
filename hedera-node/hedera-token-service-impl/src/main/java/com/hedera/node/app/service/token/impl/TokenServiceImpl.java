/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.schemas.SyntheticRecordsGenerator;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0500TokenSchema;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.SortedSet;
import java.util.function.Supplier;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    private final Supplier<SortedSet<Account>> sysAccts;
    private final Supplier<SortedSet<Account>> stakingAccts;
    private final Supplier<SortedSet<Account>> treasuryAccts;
    private final Supplier<SortedSet<Account>> miscAccts;
    private final Supplier<SortedSet<Account>> blocklistAccts;

    /**
     * Constructor for the token service. Each of the given suppliers should produce a {@link SortedSet}
     * of {@link Account} objects, where each account object represents a SYNTHETIC RECORD (see {@link
     * SyntheticRecordsGenerator} for more details). Even though these sorted sets contain account objects,
     * these account objects may or may not yet exist in state. They're needed for event recovery circumstances
     * @param sysAccts the supplier for system accounts
     * @param stakingAccts the supplier for staking accounts
     * @param treasuryAccts the supplier for treasury accounts
     * @param miscAccts the supplier for miscellaneous accounts
     * @param blocklistAccts the supplier for blocklisted accounts
     */
    public TokenServiceImpl(
            @NonNull final Supplier<SortedSet<Account>> sysAccts,
            @NonNull final Supplier<SortedSet<Account>> stakingAccts,
            @NonNull final Supplier<SortedSet<Account>> treasuryAccts,
            @NonNull final Supplier<SortedSet<Account>> miscAccts,
            @NonNull final Supplier<SortedSet<Account>> blocklistAccts) {
        this.sysAccts = requireNonNull(sysAccts);
        this.stakingAccts = requireNonNull(stakingAccts);
        this.treasuryAccts = requireNonNull(treasuryAccts);
        this.miscAccts = requireNonNull(miscAccts);
        this.blocklistAccts = requireNonNull(blocklistAccts);
    }

    /**
     * Necessary default constructor. See all params constructor for more details
     */
    public TokenServiceImpl() {
        this.sysAccts = Collections::emptySortedSet;
        this.stakingAccts = Collections::emptySortedSet;
        this.treasuryAccts = Collections::emptySortedSet;
        this.miscAccts = Collections::emptySortedSet;
        this.blocklistAccts = Collections::emptySortedSet;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V0490TokenSchema(sysAccts, stakingAccts, treasuryAccts, miscAccts, blocklistAccts));
        registry.register(new V0500TokenSchema());
    }
}
