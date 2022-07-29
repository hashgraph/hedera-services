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
package com.hedera.test.factories.accounts;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

public class MockMMapFactory {
    private final MerkleMap mock = mock(MerkleMap.class);

    private MockMMapFactory() {}

    public static MockMMapFactory newAccounts() {
        return new MockMMapFactory();
    }

    public MockMMapFactory withAccount(String id, MerkleAccount meta) {
        final var account = EntityNum.fromAccountId(asAccount(id));
        given(mock.get(account)).willReturn(meta);
        return this;
    }

    public MockMMapFactory withContract(String id, MerkleAccount meta) {
        final var contract = EntityNum.fromContractId(asContract(id));
        given(mock.get(contract)).willReturn(meta);
        return this;
    }

    public MerkleMap<EntityNum, MerkleAccount> get() {
        return (MerkleMap<EntityNum, MerkleAccount>) mock;
    }
}
