// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.stream;

import static com.swirlds.logging.legacy.LogMarker.OBJECT_STREAM;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.stream.internal.AbstractLinkedObjectStream;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Accepts a SerializableRunningHashable object each time, calculates and sets its Hash
 * when nextStream is not null, pass this object to the next stream
 *
 * @param <T>
 * 		type of the objects
 */
public class HashCalculatorForStream<T extends RunningHashable & SerializableHashable>
        extends AbstractLinkedObjectStream<T> {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(HashCalculatorForStream.class);
    /** Used for hashing */
    private final Cryptography cryptography;

    public HashCalculatorForStream() {
        this.cryptography = CryptographyHolder.get();
    }

    public HashCalculatorForStream(LinkedObjectStream<T> nextStream) {
        super(nextStream);
        this.cryptography = CryptographyHolder.get();
    }

    public HashCalculatorForStream(LinkedObjectStream<T> nextStream, Cryptography cryptography) {
        super(nextStream);
        this.cryptography = Objects.requireNonNull(cryptography);
    }

    public HashCalculatorForStream(Cryptography cryptography) {
        this.cryptography = Objects.requireNonNull(cryptography);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addObject(T t) {
        // calculate and set Hash for this object
        if (Objects.requireNonNull(t).getHash() == null) {
            cryptography.digestSync(t);
        }
        super.addObject(t);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        super.close();
        logger.info(OBJECT_STREAM.getMarker(), "HashCalculatorForStream is closed");
    }
}
