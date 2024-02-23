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

package com.swirlds.common.crypto;

import com.swirlds.common.io.SelfSerializable;
import java.util.List;
import java.util.concurrent.Future;

public interface Cryptography {
    /** The default value for the setHash argument */
    boolean DEFAULT_SET_HASH = true;
    /** The default digest type */
    DigestType DEFAULT_DIGEST_TYPE = DigestType.SHA_384;

    /**
     * Computes a cryptographic hash (message digest) for the given message. The resulting hash value will be returned
     * by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()}) has been
     * completed.
     *
     * Note: This implementation is non-blocking and returns almost immediately.
     *
     * @param message
     * 		the message to be hashed
     */
    void digestAsync(final Message message);

    /**
     * Computes a cryptographic hash (message digest) for the given list of messages. The resulting hash value will be
     * returned by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()})
     * has been completed.
     *
     * Note: This implementation is non-blocking and returns almost immediately.
     *
     * @param messages
     * 		a list of messages to be hashed
     */
    void digestAsync(final List<Message> messages);

    /**
     * Computes a cryptographic hash (message digest) for the given message. Convenience method that defaults to {@link
     * DigestType#SHA_384} message digests.
     *
     * Note: This implementation is non-blocking and returns almost immediately.
     *
     * @param message
     * 		the message contents to be hashed
     * @return a {@link Future} containing the cryptographic hash for the given message contents when resolved
     */
    default Future<Hash> digestAsync(final byte[] message) {
        return digestAsync(message, DEFAULT_DIGEST_TYPE);
    }

    /**
     * Computes a cryptographic hash (message digest) for the given message.
     *
     * Note: This implementation is non-blocking and returns almost immediately.
     *
     * @param message
     * 		the message contents to be hashed
     * @param digestType
     * 		the type of digest used to compute the hash
     * @return a {@link Future} containing the cryptographic hash for the given message contents when resolved
     */
    Future<Hash> digestAsync(final byte[] message, final DigestType digestType);

    /**
     * Computes a cryptographic hash (message digest) for the given message. The resulting hash value will be
     * returned by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()})
     * has been completed.
     *
     * @param message
     * 		the message to be hashed
     * @return the cryptographic hash for the given message
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    Hash digestSync(final Message message);

    /**
     * Computes a cryptographic hash (message digest) for the given list of messages. The resulting hash value will be
     * returned by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()})
     * has been completed.
     *
     * @param messages
     * 		a list of messages to be hashed
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    void digestSync(final List<Message> messages);

    /**
     * Computes a cryptographic hash (message digest) for the given message. Convenience method that defaults to {@link
     * DigestType#SHA_384} message digests.
     *
     * @param message
     * 		the message contents to be hashed
     * @return the cryptographic hash for the given message contents
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    default Hash digestSync(final byte[] message) {
        return digestSync(message, DEFAULT_DIGEST_TYPE);
    }

    /**
     * Computes a cryptographic hash (message digest) for the given message.
     *
     * @param message
     * 		the message contents to be hashed
     * @param digestType
     * 		the type of digest used to compute the hash
     * @return the cryptographic hash for the given message contents
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    Hash digestSync(final byte[] message, final DigestType digestType);

    /**
     * Same as {@link #digestSync(SelfSerializable, DigestType)} with DigestType set to SHA_384
     *
     * @param serializable
     * 		the object to be hashed
     * @return the cryptographic hash for the {@link SelfSerializable} object
     */
    default Hash digestSync(final SelfSerializable serializable) {
        return digestSync(serializable, DEFAULT_DIGEST_TYPE);
    }

