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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.HederaAddressBook;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.RecordCache;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static java.util.Objects.requireNonNull;

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
    /**
     * Given a {@link com.hedera.hapi.node.base.Transaction} and a list of {@link Key}s, prepares the
     * {@link TransactionSignature}s for submission to the cryptography engine.
     */
    private final SignaturePreparer signaturePreparer;
    /** Verifies the {@link TransactionSignature}s. */
    private final Cryptography cryptography;
    /**
     * The {@link ExecutorService} to use for submitting tasks for transaction pre-handling.
     *
     * <p>Each transaction is handled in a separate thread. The {@link ExecutorService} is used to buffer up these
     * tasks and manage a set of threads that will handle the tasks. In the future when the hashgraph platform has
     * a resource manager, we will use that instead. By supplying this executor service, the code that constructs this
     * object can control the number of threads that are used to handle transactions. This is useful for testing and
     * for proper configuration.
     */
    private final ExecutorService exe;
    /**
     * The {@link RecordCache} is used to check if a transaction has already been processed, to avoid processing
     * the same transaction twice. Each transaction has a {@link TransactionID} that is unique for each transaction.
     */
    private final RecordCache recordCache;

    /**
     * Creates a new instance of {@code PreHandleWorkflowImpl}.
     *
     * @param exe the {@link ExecutorService} for managing submission of pre-handle tasks. This service should be
     *            configured with a maximum number of threads, and with a maximum blocking queue length. This way,
     *            back-pressure can be applied to the hashgraph platform if the event intake rate is too high.
     * @param dispatcher the {@link TransactionDispatcher} for invoking the {@link TransactionHandler} for each
     *                   transaction.
     * @param transactionChecker the {@link TransactionChecker} for parsing and verifying the transaction
     * @param signaturePreparer the {@link SignaturePreparer} to convert the {@link Key}s into
     *                          {@link TransactionSignature}s
     * @param cryptography the {@link Cryptography} component used to verify signatures
     * @param recordCache the {@link RecordCache} used to check if a transaction has already been processed
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    @Inject
    public PreHandleWorkflowImpl(
            @NonNull final ExecutorService exe,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SignaturePreparer signaturePreparer,
            @NonNull final Cryptography cryptography,
            @NonNull final RecordCache recordCache) {
        this.exe = requireNonNull(exe);
        this.dispatcher = requireNonNull(dispatcher);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.recordCache = requireNonNull(recordCache);
    }

    /** {@inheritDoc} */
    @Override
    public void preHandle(
            @NonNull final HederaState state,
            @NonNull final AccountID creator,
            @NonNull final Iterator<Transaction> transactions) {

        // Used for looking up payer account information.
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var accountStore = readableStoreFactory.createAccountStore();

        // Using the executor service, submit a task for each transaction in the event.
        final var tasks = new ArrayList<WorkItem>(1000); // Some arbitrary number
        while (transactions.hasNext()) {
            // Skip platform (system) transactions.
            final var platformTx = transactions.next();
            if (platformTx.isSystem()) continue;

            // Submit the task to the executor service and put the resulting Future as the metadata on the transaction
            final var future = exe.submit(() -> preHandleTransaction(creator, accountStore, cryptography, platformTx));
            tasks.add(new WorkItem(platformTx, future));
        }

        // Waits for all the background threads to complete their work and stores the resulting PreHandleResult
        // as the transaction's metadata.
        for (final var task : tasks) {
            try {
                final var result = task.future.get(1, TimeUnit.SECONDS);
                task.platformTx.setMetadata(result);
            } catch (InterruptedException e) {
                // The thread should only ever be interrupted during shutdown, so we can just log the error.
                logger.error("Interrupted while waiting for a transaction to be pre handled.");
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.error("Unexpected error while pre handling a transaction!", e);
            } catch (TimeoutException e) {
                logger.error("Timed out while waiting for a transaction to be pre handled!", e);
            }
        }
    }

    // For each transaction, we will use a background thread to parse the transaction, validate it, lookup the
    // payer, collect non-payer keys, and warm up the cache. Then, once all the keys have been collected, we will
    // pass the keys and signatures to the platform for verification.
    private PreHandleResult preHandleTransaction(
            @NonNull final AccountID creator,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final Cryptography cryptoEngine,
            @NonNull final Transaction platformTx) {
        // 1. Parse the Transaction and check the syntax
        final var txBytes = Bytes.wrap(platformTx.getContents());
        final TransactionInfo txInfo;
        try {
            txInfo = transactionChecker.parseAndCheck(txBytes);
        } catch (PreCheckException preCheck) {
            // The node SHOULD have verified the transaction before it was submitted to the network.
            // Since it didn't, it has failed in its due diligence and will be charged accordingly.
            return PreHandleResult.nodeDueDiligenceFailure(creator, preCheck.responseCode());
        } catch (Throwable th) {
            // If some random exception happened, then we should not charge the node for it. Instead,
            // we will just record the exception and try again during handle. Then if we fail again
            // at handle, then we will throw away the transaction (hopefully, deterministically!)
            return PreHandleResult.unknownFailure(creator, th);
        }

        // 2. Deduplicate
        final var txBody = txInfo.txBody();
        final var txId = txBody.transactionID();
        assert txId != null : "TransactionID should never be null, transactionChecker forbids it";
        final var duplicate = recordCache.isReceiptPresent(txId);
        if (duplicate) {
            // If the transaction is a duplicate, then the creator node has failed a due-diligence check.
            // It is trying to send multiple transactions through consensus with the same TransactionID!
            // We'll charge the node for this.
            return PreHandleResult.nodeDueDiligenceFailure(creator, DUPLICATE_TRANSACTION, txInfo);
        }

        // NOTE: At this point we have completed the due-diligence checks. Any failures from here on out
        // can be attributed to the transaction itself, and not the node that submitted it. With one exception:
        // if we fail to get the payer account, then we will charge the node that submitted the transaction
        // during HANDLE. This is because the node may have submitted the transaction before the payer account
        // was created, and the node may not have been aware of that.

        // 3. Get Payer Account
        final var payer = txId.accountID();
        assert payer != null : "Payer account cannot be null, transactionChecker forbids it";
        final var accountOptional = accountStore.getAccountById(payer);
        if (accountOptional.isEmpty()) {
            // If the payer account doesn't exist, then we cannot gather signatures for it, and will need to
            // so later during the handle phase. Technically, we could still try to gather and verify the other
            // signatures, but that might be tricky and complicated with little gain. So just throw.
            return PreHandleResult.preHandleFailure(creator, PAYER_ACCOUNT_NOT_FOUND, txInfo);
        }

        // 4. Collect payer TransactionSignatures
        final var payerAccount = accountOptional.get();
        final var payerSignatureBytes = collectSignatures(
                txInfo.signatureMap().sigPairOrThrow(),
                (Key) (Object) payerAccount.getKey().orElseThrow());

        txInfo.signatureMap(); // Yay! A signature map
        // TODO Update txInfo to have the signed body bytes and set it correctly based on deprected or non deprecated field usage

        // For each key that MUST sign this transaction:
        //  1. Look through the signature map for the key prefix. If we do not find it, then we already know the
        //     transaction is not signed, so we can throw (Oh, and by the way, the node becomes the payer again.)
        //     Oh, and if we had a threshold key or key list, we have multiple signatures to check. If we find that
        //     there are not the appropriate sigs, then we can bail. If we find we have them all, then check them all.

        cryptoEngine.verifyAsync(data <-- transaction bytes, signature <-- The signature to use, publicKey <-- the full public key);
        final var payerSig = signaturePreparer.prepareSignature(state, txBytes, txInfo.signatureMap(), payer); // TODO This is wrong, shouldn't take the AccountID
        cryptoEngine.verifyAsync(payerSig);

        // If this is a duplicate transaction, then we only want to verify the payer signature,
        // and then we can skip the rest of the pre-handle logic.
        if (duplicate) {
            // TODO Sig results should be included in the PreHandleResult
            payerSig.waitForFuture();
            return PreHandleResult.preHandleFailure(payer, DUPLICATE_TRANSACTION, txInfo);
        }

        // 5. Call Pre-Transaction Handlers
        final var storeFactory = new ReadableStoreFactory(state); // TODO This looks out of place
        final var accountStore = storeFactory.createAccountStore();
        final var context = new PreHandleContext(accountStore, txInfo.txBody(), OK);
        try {
            dispatcher.dispatchPreHandle(storeFactory, context);

            // TODO Deal with potential inner context
            //            final var innerContext = context.getInnerContext();
            //            TransactionMetadata innerMetadata = null;
            //            if (innerContext != null) {
            //                // VERIFY: the txBytes used for inner transactions is the same as the outer transaction
            //                final var innerPayerSignature = verifyPayerSignature(state, innerContext, txBytes, signatureMap);
            //                final var innerOtherSignatures = verifyOtherSignatures(state, innerContext, txBytes, signatureMap);
            //                innerMetadata = createTransactionMetadata(
            //                        innerContext, signatureMap, innerPayerSignature, innerOtherSignatures, null);
            //            }

        } catch (PreCheckException preCheck) {
            // Pre-handle is the time for the service to perform semantic checks on the transaction.
            // It is quite possible those semantic checks will fail and throw a PreCheckException.
            // In that case, the payer will end up paying for the transaction.
            return PreHandleResult.preHandleFailure(payer, preCheck.responseCode(), txInfo);
        }

        // 6. Collection additional TransactionSignatures
        final var nonPayerKeys = context.getRequiredNonPayerKeys();
        final var signatures = signaturePreparer.prepareSignatures(state, txBytes, txInfo.signatureMap(), nonPayerKeys);
        // TODO What happens if prepareSignatures throws an exception? Can it?
        cryptography.verifyAsync(new ArrayList<>(signatures.values()));

        // 8. Create and return TransactionMetadata
        // TODO Sig results should be included in the PreHandleResult
        //                return new PreHandleResult(txInfo, payer, OK, null, null);
        return null;
        // TODO I did NOT keep a record of what state was accessed during pre-handle, and that was the
        //  whole point!! That needs to be done.
    }


    // TODO Now that we have hollow accounts, if we find that a signature in the sig map is not being used for
    //  anything else and is a 20-byte key, then we assume it is an ethereum key and verify its signature as well?


    // NOTE: These methods are particular to what happens when doing sig verification for HAPI. For system contracts,
    // we want to do something quite the opposite -- reject every key that isn't a contract / delegate contract key.

    private List<Bytes> collectSignatures(
            @NonNull final List<SignaturePair> signaturePairs,
            @NonNull final Key key) {

        return switch (key.key().kind()) {
            // You cannot have a Contract ID key as a required signer on a HAPI transaction
            case UNSET, DELEGATABLE_CONTRACT_ID, CONTRACT_ID -> Collections.emptyList();
            case ECDSA_384 -> collectSignature(signaturePairs, key.ecdsa384OrThrow());
            case ED25519 -> collectSignature(signaturePairs, key.ed25519OrThrow());
            case RSA_3072 -> collectSignature(signaturePairs, key.rsa3072OrThrow());
            case ECDSA_SECP256K1 -> collectSignature(signaturePairs, key.ecdsaSecp256k1OrThrow());
            case KEY_LIST -> collectSignatures(signaturePairs, key.keyListOrThrow());
            case THRESHOLD_KEY -> collectSignatures(signaturePairs, key.thresholdKeyOrThrow());
        };
    }

    @NonNull
    private List<Bytes> collectSignatures(
            @NonNull final List<SignaturePair> signatures,
            @NonNull final KeyList keyList) {
        // Iterate over all the keys in the list, delegating to collectSignatures for each one
        if (!keyList.hasKeys()) {
            // There are no keys in this key list, which means, nothing is authorized.
            return Collections.emptyList();
        }

        final var keys = keyList.keysOrThrow();
        final var collectedSignatures = new ArrayList<Bytes>(keys.size());
        for (final var key : keys) {
            // Every key in a KeyList must be valid, so if any of these keys have no associated signature,
            // then the KeyList as a whole is invalid, and we return an empty list. The KeyList might be
            // part of a ThresholdKey, so we don't want to presume it fails the whole transaction, but at least
            // it makes no sense to validate any signatures from this list.
            final var sigsToAdd = collectSignatures(signatures, key);
            if (sigsToAdd.isEmpty()) {
                return Collections.emptyList();
            }
            collectedSignatures.addAll(sigsToAdd);
        }
        return collectedSignatures;
    }

    @NonNull
    private List<Bytes> collectSignatures(
            @NonNull final List<SignaturePair> signatures,
            @NonNull final ThresholdKey thresholdKey) {
        // Iterate over all the keys in the list, delegating to collectSignatures for each one,
        // and keep track of which signatures were successful and which weren't. The ones that
        // were not successful, we can ignore. If we do not have enough successful signatures,
        // then we throw the appropriate exception.
        if (!thresholdKey.hasKeys()) {
            // There is no key list, which means, nothing is authorized.
            return Collections.emptyList();
        }

        // Iterate over all the keys in the list, delegating to collectSignatures for each one. If they have
        // signatures, then we accumulate them and increase the `numSuccessfulKeys` by 1 (regardless of how many
        // signatures they gathered). At the end, if we have exceeded the threshold, then we return ALL of the
        // signatures we collected. This is really important.
        //
        // Suppose a threshold key is 2/4, meaning that 2 of the 4 keys must sign. Suppose the transaction has
        // 3 signatures on it from 3 of those 4 keys, but only 2 of them are valid signatures, the third being
        // some nonsense. The transaction should succeed. If we short-circuited this method after collecting 2 of 4
        // keys, we might have failed the transaction. So the specification should be that if it is possible to
        // select a set of signatures and keys that would succeed, then a valid consensus node implementation will
        // select those keys to succeed.
        var numSuccessfulKeys = 0;
        final var keyList = thresholdKey.keysOrThrow();
        final var keys = keyList.keysOrElse(Collections.emptyList());
        final var collectedSignatures = new ArrayList<Bytes>(keyList.keysOrThrow().size());
        for (final var key : keys) {
            final var sigs = collectSignatures(signatures, key);
            if (!sigs.isEmpty()) {
                collectedSignatures.addAll(sigs);
                numSuccessfulKeys++;
            }
        }

        // It should be impossible for the threshold to ever be non-positive. But if it were to ever happen,
        // we will treat it as though the threshold were 1. This allows the user to fix their problem and
        // set an appropriate threshold. Likewise, if the threshold is greater than the number of keys, then
        // we clamp to the number of keys. This also shouldn't be possible, but if it happens, we give the
        // user a chance to fix their account.
        var threshold = thresholdKey.threshold();
        if (threshold <= 0) threshold = 1;
        if (threshold > keys.size()) threshold = keys.size();

        // If we didn't meet the minimum threshold for signers, then we're bad.
        return (numSuccessfulKeys >= threshold) ? collectedSignatures : Collections.emptyList();
    }

    @NonNull
    private List<Bytes> collectSignature(
            @NonNull final List<SignaturePair> signatures,
            @NonNull final Bytes keyBytes) {

        for (final var signature : signatures) {
            if (keyBytes.matchesPrefix(signature.pubKeyPrefix())) {
                return List.of(signature.signature().as());
            }
        }
        return Collections.emptyList();
    }

    private record WorkItem(
        @NonNull Transaction platformTx,
        @NonNull Future<PreHandleResult> future) {
    }
}
