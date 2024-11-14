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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;

/**
 *
 */
@FunctionalInterface
public interface CreateDecoderFunction {

    /**
     * @param input the raw input of thе call to decode
     * @param senderID the account id of the sender account
     * @param nativeOps the native Hedera operations
     * @param converter the address id converter
     * @return the transaction body of the call
     */
    TransactionBody decode(
            byte[] input, AccountID senderID, HederaNativeOperations nativeOps, AddressIdConverter converter);
}
