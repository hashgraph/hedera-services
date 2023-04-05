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

package com.swirlds.common.system.transaction.internal;

import static com.swirlds.common.io.streams.AugmentedDataOutputStream.getArraySerializedLength;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A container for an application transaction that contains extra information.
 * <p>
 * Even though this class implements {@link ConsensusTransaction}, there will be some time during which this
 * transaction has not reached consensus. It must implement {@link ConsensusTransaction} so it can be provided to the
 * application as one after it does reach consensus.
 */
public class SwirldTransaction extends ConsensusTransactionImpl implements Comparable<SwirldTransaction> {
    /** class identifier for the purposes of serialization */
    private static final long CLASS_ID = 0x9ff79186f4c4db97L;
    /** current class version */
    private static final int CLASS_VERSION = 1;

    private static final String CONTENT_ERROR = "content is null or length is 0";
    private static final int DEFAULT_SIGNATURE_LIST_SIZE = 5;

    /** A per-transaction read/write lock to ensure thread safety of the signature list */
    private final ReadWriteLock readWriteLock;

    /** The content (payload) of the transaction */
    private byte[] contents;

    /** The list of optional signatures attached to this transaction */
    private List<TransactionSignature> signatures;

    /** An optional metadata object set by the application */
    private Object metadata;

    public SwirldTransaction() {
        this.readWriteLock = new ReentrantReadWriteLock(false);
    }