    /**
     * Computes a cryptographic hash for the {@link SelfSerializable} instance by serializing it and hashing the
     * bytes. The hash is then returned by this method
     *
     * @param serializable
     * 		the object to be hashed
     * @param digestType
     * 		the type of digest used to compute the hash
     * @return the cryptographic hash for the {@link SelfSerializable} object
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    Hash digestSync(final SelfSerializable serializable, final DigestType digestType);

    /**
     * Same as {@link #digestSync(SerializableHashable, DigestType)} with DigestType set to SHA_384
     *
     * @param serializableHashable
     * 		the object to be hashed
     */
    default Hash digestSync(final SerializableHashable serializableHashable) {
        return digestSync(serializableHashable, DEFAULT_DIGEST_TYPE);
    }

    /**
     * Same as {@link #digestSync(SerializableHashable, DigestType, boolean)} with setHash set to true
     */
    default Hash digestSync(final SerializableHashable serializableHashable, final DigestType digestType) {
        return digestSync(serializableHashable, digestType, DEFAULT_SET_HASH);
    }

    /**
     * Computes a cryptographic hash for the {@link SerializableHashable} instance by serializing it and hashing the
     * bytes. The hash is then passed to the object by calling {@link Hashable#setHash(Hash)} if setHash is true.
     *
     * @param serializableHashable
     * 		the object to be hashed
     * @param digestType
     * 		the type of digest used to compute the hash
     * @param setHash
     * 		should be set to true if the calculated should be assigned to the serializableHashable object
     * @return the cryptographic hash for the {@link SelfSerializable} object
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    Hash digestSync(final SerializableHashable serializableHashable, final DigestType digestType, boolean setHash);

    /**
     * @return the hash for a null value. Uses SHA_384.
     */
    default Hash getNullHash() {
        return getNullHash(DEFAULT_DIGEST_TYPE);
    }

    /**
     * @param digestType
     * 		the type of digest used to compute the hash
     * @return the hash for a null value.
     */
    Hash getNullHash(final DigestType digestType);

    /**
     * Verifies the given digital signature for authenticity. The result of the verification will be returned by the
     * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
     * TransactionSignature#getFuture()}) has been completed.
     * <p>
     * Note: This implementation is non-blocking and returns almost immediately.
     * <p>
     * Starting in version 0.43 and onwards, the {@link SignatureType#ECDSA_SECP256K1} signature algorithm requires the
     * payload to be a KECCAK-256 hash of the original message. Verification will fail if the message is not 32 bytes in
     * length and the output of 256-bit hashing function.
     *
     * @param signature
     * 		the signature to be verified
     */
    void verifyAsync(final TransactionSignature signature);

    /**
     * Verifies the given digital signatures for authenticity. The result of the verification will be returned by the
     * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
     * TransactionSignature#getFuture()}) has
     * been completed.
     * <p>
     * Note: This implementation is non-blocking and returns almost immediately.
     * <p>
     * Starting in version 0.43 and onwards, the {@link SignatureType#ECDSA_SECP256K1} signature algorithm requires the
     * payload to be a KECCAK-256 hash of the original message. Verification will fail if the message is not 32 bytes in
     * length and the output of 256-bit hashing function.
     *
     * @param signatures
     * 		a list of signatures to be verified
     */
    void verifyAsync(final List<TransactionSignature> signatures);

    /**
     * Verifies the given digital signature for authenticity. Convenience method that defaults to {@link
     * SignatureType#ED25519} signatures.
     * <p>
     * Note: This implementation is non-blocking and returns almost immediately.
     * <p>
     * Starting in version 0.43 and onwards, the {@link SignatureType#ECDSA_SECP256K1} signature algorithm requires the
     * payload to be a KECCAK-256 hash of the original message. Verification will fail if the message is not 32 bytes in
     * length and the output of 256-bit hashing function.
     *
     * @param data
     * 		the original contents that the signature should be verified against
     * @param signature
     * 		the signature to be verified
     * @param publicKey
     * 		the public key required to validate the signature
     * @return a {@link Future} that will contain the true if the signature is valid; otherwise false when
     * 		resolved
     */
    default Future<Boolean> verifyAsync(final byte[] data, final byte[] signature, final byte[] publicKey) {
        return verifyAsync(data, signature, publicKey, SignatureType.ED25519);
    }

