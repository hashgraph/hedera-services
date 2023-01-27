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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/token_service.proto">Token
 * Service</a>.
 */
public interface TokenPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_CREATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenCreateTransactionBody}
     * @return the metadata for the token creation
     */
    TransactionMetadata preHandleCreateToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_UPDATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenUpdateTransactionBody}
     * @return the metadata for the token update
     */
    TransactionMetadata preHandleUpdateToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_MINT} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenMintTransactionBody}
     * @return the metadata for the token minting
     */
    TransactionMetadata preHandleMintToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_BURN} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenBurnTransactionBody}
     * @return the metadata for the token burning
     */
    TransactionMetadata preHandleBurnToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_DELETE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenDeleteTransactionBody}
     * @return the metadata for the token deletion
     */
    TransactionMetadata preHandleDeleteToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_ACCOUNT_WIPE} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenWipeAccountTransactionBody}
     * @return the metadata for the token wipe
     */
    TransactionMetadata preHandleWipeTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_FREEZE_ACCOUNT} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenFreezeAccountTransactionBody}
     * @return the metadata for the account freezing
     */
    TransactionMetadata preHandleFreezeTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_UNFREEZE_ACCOUNT} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenUnfreezeAccountTransactionBody}
     * @return the metadata for the account unfreezing
     */
    TransactionMetadata preHandleUnfreezeTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_GRANT_KYC_TO_ACCOUNT} transaction, returning
     * the metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenGrantKycTransactionBody}
     * @return the metadata for the KYC grant
     */
    TransactionMetadata preHandleGrantKycToTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_REVOKE_KYC_FROM_ACCOUNT} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link TokenRevokeKycTransactionBody}
     * @return the metadata for the KYC revocation
     */
    TransactionMetadata preHandleRevokeKycFromTokenAccount(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_ASSOCIATE_TO_ACCOUNT} transaction, returning
     * the metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenAssociateTransactionBody}
     * @return the metadata for the token association
     */
    TransactionMetadata preHandleAssociateTokens(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_DISSOCIATE_FROM_ACCOUNT} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link TokenDissociateTransactionBody}
     * @return the metadata for the token dissociation
     */
    TransactionMetadata preHandleDissociateTokens(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_FEE_SCHEDULE_UPDATE} transaction, returning
     * the metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenFeeScheduleUpdateTransactionBody}
     * @return the metadata for the token fee schedule update
     */
    TransactionMetadata preHandleUpdateTokenFeeSchedule(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_PAUSE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenPauseTransactionBody}
     * @return the metadata for the token pausing
     */
    TransactionMetadata preHandlePauseToken(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_UNPAUSE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link TokenUnpauseTransactionBody}
     * @return the metadata for the token un-pausing
     */
    TransactionMetadata preHandleUnpauseToken(TransactionBody txn, AccountID payer);
}
