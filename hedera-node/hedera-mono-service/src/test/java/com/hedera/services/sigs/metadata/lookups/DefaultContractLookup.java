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

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.utils.EntityNum.fromContractId;

import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.metadata.ContractSigningMetadata;
import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;

public class DefaultContractLookup implements ContractSigMetaLookup {
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

    public DefaultContractLookup(Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts) {
        this.accounts = accounts;
    }

    @Override
    public SafeLookupResult<ContractSigningMetadata> safeLookup(ContractID id) {
        var contract = accounts.get().get(fromContractId(id));
        if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
            return SafeLookupResult.failure(INVALID_CONTRACT);
        } else {
            JKey key;
            if ((key = contract.getAccountKey()) == null || key instanceof JContractIDKey) {
                return SafeLookupResult.failure(IMMUTABLE_CONTRACT);
            } else {
                return new SafeLookupResult<>(
                        new ContractSigningMetadata(key, contract.isReceiverSigRequired()));
            }
        }
    }
}
