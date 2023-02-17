/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.assertions;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class NoNftTransfers implements ErroringAssertsProvider<List<TokenTransferList>> {
    public static NoNftTransfers changingNoNftOwners() {
        return new NoNftTransfers();
    }

    @Override
    public ErroringAsserts<List<TokenTransferList>> assertsFor(HapiSpec spec) {
        final List<Throwable> unexpectedOwnershipChanges = new ArrayList<>();
        return tokenTransfers -> {
            for (var tokenTransfer : tokenTransfers) {
                try {
                    final var ownershipChanges = tokenTransfer.getNftTransfersList();
                    Assertions.assertTrue(
                            ownershipChanges.isEmpty(), () -> "Expected no NFT transfers, were: " + ownershipChanges);
                } catch (Throwable t) {
                    unexpectedOwnershipChanges.add(t);
                }
            }
            return unexpectedOwnershipChanges;
        };
    }
}
