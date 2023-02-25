/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.transaction;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.io.SerializableWithKnownLength;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link TransactionSignature}
 * objects. The list of signatures features controlled mutability with a thread-safe and atomic implementation. The
 * transaction internally uses a {@link ReadWriteLock} to provide atomic reads and writes to the underlying list of
 * signatures.
 * <p>
 * The contents provided by this class via {@link #getContents()} must never be mutated. Providing the direct (mutable)
 * reference improves performance by eliminating the need to create copies.
 * </p>
 */
public sealed interface Transaction extends SerializableWithKnownLength permits ConsensusTransaction {

    /**
     * Returns a direct (mutable) reference to the transaction contents/payload. Care must be
     * taken to never modify the array returned by this accessor. Modifying the array will result in undefined
     * behaviors.
     *
     * @return a direct reference to the transaction content/payload
     */
    byte[] getContents();

    /**
     * Get the size of the transaction
     *
     * @return the size of the transaction in the unit of byte
     */
    int getSize();

    /**
     * Internal use accessor that returns a flag indicating whether this is a system transaction.
     *
     * @return {@code true} if this is a system transaction; otherwise {@code false} if this is an application
     * 		transaction
     */
    boolean isSystem();

    /**
     * Returns the byte located at {@code index} position from the transaction content/payload.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @param index
     * 		the index of the byte to be returned
     * @return the byte located at {@code index} position
     * @throws IllegalArgumentException
     * 		if the underlying transaction content is null or a zero-length array
     * @throws ArrayIndexOutOfBoundsException
     * 		if the {@code index} parameter is less than zero or greater than the
     * 		maximum length of the contents
     */
    byte getContents(final int index);

    /**
     * Returns the custom metadata object set via {@link #setMetadata(Object)}.
     *
     * @param <T>
     * 		the type of metadata object to return
     * @return the custom metadata object, or {@code null} if none was set
     * @throws ClassCastException
     * 		if the type of object supplied to {@link #setMetadata(Object)} is not compatible with {@code T}
     */
    <T> T getMetadata();

    /**
     * Attaches a custom object to this transaction meant to store metadata. This object is not serialized
     * and is kept in memory. It must be recalculated by the application after a restart.
     *
     * @param <T>
     * 		the object to attach
     */
    <T> void setMetadata(T metadata);

    /**
     * Returns a {@link List} of {@link TransactionSignature} objects associated with this transaction. This method
     * returns a shallow copy of the original list. This method can return an unmodifiable list.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @return a shallow copy of the original signature list
     */
    List<TransactionSignature> getSignatures();

    /**
     * Efficiently extracts and adds a new signature to this transaction bypassing the need to make copies of the
     * underlying byte arrays.
     *
     * @param signatureOffset
     * 		the offset in the transaction payload where the signature begins
     * @param signatureLength
     * 		the length in bytes of the signature
     * @param publicKeyOffset
     * 		the offset in the transaction payload where the public key begins
     * @param publicKeyLength
     * 		the length in bytes of the public key
     * @param messageOffset
     * 		the offset in the transaction payload where the message begins
     * @param messageLength
     * 		the length of the message in bytes
     * @throws IllegalArgumentException
     * 		if any of the provided offsets or lengths falls outside the array bounds
     * @throws IllegalArgumentException
     * 		if the internal payload of this transaction is null or a zero length array
     */
    void extractSignature(
            final int signatureOffset,
            final int signatureLength,
            final int publicKeyOffset,
            final int publicKeyLength,
            final int messageOffset,
            final int messageLength);

    /**
     * Efficiently extracts and adds a new signature of given signature type to this transaction bypassing the need to
     * make copies of the underlying byte arrays.
     *
     * @param signatureOffset
     * 		the offset in the transaction payload where the signature begins
     * @param signatureLength
     * 		the length in bytes of the signature
     * @param publicKeyOffset
     * 		the offset in the transaction payload where the public key begins
     * @param publicKeyLength
     * 		the length in bytes of the public key
     * @param messageOffset
     * 		the offset in the transaction payload where the message begins
     * @param messageLength
     * 		the length of the message in bytes
     * @param sigType
     * 		signature type
     * @throws IllegalArgumentException
     * 		if any of the provided offsets or lengths falls outside the array bounds
     * @throws IllegalArgumentException
     * 		if the internal payload of this transaction is null or a zero length array
     */
    void extractSignature(
            final int signatureOffset,
            final int signatureLength,
            final int publicKeyOffset,
            final int publicKeyLength,
            final int messageOffset,
            final int messageLength,
            final SignatureType sigType);

    /**
     * Efficiently extracts and adds a new signature to this transaction bypassing the need to make copies of the
     * underlying byte arrays. If the optional expanded public key is provided then the public key offset and length are
     * indices into this array instead of the transaction payload.
     *
     * @param signatureOffset
     * 		the offset in the transaction payload where the signature begins
     * @param signatureLength
     * 		the length in bytes of the signature
     * @param expandedPublicKey
     * 		an optional expanded form of the public key
     * @param publicKeyOffset
     * 		the offset where the public key begins
     * @param publicKeyLength
     * 		the length in bytes of the public key
     * @param messageOffset
     * 		the offset in the transaction payload where the message begins
     * @param messageLength
     * 		the length of the message in bytes
     * @throws IllegalArgumentException
     * 		if any of the provided offsets or lengths falls outside the array bounds
     * @throws IllegalArgumentException
     * 		if the internal payload of this transaction is null or a zero length array
     */
    void extractSignature(
            final int signatureOffset,
            final int signatureLength,
            final byte[] expandedPublicKey,
            final int publicKeyOffset,
            final int publicKeyLength,
            final int messageOffset,
            final int messageLength);

    /**
     * Adds a new {@link TransactionSignature} to this transaction.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @param signature
     * 		the signature to be added
     * @throws IllegalArgumentException
     * 		if the {@code signature} parameter is null
     */
    void add(final TransactionSignature signature);

    /**
     * Adds a list of new {@link TransactionSignature} objects to this transaction.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @param signatures
     * 		the list of signatures to be added
     */
    void addAll(final TransactionSignature... signatures);

    /**
     * Removes a {@link TransactionSignature} from this transaction.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @param signature
     * 		the signature to be removed
     * @return {@code true} if the underlying list was modified; {@code false} otherwise
     */
    boolean remove(final TransactionSignature signature);

    /**
     * Removes a list of {@link TransactionSignature} objects from this transaction.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @param signatures
     * 		the list of signatures to be removed
     * @return {@code true} if the underlying list was modified; {@code false} otherwise
     */
    boolean removeAll(final TransactionSignature... signatures);

    /**
     * Removes all signatures from this transaction.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     */
    void clearSignatures();
}
