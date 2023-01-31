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
package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/crypto_service.proto">Crypto
 * Service</a>.
 */
public interface CryptoPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoCreate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody}
     * @return the metadata for the account creation
     */
    TransactionMetadata preHandleCryptoCreate(final TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody}
     * @return the metadata for the account update
     */
    TransactionMetadata preHandleUpdateAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoTransfer}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody}
     * @return the metadata for the crypto transfer
     */
    TransactionMetadata preHandleCryptoTransfer(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDelete}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody}
     * @return the metadata for the account deletion
     */
    TransactionMetadata preHandleCryptoDelete(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoApproveAllowance} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody}
     * @return the metadata for the allowance approvals
     */
    TransactionMetadata preHandleApproveAllowances(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDeleteAllowance} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody}
     * @return the metadata for the allowance revocations
     */
    TransactionMetadata preHandleDeleteAllowances(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoAddLiveHash} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody}
     * @return the metadata for the live hash addition
     */
    TransactionMetadata preHandleAddLiveHash(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDeleteLiveHash} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody}
     * @return the metadata for the live hash deletion
     */
    TransactionMetadata preHandleDeleteLiveHash(TransactionBody txn);
}
