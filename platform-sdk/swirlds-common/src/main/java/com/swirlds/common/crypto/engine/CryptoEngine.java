/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
import com.swirlds.logging.LogMarker;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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
     * The verification provider used to delegate signature verification of {@link TransactionSignature} instances
     * to either the {@code ed25519VerificationProvider} or {@code ecdsaSecp256k1VerificationProvider} as apropos.
     */
    private final DelegatingVerificationProvider delegatingVerificationProvider;

    /**
     * the total number of available physical processors and physical processor cores
     */
    private final int availableCpuCount;

    /**
     * The intake dispatcher instance that handles asynchronous signature verification
     */
    private volatile IntakeDispatcher<TransactionSignature, DelegatingVerificationProvider, AsyncVerificationHandler>
            verificationDispatcher;
    /**
     * the intake dispatcher instance that handles asynchronous message digests
     */
    private volatile IntakeDispatcher<Message, DigestProvider, AsyncDigestHandler> digestDispatcher;

    /**
     * the {@link ConcurrentLinkedQueue} instance of {@link TransactionSignature} waiting for verification
     */
    private volatile BlockingQueue<List<TransactionSignature>> verificationQueue;

    /**
     * the {@link ConcurrentLinkedQueue} instance of {@link Message} pending message digest computation
     */
    private volatile BlockingQueue<List<Message>> digestQueue;

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
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param config
     * 		the initial config to be used
     */
    public CryptoEngine(final ThreadManager threadManager, final CryptoConfig config) {
        this.threadManager = threadManager;
        this.config = config;
        this.availableCpuCount = Runtime.getRuntime().availableProcessors();
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
     * @param provider
     * 		the required {@link OperationProvider} to be used while performing the cryptographic transformations
     * @param workItems
     * 		the {@link List} of items to be processed by the created {@link AsyncOperationHandler} implementation
     * @return an {@link AsyncOperationHandler} implementation
     */
    private static AsyncVerificationHandler verificationHandler(
            final OperationProvider<TransactionSignature, Void, Boolean, ?, SignatureType> provider,
            final List<TransactionSignature> workItems) {
        return new AsyncVerificationHandler(workItems, provider);
    }

    /**
     * Efficiently builds a {@link TransactionSignature} instance from the supplied components.
     *
     * @param data
     * 		the original contents that the signature should be verified against
     * @param signature
     * 		the signature to be verified
     * @param publicKey
     * 		the public key required to validate the signature
     * @param signatureType
     * 		the type of signature to be verified
     * @return a {@link TransactionSignature} containing the provided components
     */
    private static TransactionSignature wrap(
            final byte[] data, final byte[] signature, final byte[] publicKey, final SignatureType signatureType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data");
        }

        if (signature == null || signature.length == 0) {
            throw new IllegalArgumentException("signature");
        }

        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("publicKey");
        }

        final ByteBuffer buffer = ByteBuffer.allocate(data.length + signature.length + publicKey.length);
        final int sigOffset = data.length;
        final int pkOffset = sigOffset + signature.length;

        buffer.put(data).put(signature).put(publicKey);

        return new TransactionSignature(
                buffer.array(), sigOffset, signature.length, pkOffset, publicKey.length, 0, data.length, signatureType);
    }

    /**
     * Common private utility method for performing synchronous digest computations.
     *
     * @param message
     * 		the message contents to be hashed
     * @param provider
     * 		the underlying provider to be used
     * @param future
     * 		the {@link Future} to be associated with the {@link Message}
     * @return the cryptographic hash for the given message contents
     */
    private static Hash digestSyncInternal(
            final Message message, final DigestProvider provider, final StandardFuture<Void> future) {
        final Hash hash;

        try {
            hash = provider.compute(message, message.getDigestType());
            message.setHash(hash);
        } catch (final NoSuchAlgorithmException ex) {
            message.setFuture(future);
            throw new CryptographyException(ex, LogMarker.EXCEPTION);
        }

        message.setFuture(future);

        return hash;
    }

    /**
     * Common private utility method for performing synchronous signature verification.
     *
     * @param signature
     * 		the signature to be verified
     * @param provider
     * 		the underlying provider to be used
     * @param future
     * 		the {@link Future} to be associated with the {@link TransactionSignature}
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
     * Indicates whether a supported OpenCL framework is installed and available on this system.
     *
     * @return true if OpenCL is available; false otherwise
     */
    public boolean isOpenCLAvailable() {
        return false;
    }

    /**
     * Indicates whether a support GPU is available on this system.
     *
     * @return true if a support GPU is available; false otherwise
     */
    public boolean isGpuAvailable() {
        return false;
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
     * @param config
     * 		the configuration settings
     */
    public synchronized void setSettings(final CryptoConfig config) {
        this.config = config;
        applySettings();
    }

    /**
     * Returns the total number of physical processors and physical processor cores available.
     *
     * @return the total number of physical processors and physical cores
     */
    public int getAvailableCpuCount() {
        return availableCpuCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void digestAsync(final Message message) {
        try {
            digestQueue.put(Collections.singletonList(message));
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void digestAsync(final List<Message> messages) {
        try {
            digestQueue.put(messages);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Hash> digestAsync(final byte[] message, final DigestType digestType) {
        final Message wrappedMessage = new Message(message, digestType);
        try {
            digestQueue.put(Collections.singletonList(wrappedMessage));

            return new WrappingLambdaFuture<>(
                    () -> {
                        try {
                            return wrappedMessage.waitForFuture();
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    },
                    wrappedMessage::getHash);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CryptographyException(ex, LogMarker.TESTING_EXCEPTIONS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(final Message message) {
        final DigestProvider provider = new DigestProvider();
        final StandardFuture<Void> future = new StandardFuture<>();
        future.complete(null);

        return digestSyncInternal(message, provider, future);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void digestSync(final List<Message> messages) {
        final StandardFuture<Void> future = new StandardFuture<>();
        future.complete(null);

        for (final Message message : messages) {
            digestSyncInternal(message, digestProvider, future);
        }
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
    public void verifyAsync(final TransactionSignature signature) {
        try {
            verificationQueue.put(Collections.singletonList(signature));
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyAsync(final List<TransactionSignature> signatures) {
        try {
            verificationQueue.put(signatures);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Boolean> verifyAsync(
            final byte[] data, final byte[] signature, final byte[] publicKey, final SignatureType signatureType) {
        final TransactionSignature wrappedSignature = wrap(data, signature, publicKey, signatureType);
        try {
            verificationQueue.put(Collections.singletonList(wrappedSignature));

            return new WrappingLambdaFuture<>(
                    () -> {
                        try {
                            return wrappedSignature.waitForFuture();
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    },
                    () -> wrappedSignature.getSignatureStatus() == VerificationStatus.VALID);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CryptographyException(ex, LogMarker.TESTING_EXCEPTIONS);
        }
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

        if (this.digestDispatcher != null) {
            this.digestDispatcher.shutdown();
            this.digestDispatcher = null;
        }

        // Resize the dispatcher queues
        final Queue<List<TransactionSignature>> oldVerifierQueue = this.verificationQueue;
        this.verificationQueue = new LinkedBlockingQueue<>(config.cpuVerifierQueueSize());

        final Queue<List<Message>> oldDigestQueue = this.digestQueue;
        this.digestQueue = new LinkedBlockingQueue<>(config.cpuDigestQueueSize());

        if (oldVerifierQueue != null && oldVerifierQueue.size() > 0) {
            this.verificationQueue.addAll(oldVerifierQueue);
        }

        if (oldDigestQueue != null && oldDigestQueue.size() > 0) {
            this.digestQueue.addAll(oldDigestQueue);
        }

        // Launch new background threads with the new settings
        this.verificationDispatcher = new IntakeDispatcher<>(
                threadManager,
                TransactionSignature.class,
                this.verificationQueue,
                this.delegatingVerificationProvider,
                config.computeCpuVerifierThreadCount(),
                CryptoEngine::verificationHandler);

        this.digestDispatcher = new IntakeDispatcher<>(
                threadManager,
                Message.class,
                this.digestQueue,
                this.digestProvider,
                config.computeCpuDigestThreadCount(),
                this::digestHandler);
    }

    /**
     * Supplier implementation for {@link AsyncDigestHandler} used by the
     * {@link #CryptoEngine(ThreadManager, CryptoConfig)}
     * constructor.
     *
     * @param provider
     * 		the required {@link OperationProvider} to be used while performing the cryptographic transformations
     * @param workItems
     * 		the {@link List} of items to be processed by the created {@link AsyncOperationHandler} implementation
     * @return an {@link AsyncOperationHandler} implementation
     */
    private AsyncDigestHandler digestHandler(final DigestProvider provider, final List<Message> workItems) {
        return new AsyncDigestHandler(workItems, provider);
    }

    /**
     * Common private utility method for performing synchronous digest computations.
     *
     * @param message
     * 		the message contents to be hashed
     * @param digestType
     * 		the type of digest used to compute the hash
     * @param provider
     * 		the underlying provider to be used
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
