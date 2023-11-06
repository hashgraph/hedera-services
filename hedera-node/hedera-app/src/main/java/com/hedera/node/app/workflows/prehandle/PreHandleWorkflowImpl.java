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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.node.app.spi.HapiUtils.isHollow;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.nodeDueDiligenceFailure;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.preHandleFailure;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.unknownFailure;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implementation of {@link PreHandleWorkflow} */
@Singleton
public class PreHandleWorkflowImpl implements PreHandleWorkflow {
    private static final Logger logger = LogManager.getLogger(PreHandleWorkflowImpl.class);
    /**
     * Used to verify basic syntactic and semantic validity of a transaction.
     *
     * <p>The hashgraph platform gossips {@link Event}s between nodes. Each {@link Event} contains zero or
     * more transactions. We have to parse the bytes for those transactions, and check their validity both
     * semantically and syntactically. This is done by the {@link TransactionChecker}. It is possible that
     * a misbehaving node, or a malicious node, will gossip a transaction that is invalid.
     */
    private final TransactionChecker transactionChecker;
    /** Dispatches transactions to the appropriate {@link TransactionHandler} based on the type of transaction. */
    private final TransactionDispatcher dispatcher;
    /** "Expands" {@link SignaturePair}s by converting prefixes into full keys. */
    private final SignatureExpander signatureExpander;
    /** Verifies signatures */
    private final SignatureVerifier signatureVerifier;
    /** Provides the latest versioned configuration */
    private final ConfigProvider configProvider;
    /** Used for registering notice of transactionIDs seen by this node */
    private final DeduplicationCache deduplicationCache;

    /**
     * Creates a new instance of {@code PreHandleWorkflowImpl}.
     *
     * @param dispatcher the {@link TransactionDispatcher} for invoking the {@link TransactionHandler} for each
     *                   transaction.
     * @param transactionChecker the {@link TransactionChecker} for parsing and verifying the transaction
     * @param signatureVerifier the {@link SignatureVerifier} to verify signatures
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    @Inject
    public PreHandleWorkflowImpl(
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final ConfigProvider configProvider,
            @NonNull final DeduplicationCache deduplicationCache) {
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signatureVerifier = requireNonNull(signatureVerifier);
        this.signatureExpander = requireNonNull(signatureExpander);
        this.configProvider = requireNonNull(configProvider);
        this.deduplicationCache = requireNonNull(deduplicationCache);
    }

    /** {@inheritDoc} */
    @Override
    public void preHandle(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final AccountID creator,
            @NonNull final Stream<Transaction> transactions) {

        requireNonNull(readableStoreFactory);
        requireNonNull(creator);
        requireNonNull(transactions);

        // Used for looking up payer account information.
        final var accountStore = readableStoreFactory.getStore(ReadableAccountStore.class);

        // In parallel, we will pre-handle each transaction.
        transactions.parallel().forEach(tx -> {
            if (tx.isSystem()) return;
            try {
                tx.setMetadata(preHandleTransaction(creator, readableStoreFactory, accountStore, tx));
            } catch (final Exception unexpectedException) {
                // If some random exception happened, then we should not charge the node for it. Instead,
                // we will just record the exception and try again during handle. Then if we fail again
                // at handle, then we will throw away the transaction (hopefully, deterministically!)
                logger.error("Unexpected error while pre handling a transaction!", unexpectedException);
                tx.setMetadata(unknownFailure());
            }
        });
    }

    // For each transaction, we will use a background thread to parse the transaction, validate it, lookup the
    // payer, collect non-payer keys, and warm up the cache. Then, once all the keys have been collected, we will
    // pass the keys and signatures to the platform for verification.
    @Override
    @NonNull
    public PreHandleResult preHandleTransaction(
            @NonNull final AccountID creator,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final Transaction platformTx) {

        // 1. Parse the Transaction and check the syntax
        final var txBytes = Bytes.wrap(platformTx.getContents());
        final TransactionInfo txInfo;
        try {
            txInfo = transactionChecker.parseAndCheck(txBytes);

            // The transaction account ID MUST have matched the creator!
            if (!creator.equals(txInfo.txBody().nodeAccountID())) {
                throw new PreCheckException(INVALID_NODE_ACCOUNT);
            }
        } catch (PreCheckException preCheck) {
            // The node SHOULD have verified the transaction before it was submitted to the network.
            // Since it didn't, it has failed in its due diligence and will be charged accordingly.
            logger.debug("Transaction failed pre-check", preCheck);
            return nodeDueDiligenceFailure(creator, preCheck.responseCode(), null);
        }

        // Also register this txID as having been seen (we don't actually do deduplication in the pre-handle because
        // deduplication needs to be done deterministically, but we will keep track of the fact that we have seen this
        // transaction ID, so we can give proper results in the different receipt queries)
        deduplicationCache.add(txInfo.transactionID());

        // 2. Get Payer Account
        final var payer = txInfo.payerID();
        final var payerAccount = accountStore.getAccountById(payer);
        if (payerAccount == null) {
            // If the payer account doesn't exist, then we cannot gather signatures for it, and will need to do
            // so later during the handle phase. Technically, we could still try to gather and verify the other
            // signatures, but that might be tricky and complicated with little gain. So just throw.
            return preHandleFailure(creator, null, PAYER_ACCOUNT_NOT_FOUND, txInfo, null, null, null);
        } else if (payerAccount.deleted()) {
            // this check is not guaranteed, it should be checked again in handle phase. If the payer account is
            // deleted, we skip the signature verification.
            return preHandleFailure(creator, null, PAYER_ACCOUNT_DELETED, txInfo, null, null, null);
        }

        // 3. Expand and verify signatures
        return expandAndVerifySignatures(txInfo, payer, payerAccount, storeFactory);
    }

