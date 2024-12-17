/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.dsl.operations.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecNft;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.utilops.RunnableOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Asserts that a given account owns a given NFT.
 */
public class AssertNftOwnerOperation extends AbstractSpecOperation {
    private final SpecNft nft;
    private final SpecAccount owner;

    public AssertNftOwnerOperation(@NonNull final SpecNft nft, @NonNull final SpecAccount owner) {
        super(List.of(nft, owner));
        this.nft = nft;
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        return new RunnableOp(() -> {
            final var network = spec.targetNetworkOrThrow();
            assertEquals(
                    owner.accountOrThrow(network).accountIdOrThrow(),
                    nft.nftOrThrow(network).ownerIdOrThrow());
        });
    }
}
