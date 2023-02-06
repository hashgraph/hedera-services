/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.contract;

import static com.hedera.node.app.hapi.fees.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getAccountKeyStorageSize;

import com.hedera.node.app.hapi.fees.usage.QueryUsage;
import com.hedera.node.app.hapi.fees.usage.contract.entities.ContractEntitySizes;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import java.nio.charset.StandardCharsets;

public final class ContractGetInfoUsage extends QueryUsage {
    private ContractGetInfoUsage(final Query query) {
        super(query.getContractGetInfo().getHeader().getResponseType());
        addTb(BASIC_ENTITY_ID_SIZE);
        addRb(ContractEntitySizes.CONTRACT_ENTITY_SIZES.fixedBytesInContractRepr());
    }

    public static ContractGetInfoUsage newEstimate(final Query query) {
        return new ContractGetInfoUsage(query);
    }

    public ContractGetInfoUsage givenCurrentKey(final Key key) {
        addRb(getAccountKeyStorageSize(key));
        return this;
    }

    public ContractGetInfoUsage givenCurrentMemo(final String memo) {
        addRb(memo.getBytes(StandardCharsets.UTF_8).length);
        return this;
    }

    public ContractGetInfoUsage givenCurrentTokenAssocs(final int count) {
        addRb(count * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr());
        return this;
    }
}