    /**
     * Verifies the given digital signature for authenticity.
     * <p>
     * Note: This implementation is non-blocking and returns almost immediately.
     * <p>
     * Starting in version 0.43 and onwards, the {@link SignatureType#ECDSA_SECP256K1} signature algorithm requires the
     * payload to be a KECCAK-256 hash of the original message. Verification will fail if the message is not 32 bytes in
     * length and the output of 256-bit hashing function.
     *
     * @param data
     * 		the original contents that the signature should be verified against
     * @param signature
     * 		the signature to be verified
     * @param publicKey
     * 		the public key required to validate the signature
     * @param signatureType
     * 		the type of signature to be verified
     * @return a {@link Future} that will contain the true if the signature is valid; otherwise false when
     * 		resolved
     */
    Future<Boolean> verifyAsync(
            final byte[] data, final byte[] signature, final byte[] publicKey, final SignatureType signatureType);

    /**
     * Verifies the given digital signature for authenticity. The result of the verification will be returned by the
     * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
     * TransactionSignature#getFuture()}) has
     * been completed.
     * <p>
     * Starting in version 0.43 and onwards, the {@link SignatureType#ECDSA_SECP256K1} signature algorithm requires the
     * payload to be a KECCAK-256 hash of the original message. Verification will fail if the message is not 32 bytes in
     * length and the output of 256-bit hashing function.
     *
     * @param signature
     * 		the signature to be verified
     * @return true if the signature is valid; otherwise false
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    boolean verifySync(final TransactionSignature signature);

    /**
     * Verifies the given digital signatures for authenticity. The result of the verification will be returned by the
     * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
     * TransactionSignature#getFuture()}) has
     * been completed.
     * <p>
     * Starting in version 0.43 and onwards, the {@link SignatureType#ECDSA_SECP256K1} signature algorithm requires the
     * payload to be a KECCAK-256 hash of the original message. Verification will fail if the message is not 32 bytes in
     * length and the output of 256-bit hashing function.
     *
     * @param signatures
     * 		a list of signatures to be verified
     * @return true if all the signatures are valid; otherwise false
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    boolean verifySync(final List<TransactionSignature> signatures);

    /**
     * Verifies the given digital signature for authenticity. Convenience method that defaults to {@link
     * SignatureType#ED25519} signatures.
     *
     * @param data
     * 		the original contents that the signature should be verified against
     * @param signature
     * 		the signature to be verified
     * @param publicKey
     * 		the public key required to validate the signature
     * @return true if the signature is valid; otherwise false
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    default boolean verifySync(final byte[] data, final byte[] signature, final byte[] publicKey) {
        return verifySync(data, signature, publicKey, SignatureType.ED25519);
    }

    /**
     * Verifies the given digital signature for authenticity.
     *
     * @param data
     * 		the original contents that the signature should be verified against
     * @param signature
     * 		the signature to be verified
     * @param publicKey
     * 		the public key required to validate the signature
     * @param signatureType
     * 		the type of signature to be verified
     * @return true if the signature is valid; otherwise false
     * @throws CryptographyException
     * 		if an unrecoverable error occurs while computing the digest
     */
    boolean verifySync(
            final byte[] data, final byte[] signature, final byte[] publicKey, final SignatureType signatureType);

    /**
     * Computes a cryptographic hash for the concatenation of current running Hash and
     * the given newHashToAdd.
     * return the calculated running Hash
     *
     * @param runningHash
     * 		the calculated running {@code Hash}
     * @param newHashToAdd
     * 		a Hash for updating the runningHash
     * @param digestType
     * 		the digest type used to compute runningHash
     * @return calculated running Hash
     */
    Hash calcRunningHash(final Hash runningHash, final Hash newHashToAdd, final DigestType digestType);
}
