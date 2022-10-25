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
import com.hederahashgraph.api.proto.java.TransactionResponse;

/**
 * The pre-handler for the HAPI
 * <a href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/crypto_service.proto">Crypto Service</a>
 * and the
 * <a href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/token_service.proto">Token Service</a>
 */
public interface CryptoPreTransactionHandler extends PreTransactionHandler {
    // HAPI Crypto Service

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoCreate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody}
     * @return the metadata for the account creation
     */
    TransactionMetadata preHandleCryptoCreate(final TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody}
     * @return the metadata for the account update
     */
    TransactionMetadata preHandleUpdateAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoTransfer}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody}
     * @return the metadata for the crypto transfer
     */
    TransactionMetadata preHandleCryptoTransfer(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDelete}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody}
     * @return the metadata for the account deletion
     */
    TransactionMetadata preHandleCryptoDelete(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoApproveAllowance}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody}
     * @return the metadata for the allowance approvals
     */
    TransactionMetadata preHandleApproveAllowances(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDeleteAllowance}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody}
     * @return the metadata for the allowance revocations
     */
    TransactionMetadata preHandleDeleteAllowances(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoAddLiveHash}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody}
     * @return the metadata for the live hash addition
     */
    TransactionMetadata preHandleAddLiveHash(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDeleteLiveHash}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody}
     * @return the metadata for the live hash deletion
     */
    TransactionMetadata preHandleDeleteLiveHash(TransactionBody txn);

    // HAPI Token Service

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenCreate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenCreateTransactionBody}
     * @return the metadata for the token creation
     */
    TransactionMetadata preHandleCreateToken(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody}
     * @return the metadata for the token update
     */
    TransactionMetadata preHandleUpdateToken(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenMint}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenMintTransactionBody}
     * @return the metadata for the token minting
     */
    TransactionMetadata preHandleMintToken(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenBurn}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenBurnTransactionBody}
     * @return the metadata for the token burning
     */
    TransactionMetadata preHandleBurnToken(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenDelete}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody}
     * @return the metadata for the token deletion
     */
    TransactionMetadata preHandleDeleteToken(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenAccountWipe}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody}
     * @return the metadata for the token wipe
     */
    TransactionMetadata preHandleWipeTokenAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenFreezeAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody}
     * @return the metadata for the account freezing
     */
    TransactionMetadata preHandleFreezeTokenAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUnfreezeAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody}
     * @return the metadata for the account unfreezing
     */
    TransactionMetadata preHandleUnfreezeTokenAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenGrantKycToAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody}
     * @return the metadata for the KYC grant
     */
    TransactionMetadata preHandleGrantKycToTokenAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenRevokeKycFromAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody}
     * @return the metadata for the KYC revocation
     */
    TransactionMetadata preHandleRevokeKycFromTokenAccount(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenAssociateToAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody}
     * @return the metadata for the token association
     */
    TransactionMetadata preHandleAssociateTokens(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenDissociateFromAccount}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody}
     * @return the metadata for the token dissociation
     */
    TransactionMetadata preHandleDissociateTokens(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenFeeScheduleUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody}
     * @return the metadata for the token fee schedule update
     */
    TransactionMetadata preHandleUpdateTokenFeeSchedule(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenPause}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenPauseTransactionBody}
     * @return the metadata for the token pausing
     */
    TransactionMetadata preHandlePauseToken(TransactionBody txn);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUnpause}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody}
     * @return the metadata for the token un-pausing
     */
    TransactionMetadata preHandleUnpauseToken(TransactionBody txn);
}
