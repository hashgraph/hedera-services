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
package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/token_service.proto">Token
 * Service</a>.
 */
public interface TokenPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenCreate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenCreateTransactionBody}
     * @return the metadata for the token creation
     */
    TransactionMetadata preHandleCreateToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody}
     * @return the metadata for the token update
     */
    TransactionMetadata preHandleUpdateToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenMint}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenMintTransactionBody}
     * @return the metadata for the token minting
     */
    TransactionMetadata preHandleMintToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenBurn}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenBurnTransactionBody}
     * @return the metadata for the token burning
     */
    TransactionMetadata preHandleBurnToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenDelete}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody}
     * @return the metadata for the token deletion
     */
    TransactionMetadata preHandleDeleteToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenAccountWipe}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody}
     * @return the metadata for the token wipe
     */
    TransactionMetadata preHandleWipeTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenFreezeAccount} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody}
     * @return the metadata for the account freezing
     */
    TransactionMetadata preHandleFreezeTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUnfreezeAccount} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody}
     * @return the metadata for the account unfreezing
     */
    TransactionMetadata preHandleUnfreezeTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenGrantKycToAccount} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody}
     * @return the metadata for the KYC grant
     */
    TransactionMetadata preHandleGrantKycToTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenRevokeKycFromAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody}
     * @return the metadata for the KYC revocation
     */
    TransactionMetadata preHandleRevokeKycFromTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenAssociateToAccount} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody}
     * @return the metadata for the token association
     */
    TransactionMetadata preHandleAssociateTokens(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenDissociateFromAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody}
     * @return the metadata for the token dissociation
     */
    TransactionMetadata preHandleDissociateTokens(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenFeeScheduleUpdate} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody}
     * @return the metadata for the token fee schedule update
     */
    TransactionMetadata preHandleUpdateTokenFeeSchedule(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenPause}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenPauseTransactionBody}
     * @return the metadata for the token pausing
     */
    TransactionMetadata preHandlePauseToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUnpause}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody}
     * @return the metadata for the token un-pausing
     */
    TransactionMetadata preHandleUnpauseToken(TransactionBody txn, AccountID payer);
}
