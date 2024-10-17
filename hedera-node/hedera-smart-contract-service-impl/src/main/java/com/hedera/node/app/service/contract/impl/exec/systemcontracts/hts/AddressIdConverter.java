/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class to convert address to account id
 */
public interface AddressIdConverter {
    /**
     * Given an address to be referenced in a synthetic {@link com.hedera.hapi.node.transaction.TransactionBody},
     * returns the {@link AccountID} that should be used in the synthetic transaction.
     *
     * @param address the address to be used in the synthetic transaction
     * @return the {@link AccountID} that should be used in the synthetic transaction
     */
    @NonNull
    AccountID convert(@NonNull Address address);

    /**
     * Given a Besu sender address to be referenced in a synthetic {@link com.hedera.hapi.node.transaction.TransactionBody},
     * returns the {@link AccountID} that should be used in the synthetic transaction.
     *
     * @param address the address to be used in the synthetic transaction
     * @return the {@link AccountID} that should be used in the synthetic transaction
     */
    default AccountID convertSender(@NonNull org.hyperledger.besu.datatypes.Address address) {
        return convert(asHeadlongAddress(address.toArrayUnsafe()));
    }

    /**
     * Given an address to be credited in a synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody},
     * returns the {@link AccountID} that should be used in the synthetic transaction.
     *
     * <p>Follows the logic used in mono-service, despite it being slightly odd in the case of non-canonical
     * references (i.e., when an account with a EVM address is referenced by its long-zero address).
     *
     * @param address the address to be used in the synthetic transaction
     * @return the {@link AccountID} that should be used in the synthetic transaction
     */
    @NonNull
    AccountID convertCredit(@NonNull final Address address);
}