    /**
     * Expands and verifies the payer signature and other require signatures for the transaction.
     * @param txInfo the transaction info
     * @param payer the payer account ID
     * @param payerAccount the payer account
     * @param storeFactory the store factory
     * @return the pre-handle result
     */
    private PreHandleResult expandAndVerifySignatures(
            final TransactionInfo txInfo,
            final AccountID payer,
            final Account payerAccount,
            final ReadableStoreFactory storeFactory) {
        // Bootstrap the expanded signature pairs by grabbing all prefixes that are "full" keys already
        final var originals = txInfo.signatureMap().sigPairOrElse(emptyList());
        final var expanded = new HashSet<ExpandedSignaturePair>();
        signatureExpander.expand(originals, expanded);

        // 1a. Create the PreHandleContext. This will get reused across several calls to the transaction handlers
        final PreHandleContext context;
        final VersionedConfiguration configuration = configProvider.getConfiguration();
        try {
            // NOTE: Once PreHandleContext is moved from being a concrete implementation in SPI, to being an Interface/
            // implementation pair, with the implementation in `hedera-app`, then we will change the constructor,
            // so I can pass the payer account in directly, since I've already looked it up. But I don't really want
            // that as a public API in the SPI, so for now, we do a double lookup. Boo.
            context = new PreHandleContextImpl(storeFactory, txInfo.txBody(), configuration, dispatcher);
        } catch (PreCheckException preCheck) {
            // This should NEVER happen. The only way an exception is thrown from the PreHandleContext constructor
            // is if the payer account doesn't exist, but by the time we reach this line of code, we already know
            // that it does exist.
            throw new RuntimeException(
                    "Payer account disappeared between preHandle and preHandleContext creation!", preCheck);
        }

        // 2. Expand the Payer signature
        final Key payerKey;
        if (!isHollow(payerAccount)) {
            // If the account IS a hollow account, then we will discover all such possible signatures when expanding
            // all "full prefix" keys above, so we already have it covered. We only need to do this if the payer is
            // NOT a hollow account (which is the common case).
            payerKey = payerAccount.keyOrThrow();
            signatureExpander.expand(payerKey, originals, expanded);
        } else {
            payerKey = null;
            // If the account is hollow and since it is the payer that needs to sign the transaction, we need to
            // add to the list of requiredHollowAccounts so that we can finalize the hollow accounts in handle workflow
            context.requireSignatureForHollowAccount(payerAccount);
        }

        // 2b. Call Pre-Transaction Handlers
        try {
            // First, perform semantic checks on the transaction
            dispatcher.dispatchPureChecks(txInfo.txBody());
            // Then gather the signatures from the transaction handler
            dispatcher.dispatchPreHandle(context);
            // FUTURE: Finally, let the transaction handler do warm up of other state it may want to use later (TBD)
        } catch (PreCheckException preCheck) {
            // It is quite possible those semantic checks and other tasks will fail and throw a PreCheckException.
            // In that case, the payer will end up paying for the transaction. So we still need to do the signature
            // verifications that we have determined so far.
            logger.debug("Transaction failed pre-check", preCheck);
            final var results = signatureVerifier.verify(txInfo.signedBytes(), expanded);
            return preHandleFailure(payer, payerKey, preCheck.responseCode(), txInfo, Set.of(), Set.of(), results);
        }

        // 3. Expand additional SignaturePairs based on gathered keys (we can safely ignore hollow accounts because we
        // already grabbed them when expanding the "full prefix" keys above)
        signatureExpander.expand(context.requiredNonPayerKeys(), originals, expanded);
        signatureExpander.expand(context.optionalNonPayerKeys(), originals, expanded);

        // 4. Submit the expanded SignaturePairs to the cryptography engine for verification
        final var results = signatureVerifier.verify(txInfo.signedBytes(), expanded);

        // 5. Create and return TransactionMetadata
        return new PreHandleResult(
                payer,
                payerKey,
                SO_FAR_SO_GOOD,
                OK,
                txInfo,
                context.requiredNonPayerKeys(),
                context.requiredHollowAccounts(),
                results,
                null,
                configuration.getVersion());
    }
}
