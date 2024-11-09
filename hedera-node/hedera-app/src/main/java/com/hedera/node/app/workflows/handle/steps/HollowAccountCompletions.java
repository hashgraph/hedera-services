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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.workflows.DispatchOptions.independentDispatch;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.EthereumTransactionHandler;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.records.CryptoUpdateStreamBuilder;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Completes the hollow accounts by finalizing them.
 */
@Singleton
public class HollowAccountCompletions {
    private static final Logger logger = LogManager.getLogger(HollowAccountCompletions.class);

    private final EthereumTransactionHandler ethereumTransactionHandler;

    @Inject
    public HollowAccountCompletions(@NonNull final EthereumTransactionHandler ethereumTransactionHandler) {
        // Dagger2
        this.ethereumTransactionHandler = requireNonNull(ethereumTransactionHandler);
    }

    /**
     * Finalizes the hollow accounts by updating the key on the hollow accounts that need to be finalized.
     * This is done by dispatching a preceding synthetic update transaction. The key is derived from the signature
     * expansion, by looking up the ECDSA key for the alias.
     * The hollow accounts that need to be finalized are determined by the set of hollow accounts that are returned
     * by the pre-handle result.
     * @param userTxn the user transaction component
     * @param dispatch the dispatch
     */
    public void completeHollowAccounts(@NonNull final UserTxn userTxn, @NonNull final Dispatch dispatch) {
        requireNonNull(userTxn);
        requireNonNull(dispatch);
        // Any hollow accounts that must sign to have all needed signatures, need to be finalized
        // as a result of transaction being handled.
        Set<Account> hollowAccounts = userTxn.preHandleResult().getHollowAccounts();
        SignatureVerification maybeEthTxVerification = null;
        if (userTxn.functionality() == ETHEREUM_TRANSACTION) {
            final var ethFinalization = findEthHollowAccount(userTxn);
            if (ethFinalization != null) {
                hollowAccounts = new LinkedHashSet<>(userTxn.preHandleResult().getHollowAccounts());
                hollowAccounts.add(ethFinalization.hollowAccount());
                maybeEthTxVerification = ethFinalization.ethVerification();
            }
        }
        finalizeHollowAccounts(
                dispatch.handleContext(), hollowAccounts, dispatch.keyVerifier(), maybeEthTxVerification, userTxn);
    }

    /**
     * Finds the hollow account that needs to be finalized for the Ethereum transaction.
     * @param userTxn the user transaction component
     * @return the hollow account that needs to be finalized for the Ethereum transaction
     */
    @Nullable
    private EthFinalization findEthHollowAccount(@NonNull final UserTxn userTxn) {
        final var fileStore = userTxn.readableStoreFactory().getStore(ReadableFileStore.class);
        final var maybeEthTxSigs = ethereumTransactionHandler.maybeEthTxSigsFor(
                userTxn.txnInfo().txBody().ethereumTransactionOrThrow(), fileStore, userTxn.config());
        if (maybeEthTxSigs != null) {
            final var alias = Bytes.wrap(maybeEthTxSigs.address());
            final var accountStore = userTxn.readableStoreFactory().getStore(ReadableAccountStore.class);
            final var maybeHollowAccountId = accountStore.getAccountIDByAlias(alias);
            if (maybeHollowAccountId != null) {
                final var maybeHollowAccount = requireNonNull(accountStore.getAccountById(maybeHollowAccountId));
                if (isHollow(maybeHollowAccount)) {
                    return new EthFinalization(
                            maybeHollowAccount,
                            new SignatureVerificationImpl(
                                    Key.newBuilder()
                                            .ecdsaSecp256k1(Bytes.wrap(maybeEthTxSigs.publicKey()))
                                            .build(),
                                    alias,
                                    true));
                }
            }
        }
        return null;
    }

    /**
     * Updates key on the hollow accounts that need to be finalized. This is done by dispatching a preceding
     * synthetic update transaction. The ksy is derived from the signature expansion, by looking up the ECDSA key
     * for the alias.
     *
     * @param context the handle context
     * @param accounts the set of hollow accounts that need to be finalized
     * @param verifier the key verifier
     * @param ethTxVerification the Ethereum transaction verification
     */
    private void finalizeHollowAccounts(
            @NonNull final HandleContext context,
            @NonNull final Set<Account> accounts,
            @NonNull final AppKeyVerifier verifier,
            @Nullable SignatureVerification ethTxVerification,
            @NonNull final UserTxn userTxn) {
        for (final var hollowAccount : accounts) {
            if (!userTxn.stack().hasMoreSystemRecords()) {
                break;
            }

            if (hollowAccount.accountIdOrElse(AccountID.DEFAULT).equals(AccountID.DEFAULT)) {
                // The CryptoCreateHandler uses a "hack" to validate that a CryptoCreate with
                // an EVM address has signed with that alias's ECDSA key; that is, it adds a
                // dummy "hollow account" with the EVM address as an alias. But we don't want
                // to try to finalize such a dummy account, so skip it here.
                continue;
            }
            // get the verified key for this hollow account
            final var verification =
                    ethTxVerification != null && hollowAccount.alias().equals(ethTxVerification.evmAlias())
                            ? ethTxVerification
                            : requireNonNull(
                                    verifier.verificationFor(hollowAccount.alias()),
                                    "Required hollow account verified signature did not exist");
            if (verification.key() != null) {
                if (!IMMUTABILITY_SENTINEL_KEY.equals(hollowAccount.keyOrThrow())) {
                    logger.error("Hollow account {} has a key other than the sentinel key", hollowAccount);
                    return;
                }
                // dispatch synthetic update transaction for updating key on this hollow account
                final var syntheticUpdateTxn = TransactionBody.newBuilder()
                        .cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                                .accountIDToUpdate(hollowAccount.accountId())
                                .key(verification.key())
                                .build())
                        .build();
                final var streamBuilder = context.dispatch(
                        independentDispatch(context.payer(), syntheticUpdateTxn, CryptoUpdateStreamBuilder.class));
                streamBuilder.accountID(hollowAccount.accountIdOrThrow());
            }
        }
    }

    /**
     * A record that contains the hollow account and the Ethereum verification.
     * @param hollowAccount the hollow account
     * @param ethVerification the Ethereum verification
     */
    private record EthFinalization(Account hollowAccount, SignatureVerification ethVerification) {}
}
