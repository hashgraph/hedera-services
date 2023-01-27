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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractDeleteTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/smart_contract_service.proto">Smart
 * Contract Service</a>.
 */
public interface ContractPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link HederaFunctionality#CONTRACT_CREATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ContractCreateTransactionBody}
     * @return the metadata for the contract creation
     */
    TransactionMetadata preHandleCreateContract(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#CONTRACT_UPDATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ConsensusUpdateTopicTransactionBody}
     * @return the metadata for the contract update
     */
    TransactionMetadata preHandleUpdateContract(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#CONTRACT_CALL} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ContractCallTransactionBody}
     * @return the metadata for the contract call
     */
    TransactionMetadata preHandleContractCall(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#CONTRACT_DELETE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ContractDeleteTransactionBody}
     * @return the metadata for the contract deletion
     */
    TransactionMetadata preHandleDeleteContract(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#SYSTEM_DELETE} transaction that targets a contract,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link SystemDeleteTransactionBody} targeting a contract
     * @return the metadata for the system contract deletion
     */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#SYSTEM_UNDELETE} transaction that targets a
     * system-deleted contract, returning the metadata required to, at minimum, validate the
     * signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link SystemUndeleteTransactionBody} targeting a contract
     * @return the metadata for the system contract un-deletion
     */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#ETHEREUM_TRANSACTION} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link EthereumTransactionBody}
     * @return the metadata for the Ethereum transaction
     */
    TransactionMetadata preHandleCallEthereum(TransactionBody txn, AccountID payer);
}
