/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/smart_contract_service.proto">Smart
 * Contract Service</a>.
 */
public interface ContractPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#ContractCreate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ContractCreateTransactionBody}
     * @return the metadata for the contract creation
     */
    TransactionMetadata preHandleCreateContract(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#ContractUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody}
     * @return the metadata for the contract update
     */
    TransactionMetadata preHandleUpdateContract(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#ContractCall}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ContractCallTransactionBody}
     * @return the metadata for the contract call
     */
    TransactionMetadata preHandleContractCall(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#ContractDelete}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody}
     * @return the metadata for the contract deletion
     */
    TransactionMetadata preHandleDeleteContract(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#SystemDelete}
     * transaction that targets a contract, returning the metadata required to, at minimum, validate
     * the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody} targeting a contract
     * @return the metadata for the system contract deletion
     */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#SystemUndelete}
     * transaction that targets a system-deleted contract, returning the metadata required to, at
     * minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody} targeting a contract
     * @return the metadata for the system contract un-deletion
     */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#EthereumTransaction} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.EthereumTransactionBody}
     * @return the metadata for the Ethereum transaction
     */
    TransactionMetadata preHandleCallEthereum(TransactionBody txn, AccountID payer);
}