    /**
     * Constructs a new transaction with no associated signatures.
     *
     * @param contents
     * 		the binary content/payload of the Swirld transaction
     * @throws IllegalArgumentException
     * 		if the {@code contents} parameter is null or a zero-length array
     */
    public SwirldTransaction(final byte[] contents) {
        if (contents == null || contents.length == 0) {
            throw new IllegalArgumentException(CONTENT_ERROR);
        }
        this.contents = contents.clone();
        this.readWriteLock = new ReentrantReadWriteLock(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeByteArray(contents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.contents = in.readByteArray(SettingsCommon.transactionMaxBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getContents() {
        return contents;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getContents(final int index) {
        if (contents == null || contents.length == 0) {
            throw new IllegalArgumentException(CONTENT_ERROR);
        }

        if (index < 0 || index >= contents.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        return contents[index];
    }

    /**
     * Returns the size of the transaction content/payload.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @return the length of the transaction content
     */
    public int getLength() {
        return (contents != null) ? contents.length : 0;
    }

    /**
     * Returns the size of the transaction content/payload.
     *
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @return the length of the transaction content
     */
    public int size() {
        return getLength();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TransactionSignature> getSignatures() {
        final Lock readLock = readWriteLock.readLock();

        try {
            readLock.lock();
            return (signatures != null) ? new ArrayList<>(signatures) : Collections.emptyList();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void extractSignature(
            final int signatureOffset,
            final int signatureLength,
            final int publicKeyOffset,
            final int publicKeyLength,
            final int messageOffset,
            final int messageLength) {
        add(new TransactionSignature(
                contents,
                signatureOffset,
                signatureLength,
                publicKeyOffset,
                publicKeyLength,
                messageOffset,
                messageLength));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void extractSignature(
            final int signatureOffset,
            final int signatureLength,
            final int publicKeyOffset,
            final int publicKeyLength,
            final int messageOffset,
            final int messageLength,
            final SignatureType sigType) {
        add(new TransactionSignature(
                contents,
                signatureOffset,
                signatureLength,
                publicKeyOffset,
                publicKeyLength,
                messageOffset,
                messageLength,
                sigType));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void extractSignature(
            final int signatureOffset,
            final int signatureLength,
            final byte[] expandedPublicKey,
            final int publicKeyOffset,
            final int publicKeyLength,
            final int messageOffset,
            final int messageLength) {
        add(new TransactionSignature(
                contents,
                signatureOffset,
                signatureLength,
                expandedPublicKey,
                publicKeyOffset,
                publicKeyLength,
                messageOffset,
                messageLength));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(final TransactionSignature signature) {
        if (signature == null) {
            throw new IllegalArgumentException("signature");
        }

        final Lock writeLock = readWriteLock.writeLock();

        try {
            writeLock.lock();
            if (signatures == null) {
                signatures = new ArrayList<>(DEFAULT_SIGNATURE_LIST_SIZE);
            }

            signatures.add(signature);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAll(final TransactionSignature... signatures) {
        if (signatures == null || signatures.length == 0) {
            return;
        }

        final Lock writeLock = readWriteLock.writeLock();

        try {
            writeLock.lock();
            if (this.signatures == null) {
                this.signatures = new ArrayList<>(signatures.length);
            }

            this.signatures.addAll(Arrays.asList(signatures));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(final TransactionSignature signature) {
        if (signature == null) {
            return false;
        }

        final Lock writeLock = readWriteLock.writeLock();

        try {
            writeLock.lock();
            if (signatures == null) {
                return false;
            }

            return signatures.remove(signature);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeAll(final TransactionSignature... signatures) {
        if (signatures == null || signatures.length == 0) {
            return false;
        }

        final Lock writeLock = readWriteLock.writeLock();

        try {
            writeLock.lock();
            if (this.signatures == null) {
                return false;
            }

            return this.signatures.removeAll(Arrays.asList(signatures));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearSignatures() {
        final Lock writeLock = readWriteLock.writeLock();

        try {
            writeLock.lock();
            if (signatures == null) {
                return;
            }

            signatures = null;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit of hash tables such as those
     * provided by {@link HashMap}.
     * <p>
     * The general contract of {@code hashCode} is:
     * <ul>
     * <li>Whenever it is invoked on the same object more than once during an execution of a Java application, the
     * {@code hashCode} method must consistently return the same integer, provided no information used in {@code equals}
     * comparisons on the object is modified. This integer need not remain consistent from one execution of an
     * application to another execution of the same application.
     * <li>If two objects are equal according to the {@code equals(Object)} method, then calling the {@code hashCode}
     * method on each of the two objects must produce the same integer result.
     * <li>It is <em>not</em> required that if two objects are unequal according to the {@link Object#equals(Object)}
     * method, then calling the {@code hashCode} method on each of the two objects must produce distinct integer
     * results. However, the programmer should be aware that producing distinct integer results for unequal objects may
     * improve the performance of hash tables.
     * </ul>
     * <p>
     * As much as is reasonably practical, the hashCode method defined by class {@code Object} does return distinct
     * integers for distinct objects. (This is typically implemented by converting the internal address of the object
     * into an integer, but this implementation technique is not required by the Java&trade; programming language.)
     *
     * @return a hash code value for this object.
     * @see Object#equals(Object)
     * @see System#identityHashCode
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(contents);
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p>
     * The {@code equals} method implements an equivalence relation on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value {@code x}, {@code x.equals(x)} should return
     * {@code true}.
     * <li>It is <i>symmetric</i>: for any non-null reference values {@code x} and {@code y}, {@code x.equals(y)} should
     * return {@code true} if and only if {@code y.equals(x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any non-null reference values {@code x}, {@code y}, and {@code z}, if
     * {@code x.equals(y)} returns {@code true} and {@code y.equals(z)} returns {@code true}, then {@code x.equals(z)}
     * should return {@code true}.
     * <li>It is <i>consistent</i>: for any non-null reference values {@code x} and {@code y}, multiple invocations of
     * {@code x.equals(y)} consistently return {@code true} or consistently return {@code false}, provided no
     * information used in {@code equals} comparisons on the objects is modified.
     * <li>For any non-null reference value {@code x}, {@code x.equals(null)} should return {@code false}.
     * </ul>
     * <p>
     * The {@code equals} method for class {@code Object} implements the most discriminating possible equivalence
     * relation on objects; that is, for any non-null reference values {@code x} and {@code y}, this method returns
     * {@code true} if and only if {@code x} and {@code y} refer to the same object ({@code x == y} has the value
     * {@code true}).
     * <p>
     * Note that it is generally necessary to override the {@code hashCode} method whenever this method is overridden,
     * so as to maintain the general contract for the {@code hashCode} method, which states that equal objects must have
     * equal hash codes.
     *
     * @param obj
     * 		the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise.
     * @see #hashCode()
     * @see HashMap
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SwirldTransaction)) {
            return false;
        }
        final SwirldTransaction that = (SwirldTransaction) obj;
        return Arrays.equals(contents, that.contents);
    }

    /**
     * Compares this object with the specified object for order. Returns a negative integer, zero, or a positive integer
     * as this object is less than, equal to, or greater than the specified object.
     *
     * <p>
     * The implementor must ensure <code>sgn(x.compareTo(y)) ==
     * -sgn(y.compareTo(x))</code> for all <code>x</code> and <code>y</code>. (This implies that
     * <code>x.compareTo(y)</code> must
     * throw an exception iff <code>y.compareTo(x)</code> throws an exception.)
     *
     * <p>
     * The implementor must also ensure that the relation is transitive:
     * <code>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</code> implies <code>x.compareTo(z)&gt;0</code>.
     *
     * <p>
     * Finally, the implementor must ensure that <code>x.compareTo(y)==0</code> implies that
     * <code>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</code>, for all <code>z</code>.
     *
     * <p>
     * It is strongly recommended, but <i>not</i> strictly required that <code>(x.compareTo(y)==0) ==
     * (x.equals(y))</code>.
     * Generally speaking, any class that implements the <code>Comparable</code> interface and violates this condition
     * should clearly indicate this fact. The recommended language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     *
     * <p>
     * In the foregoing description, the notation <code>sgn(</code><i>expression</i><code>)</code> designates the
     * mathematical
     * <i>signum</i> function, which is defined to return one of <code>-1</code>, <code>0</code>, or <code>1</code>
     * according to
     * whether the value of <i>expression</i> is negative, zero or positive.
     *
     * @param that
     * 		the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
     * 		the specified object.
     * @throws IllegalArgumentException
     * 		if the specified object is null
     * @throws ClassCastException
     * 		if the specified object's type prevents it from being compared to this object.
     */
    @Override
    public int compareTo(final SwirldTransaction that) {
        if (this == that) {
            return 0;
        }

        if (that == null) {
            throw new IllegalArgumentException();
        }

        return Arrays.compare(contents, that.contents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("contents", contents)
                .append("signatures", signatures)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedLength() {
        return getArraySerializedLength(contents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return contents == null ? 0 : contents.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T getMetadata() {
        return (T) metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void setMetadata(final T metadata) {
        this.metadata = metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSystem() {
        return false;
    }
}
