/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.contract;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface ContractPreTransactionHandler extends PreTransactionHandler {
    /** Creates a contract */
    TransactionMetadata preHandleCreateContract(TransactionBody txn);

    /** Updates a contract with the content */
    TransactionMetadata preHandleUpdateContract(TransactionBody txn);

    /** Calls a contract */
    TransactionMetadata preHandleContractCallMethod(TransactionBody txn);

    /** Deletes a contract instance and transfers any remaining hbars to a specified receiver */
    TransactionMetadata preHandleDeleteContract(TransactionBody txn);

    /** Deletes a contract if the submitting account has network admin privileges */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn);

    /** Undeletes a contract if the submitting account has network admin privileges */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn);

    /** Ethereum transaction */
    TransactionMetadata preHandleCallEthereum(TransactionBody txn);
}
