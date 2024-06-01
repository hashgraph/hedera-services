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

package com.hedera.node.app.workflows.handle.flow.infra;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.records.CryptoUpdateRecordBuilder;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HollowAccountFinalizer {
    private static final Logger logger = LogManager.getLogger(HollowAccountFinalizer.class);
    private final PreHandleResult preHandleResult;
    private final Configuration configuration;
    private final ReadableStoreFactory readableStoreFactory;
    private final DefaultKeyVerifier verifier;
    private final HandleContextImpl context;

    @Inject
    public HollowAccountFinalizer(
            final PreHandleResult preHandleResult,
            final Configuration configuration,
            final ReadableStoreFactory readableStoreFactory,
            final DefaultKeyVerifier verifier,
            final HandleContextImpl context) {
        this.preHandleResult = preHandleResult;
        this.configuration = configuration;
        this.readableStoreFactory = readableStoreFactory;
        this.verifier = verifier;
        this.context = context;
    }

    public void finalizeHollowAccountsIfAny() {
        final var txnInfo = preHandleResult.txInfo();
        final var functionality = txnInfo.functionality();
        final var txnBody = txnInfo.txBody();
        // Any hollow accounts that must sign to have all needed signatures, need to be finalized
        // as a result of transaction being handled.
        Set<Account> hollowAccounts = preHandleResult.getHollowAccounts();
        SignatureVerification maybeEthTxVerification = null;
        if (functionality == ETHEREUM_TRANSACTION) {
            final var maybeEthTxSigs = CONTRACT_SERVICE
                    .handlers()
                    .ethereumTransactionHandler()
                    .maybeEthTxSigsFor(
                            txnBody.ethereumTransactionOrThrow(),
                            readableStoreFactory.getStore(ReadableFileStore.class),
                            configuration);
            if (maybeEthTxSigs != null) {
                final var alias = Bytes.wrap(maybeEthTxSigs.address());
                final var accountStore = readableStoreFactory.getStore(ReadableAccountStore.class);
                final var maybeHollowAccountId = accountStore.getAccountIDByAlias(alias);
                if (maybeHollowAccountId != null) {
                    final var maybeHollowAccount = requireNonNull(accountStore.getAccountById(maybeHollowAccountId));
                    if (isHollow(maybeHollowAccount)) {
                        hollowAccounts = new LinkedHashSet<>(preHandleResult.getHollowAccounts());
                        hollowAccounts.add(maybeHollowAccount);
                        maybeEthTxVerification = new SignatureVerificationImpl(
                                Key.newBuilder()
                                        .ecdsaSecp256k1(Bytes.wrap(maybeEthTxSigs.publicKey()))
                                        .build(),
                                alias,
                                true);
                    }
                }
            }
        }
        finalizeHollowAccounts(hollowAccounts, maybeEthTxVerification);
    }

    /**
     * Updates key on the hollow accounts that need to be finalized. This is done by dispatching a preceding
     * synthetic update transaction. The ksy is derived from the signature expansion, by looking up the ECDSA key
     * for the alias.
     *
     * @param accounts the set of hollow accounts that need to be finalized
     * @param ethTxVerification
     */
    private void finalizeHollowAccounts(
            @NonNull final Set<Account> accounts, @Nullable SignatureVerification ethTxVerification) {
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        final var precedingHollowAccountRecords = accounts.size();
        final var maxRecords = consensusConfig.handleMaxPrecedingRecords();
        // If the hollow accounts that need to be finalized is greater than the max preceding
        // records allowed throw an exception
        if (precedingHollowAccountRecords >= maxRecords) {
            throw new HandleException(MAX_CHILD_RECORDS_EXCEEDED);
        } else {
            for (final var hollowAccount : accounts) {
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
                    // Note the null key verification callback below; we bypass signature
                    // verifications when doing hollow account finalization
                    final var recordBuilder = context.dispatchPrecedingTransaction(
                            syntheticUpdateTxn, CryptoUpdateRecordBuilder.class, null, context.payer());
                    // For some reason update accountId is set only for the hollow account finalizations and not
                    // for top level crypto update transactions. So we set it here.
                    recordBuilder.accountID(hollowAccount.accountId());
                }
            }
        }
    }
}
