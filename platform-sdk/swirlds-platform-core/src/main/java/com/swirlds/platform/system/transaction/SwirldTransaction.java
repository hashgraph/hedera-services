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

package com.swirlds.platform.system.transaction;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.config.TransactionConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

/**
 * A container for an application transaction that contains extra information.
 * <p>
 * Even though this class implements {@link ConsensusTransaction}, there will be some time during which this
 * transaction has not reached consensus. It must implement {@link ConsensusTransaction} so it can be provided to the
 * application as one after it does reach consensus.
 */
public class SwirldTransaction extends ConsensusTransactionImpl implements Comparable<SwirldTransaction> {
    /** ensures that payload is never null even when constructed with the no-args constructor */
    private static final OneOf<PayloadOneOfType> DEFAULT_PAYLOAD =
            new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, Bytes.EMPTY);
    /** class identifier for the purposes of serialization */
    public static final long CLASS_ID = 0x9ff79186f4c4db97L;
    /** current class version */
    private static final int CLASS_VERSION = 1;

    private static final String CONTENT_ERROR = "content is null or length is 0";

    /** The data stored as protobuf */
    private OneOf<PayloadOneOfType> payload = DEFAULT_PAYLOAD;

    public SwirldTransaction() {}

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
        this.payload = new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, Bytes.wrap(contents.clone()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        final Bytes bytes = getBytes();
        out.writeInt((int) bytes.length()); // array length
        bytes.writeTo(out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        final TransactionConfig transactionConfig = ConfigurationHolder.getConfigData(TransactionConfig.class);
        final byte[] contents = in.readByteArray(transactionConfig.transactionMaxBytes());
        this.payload = new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, Bytes.wrap(contents));
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
        return getBytes().hashCode();
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
        if (!(obj instanceof final SwirldTransaction that)) {
            return false;
        }
        return Objects.equals(payload, that.payload);
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

        return this.getBytes().compareTo(that.getBytes());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "payload: " + getBytes().toHex();
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
        return Integer.BYTES // add the the size of array length field
                + getSize(); // add the size of the array
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return (int) getBytes().length();
    }

    private Bytes getBytes() {
        return payload.as();
    }

    @Override
    public @NonNull OneOf<PayloadOneOfType> getPayload() {
        return payload;
    }
}
