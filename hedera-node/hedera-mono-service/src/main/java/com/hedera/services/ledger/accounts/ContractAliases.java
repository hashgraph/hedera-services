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
package com.hedera.services.ledger.accounts;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.isAlias;

import com.hedera.services.ledger.SigImpactHistorian;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public interface ContractAliases {
    void revert();

    void filterPendingChanges(Predicate<Address> filter);

    void commit(@Nullable SigImpactHistorian observer);

    void unlink(Address alias);

    void link(Address alias, Address address);

    boolean isMirror(Address address);

    boolean isInUse(Address address);

    Address resolveForEvm(Address addressOrAlias);

    default Address currentAddress(final ContractID idOrAlias) {
        if (isAlias(idOrAlias)) {
            return resolveForEvm(Address.wrap(Bytes.wrap(idOrAlias.getEvmAddress().toByteArray())));
        }
        return asTypedEvmAddress(idOrAlias);
    }
}
