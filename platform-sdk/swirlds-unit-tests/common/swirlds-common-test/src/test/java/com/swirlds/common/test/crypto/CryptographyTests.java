/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.crypto;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Message;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.common.threading.futures.FuturePool;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.TestQualifierTags;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CryptographyTests {
    private static CryptoConfig cryptoConfig;
    private static final int PARALLELISM = 16;
    private static final Hash KNOWN_DUMMY_SERIALIZABLE_HASH = new Hash(
            unhex("a19330d1f361a9e8f6433cce909b5d04ec0216788acef9e8977633a8332a1b08ab6b65d821e8ff30f64f1353d46182d1"));
    private static MessageDigestPool digestPool;
    private static SignaturePool ed25519SignaturePool;
    private static ExecutorService executorService;
    private static EcdsaSignedTxnPool ecdsaSignaturePool;
    private static Cryptography cryptography;

    @BeforeAll
    public static void startup() throws NoSuchAlgorithmException {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        cryptoConfig = configuration.getConfigData(CryptoConfig.class);

        assertTrue(cryptoConfig.computeCpuDigestThreadCount() >= 1);

        executorService = Executors.newFixedThreadPool(PARALLELISM);
        cryptography = CryptographyHolder.get();

        digestPool = new MessageDigestPool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 100);
    }

    @AfterAll
    public static void shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @Tag(TIME_CONSUMING)
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void digestAsyncRawTest(final int count) throws ExecutionException, InterruptedException {
        final Message[] messages = new Message[count];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
            final Future<Hash> future = cryptography.digestAsync(messages[i].getPayloadDirect(), DigestType.SHA_384);
            assertNotNull(future);
            assertTrue(digestPool.isValid(messages[i], future.get().getValue()));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void digestSyncRawTest(final int count) {
        final Message[] messages = new Message[count];

        for (int i = 0; i < messages.length; i++) {
            messages[i] = digestPool.next();
            final Hash hash = cryptography.digestSync(messages[i].getPayloadDirect(), DigestType.SHA_384);
            assertTrue(digestPool.isValid(messages[i], hash.getValue()));
        }
    }

    @Test
    void hashableSerializableTest() {
        final SerializableHashable hashable = new SerializableHashableDummy(123, "some string");
        assertNull(hashable.getHash());
        cryptography.digestSync(hashable);
        assertNotNull(hashable.getHash());

        final Hash hash = hashable.getHash();
        assertEquals(KNOWN_DUMMY_SERIALIZABLE_HASH, hash);
        assertArrayEquals(KNOWN_DUMMY_SERIALIZABLE_HASH.getValue(), hash.getValue());
    }

    @ParameterizedTest
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void verifyAsyncEd25519Only(final int count) throws ExecutionException, InterruptedException {
        ed25519SignaturePool = new SignaturePool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 100, true);
        final TransactionSignature[] signatures = new TransactionSignature[count];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = ed25519SignaturePool.next();
            final Triple<byte[], byte[], byte[]> components = extractComponents(signatures[i]);
            final Future<Boolean> future = cryptography.verifyAsync(
                    components.getLeft(), components.getMiddle(), components.getRight(), SignatureType.ED25519);
            assertNotNull(future, "Future should not be null");
            assertTrue(future.get(), "Future should return computed result");
        }
    }

    @ParameterizedTest
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void verifyAsyncEcdsaSecp256k1Only(final int count) throws ExecutionException, InterruptedException {
        /* Note that here the "transactionSize" is limited to the largest support digest type, which is 64 bytes */
        ecdsaSignaturePool = new EcdsaSignedTxnPool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 64);
        final TransactionSignature[] signatures = new TransactionSignature[count];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = ecdsaSignaturePool.next();
            final Triple<byte[], byte[], byte[]> components = extractComponents(signatures[i]);
            final Future<Boolean> future = cryptography.verifyAsync(
                    components.getLeft(), components.getMiddle(), components.getRight(), SignatureType.ECDSA_SECP256K1);
            assertNotNull(future);
            assertTrue(future.get());
        }
    }

    @ParameterizedTest
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void verifySyncEd25519Only(final int count) {
        ed25519SignaturePool = new SignaturePool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 100, true);
        final TransactionSignature[] signatures = new TransactionSignature[count];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = ed25519SignaturePool.next();
            final Triple<byte[], byte[], byte[]> components = extractComponents(signatures[i]);
            assertTrue(cryptography.verifySync(
                    components.getLeft(), components.getMiddle(), components.getRight(), SignatureType.ED25519));
        }
    }

    @ParameterizedTest
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void verifySyncEcdsaSecp256k1Only(final int count) {
        ecdsaSignaturePool = new EcdsaSignedTxnPool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 64);
        final TransactionSignature[] signatures = new TransactionSignature[count];

        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = ecdsaSignaturePool.next();
            final Triple<byte[], byte[], byte[]> components = extractComponents(signatures[i]);
            assertTrue(
                    cryptography.verifySync(
                            components.getLeft(),
                            components.getMiddle(),
                            components.getRight(),
                            SignatureType.ECDSA_SECP256K1),
                    "Signature should be valid");
        }
    }

    @Test
    @Tag(TIME_CONSUMING)
    void verifySyncInvalidEcdsaSecp256k1() {
        ecdsaSignaturePool = new EcdsaSignedTxnPool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 64);
        final TransactionSignature signature = ecdsaSignaturePool.next();
        final Triple<byte[], byte[], byte[]> components = extractComponents(signature);
        Configurator.setAllLevels("", Level.ALL);
        assertFalse(
                cryptography.verifySync(
                        components.getLeft(),
                        Arrays.copyOfRange(components.getMiddle(), 0, components.getMiddle().length - 1),
                        components.getRight(),
                        SignatureType.ECDSA_SECP256K1),
                "Fails for invalid signature");
    }

    @Test
    @Tag(TIME_CONSUMING)
    void verifySyncInvalidEd25519() {
        ed25519SignaturePool = new SignaturePool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 100, true);
        final TransactionSignature signature = ed25519SignaturePool.next();
        final Triple<byte[], byte[], byte[]> components = extractComponents(signature);
        Configurator.setAllLevels("", Level.ALL);
        assertFalse(
                cryptography.verifySync(
                        components.getLeft(),
                        Arrays.copyOfRange(components.getMiddle(), 0, components.getMiddle().length - 1),
                        components.getRight(),
                        SignatureType.ED25519),
                "Fails for invalid signature");
    }

    @ParameterizedTest
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @ValueSource(ints = {1, 10, 49, 98, 101, 25_000, 50_005})
    void verifyAsyncMix(final int count) throws ExecutionException, InterruptedException {
        ecdsaSignaturePool = new EcdsaSignedTxnPool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 64);
        ed25519SignaturePool = new SignaturePool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 100, true);
        final TransactionSignature[] signatures = new TransactionSignature[count];

        boolean useEcdsa = true;
        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = useEcdsa ? ecdsaSignaturePool.next() : ed25519SignaturePool.next();
            final SignatureType type = useEcdsa ? SignatureType.ECDSA_SECP256K1 : SignatureType.ED25519;
            final Triple<byte[], byte[], byte[]> components = extractComponents(signatures[i]);
            final Future<Boolean> future =
                    cryptography.verifyAsync(components.getLeft(), components.getMiddle(), components.getRight(), type);
            assertNotNull(future, "Future should not be null");
            assertTrue(future.get(), "Future should return computed result");
            useEcdsa = !useEcdsa;
        }
    }

    @Test
    @Tag(TIME_CONSUMING)
    void verifySyncEd25519Signature() {
        ed25519SignaturePool = new SignaturePool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 100, true);
        final TransactionSignature signature = ed25519SignaturePool.next();
        assertTrue(cryptography.verifySync(signature), "Should be a valid signature");
    }

    @Test
    void verifySyncEcdsaSignature() {
        ecdsaSignaturePool = new EcdsaSignedTxnPool(cryptoConfig.computeCpuDigestThreadCount() * PARALLELISM, 64);
        final TransactionSignature signature = ecdsaSignaturePool.next();
        assertTrue(cryptography.verifySync(signature), "Should be a valid signature");
    }

    private Triple<byte[], byte[], byte[]> extractComponents(final TransactionSignature signature) {
        final ByteBuffer buffer = ByteBuffer.wrap(signature.getContentsDirect());
        final byte[] data = new byte[signature.getMessageLength()];
        final byte[] publicKey = new byte[signature.getPublicKeyLength()];
        final byte[] signatureBytes = new byte[signature.getSignatureLength()];

        buffer.position(signature.getMessageOffset())
                .get(data)
                .position(signature.getPublicKeyOffset())
                .get(publicKey)
                .position(signature.getSignatureOffset())
                .get(signatureBytes);

        return Triple.of(data, signatureBytes, publicKey);
    }

    private void checkMessages(final Message... messages) throws ExecutionException, InterruptedException {
        int numInvalid = 0;

        for (final Message m : messages) {
            assertNotNull(m.getFuture());
            m.getFuture().get();

            if (!digestPool.isValid(m)) {
                numInvalid++;
            }
        }

        assertEquals(0, numInvalid);
    }

    private void checkSignatures(final TransactionSignature... signatures)
            throws ExecutionException, InterruptedException {
        int numInvalid = 0;

        for (final TransactionSignature sig : signatures) {
            final Future<Void> future = sig.waitForFuture();
            assertNotNull(future);
            future.get();

            if (!VerificationStatus.VALID.equals(sig.getSignatureStatus())) {
                numInvalid++;
            }
        }

        assertEquals(0, numInvalid);
    }

    private void verifyParallel(final TransactionSignature[] signatures, final int parallelism) {
        final FuturePool<Void> futures = parallelExecute(signatures, parallelism, (sItems, offset, count) -> {
            for (int i = offset; i < count; i++) {
                final TransactionSignature signature = sItems[i];

                final boolean isValid = cryptography.verifySync(signature);
                signature.setSignatureStatus(((isValid) ? VerificationStatus.VALID : VerificationStatus.INVALID));
            }
        });

        futures.waitForCompletion();
    }

    private FuturePool<Void> parallelExecute(
            final TransactionSignature[] signatures,
            final int parallelism,
            final SliceConsumer<TransactionSignature, Integer, Integer> executor) {
        final FuturePool<Void> futures = new FuturePool<>();

        if (signatures == null || signatures.length == 0) {
            return futures;
        }

        final int sliceSize = (int) Math.ceil((double) signatures.length / (double) parallelism);

        int offset = 0;
        int sliceLength = sliceSize;

        while ((offset + sliceLength) <= signatures.length) {
            //			futures.add(executor, signatures, offset, offset + sliceLength);
            final int fOffset = offset;
            final int fSliceLength = sliceLength;

            final Future<Void> future = executorService.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    executor.accept(signatures, fOffset, fOffset + fSliceLength);
                    return null;
                }
            });

            futures.add(future);

            for (int i = offset; i < (fOffset + fSliceLength); i++) {
                signatures[i].setFuture(future);
            }

            offset += sliceLength;

            if (offset >= signatures.length) {
                break;
            } else if (signatures.length < offset + sliceLength) {
                sliceLength = signatures.length - offset;
            }
        }

        return futures;
    }
}
