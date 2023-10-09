/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.api;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Contains the ordered summary of the contracts added and the nonces updated by a contract operation.
 *
 * @param newContractIds the list of new contract IDs, ordered by contract number
 * @param updatedContractNonces the list of updated contract nonces, ordered by contract number
 */
public record ContractChangeSummary(List<ContractID> newContractIds, List<ContractNonceInfo> updatedContractNonces) {
    private static final Comparator<ContractID> CONTRACT_ID_NUM_COMPARATOR =
            Comparator.comparingLong(ContractID::contractNumOrThrow);
    private static final Comparator<ContractNonceInfo> NONCE_INFO_CONTRACT_ID_COMPARATOR =
            Comparator.comparing(ContractNonceInfo::contractIdOrThrow, CONTRACT_ID_NUM_COMPARATOR);

    public ContractChangeSummary {
        Objects.requireNonNull(newContractIds).sort(CONTRACT_ID_NUM_COMPARATOR);
        Objects.requireNonNull(updatedContractNonces).sort(NONCE_INFO_CONTRACT_ID_COMPARATOR);
    }
}
