/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.crypto.Message;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.legacy.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class CryptoEngine implements Cryptography {

    /**
     * The constant value used as the component name for all threads created by this module.
     */
    public static final String THREAD_COMPONENT_NAME = "adv crypto";

    static {
        // Register the BouncyCastle Provider instance with the JVM
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * The digest provider instance that is used to generate hashes of SelfSerializable objects.
     */
    private final SerializationDigestProvider serializationDigestProvider;

    /**
     * The digest provider instance that is used to generate running hashes.
     */
    private final RunningHashProvider runningHashProvider;

    /**
     * The digest provider instance that is used to compute hashes of {@link Message} instances and byte arrays.
     */
    private final DigestProvider digestProvider;

    /**
     * The verification provider used to perform signature verification of {@link TransactionSignature} instances.
     */
    private final Ed25519VerificationProvider ed25519VerificationProvider;

    /**
     * The verification provider used to perform signature verification of {@link TransactionSignature} instances.
     */
    private final EcdsaSecp256k1VerificationProvider ecdsaSecp256k1VerificationProvider;

    /**
     * The verification provider used to delegate signature verification of {@link TransactionSignature} instances to
     * either the {@code ed25519VerificationProvider} or {@code ecdsaSecp256k1VerificationProvider} as apropos.
     */
    private final DelegatingVerificationProvider delegatingVerificationProvider;

    /**
     * The intake dispatcher instance that handles asynchronous signature verification
     */
    private volatile IntakeDispatcher<TransactionSignature, DelegatingVerificationProvider, AsyncVerificationHandler>
            verificationDispatcher;

    /**
     * the current configuration settings
     */
    private volatile CryptoConfig config;

    /**
     * a pre-computed {@link Map} of each algorithm's {@code null} hash value.
     */
    private Map<DigestType, Hash> nullHashes;

    /**
     * Responsible for creating and managing all threads and threading resources used by this utility.
     */
    private final ThreadManager threadManager;

    /**
     * Constructs a new {@link CryptoEngine} using the provided settings.
     *
     * @param threadManager responsible for managing thread lifecycles
     * @param config        the initial config to be used
     */
    public CryptoEngine(final ThreadManager threadManager, final CryptoConfig config) {
        this.threadManager = threadManager;
        this.config = config;
        this.digestProvider = new DigestProvider();

        this.ed25519VerificationProvider = new Ed25519VerificationProvider();
        this.ecdsaSecp256k1VerificationProvider = new EcdsaSecp256k1VerificationProvider();
        this.delegatingVerificationProvider =
                new DelegatingVerificationProvider(ed25519VerificationProvider, ecdsaSecp256k1VerificationProvider);

        this.serializationDigestProvider = new SerializationDigestProvider();
        this.runningHashProvider = new RunningHashProvider();

        applySettings();
        buildNullHashes();
    }

    /**
     * Supplier implementation for {@link AsyncVerificationHandler}.
     *
     * @param provider  the required {@link OperationProvider} to be used while performing the cryptographic
     *                  transformations
     * @param workItems the {@link List} of items to be processed by the created {@link AsyncOperationHandler}
     *                  implementation
     * @return an {@link AsyncOperationHandler} implementation
     */
    private static AsyncVerificationHandler verificationHandler(
            final OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType> provider,
            final List<TransactionSignature> workItems) {
        return new AsyncVerificationHandler(workItems, provider);
    }

    /**
     * Common private utility method for performing synchronous signature verification.
     *
     * @param signature the signature to be verified
     * @param provider  the underlying provider to be used
     * @param future    the {@link Future} to be associated with the {@link TransactionSignature}
     * @return true if the signature is valid; otherwise false
     */
    private static boolean verifySyncInternal(
            final TransactionSignature signature,
            final OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType> provider,
            final StandardFuture<Void> future) {
        final boolean isValid;

        try {
            isValid = provider.compute(signature, signature.getSignatureType());
            signature.setSignatureStatus(isValid ? VerificationStatus.VALID : VerificationStatus.INVALID);
            signature.setFuture(future);
        } catch (final NoSuchAlgorithmException ex) {
            signature.setFuture(future);
            throw new CryptographyException(ex, LogMarker.EXCEPTION);
        }

        return isValid;
    }

    /**
     * Getter for the current configuration settings used by the {@link CryptoEngine}.
     *
     * @return the current configuration settings
     */
    public synchronized CryptoConfig getSettings() {
        return config;
    }

    /**
     * Setter to allow the configuration settings to be updated at runtime.
     *
     * @param config the configuration settings
     */
    public synchronized void setSettings(final CryptoConfig config) {
        this.config = config;
        applySettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(final byte[] message, final DigestType digestType) {
        return digestSyncInternal(message, digestType, digestProvider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(final SelfSerializable serializable, final DigestType digestType) {
        try {
            return serializationDigestProvider.compute(serializable, digestType);
        } catch (final NoSuchAlgorithmException ex) {
            throw new CryptographyException(ex, LogMarker.EXCEPTION);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(
            final SerializableHashable serializableHashable, final DigestType digestType, final boolean setHash) {
        try {
            final Hash hash = serializationDigestProvider.compute(serializableHashable, digestType);
            if (setHash) {
                serializableHashable.setHash(hash);
            }
            return hash;
        } catch (final NoSuchAlgorithmException ex) {
            throw new CryptographyException(ex, LogMarker.EXCEPTION);
        }
    }

    /**
     * Compute and store hash for null using different digest types.
     */
    private void buildNullHashes() {
        nullHashes = new HashMap<>();
        for (final DigestType digestType : DigestType.values()) {
            final HashBuilder hb = new HashBuilder(digestType);
            nullHashes.put(digestType, hb.build());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getNullHash(final DigestType digestType) {
        return nullHashes.get(digestType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyAsync(@NonNull final List<TransactionSignature> signatures) {
        verificationDispatcher.submit(signatures);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifySync(final TransactionSignature signature) {
        final StandardFuture<Void> future = new StandardFuture<>();
        future.complete(null);
        if (signature.getSignatureType() == SignatureType.ECDSA_SECP256K1) {
            return verifySyncInternal(signature, ecdsaSecp256k1VerificationProvider, future);
        } else {
            return verifySyncInternal(signature, ed25519VerificationProvider, future);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifySync(final List<TransactionSignature> signatures) {
        final StandardFuture<Void> future = new StandardFuture<>();
        future.complete(null);

        boolean finalOutcome = true;

        OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType> provider;
        for (final TransactionSignature signature : signatures) {
            if (signature.getSignatureType() == SignatureType.ECDSA_SECP256K1) {
                provider = ecdsaSecp256k1VerificationProvider;
            } else {
                provider = ed25519VerificationProvider;
            }

            if (!verifySyncInternal(signature, provider, future)) {
                finalOutcome = false;
            }
        }

        return finalOutcome;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifySync(
            final byte[] data, final byte[] signature, final byte[] publicKey, final SignatureType signatureType) {
        if (signatureType == SignatureType.ECDSA_SECP256K1) {
            return ecdsaSecp256k1VerificationProvider.compute(data, signature, publicKey, signatureType);
        } else {
            return ed25519VerificationProvider.compute(data, signature, publicKey, signatureType);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash calcRunningHash(final Hash runningHash, final Hash newHashToAdd, final DigestType digestType) {
        try {
            return runningHashProvider.compute(runningHash, newHashToAdd, digestType);
        } catch (final NoSuchAlgorithmException e) {
            throw new CryptographyException(e, LogMarker.EXCEPTION);
        }
    }

    /**
     * Applies any changes in the {@link CryptoEngine} settings by stopping the {@link IntakeDispatcher} threads,
     * applying the changes, and relaunching the {@link IntakeDispatcher} threads.
     */
    protected synchronized void applySettings() {
        // Cleanup existing (if applicable) background threads
        if (this.verificationDispatcher != null) {
            this.verificationDispatcher.shutdown();
            this.verificationDispatcher = null;
        }

        // Launch new background threads with the new settings
        this.verificationDispatcher = new IntakeDispatcher<>(
                threadManager,
                TransactionSignature.class,
                this.delegatingVerificationProvider,
                config.computeCpuVerifierThreadCount(),
                CryptoEngine::verificationHandler);
    }

    /**
     * Common private utility method for performing synchronous digest computations.
     *
     * @param message    the message contents to be hashed
     * @param digestType the type of digest used to compute the hash
     * @param provider   the underlying provider to be used
     * @return the cryptographic hash for the given message contents
     */
    private Hash digestSyncInternal(final byte[] message, final DigestType digestType, final DigestProvider provider) {
        try {
            return provider.compute(message, digestType);
        } catch (final NoSuchAlgorithmException ex) {
            throw new CryptographyException(ex, LogMarker.EXCEPTION);
        }
    }
}
