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

package com.swirlds.platform;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A class similar to Random, but with extra methods to clone it and add entropy, and without thread safety.
 * <p>
 * The {@link #clone} method makes a copy, after which both the original and copy will generate identical
 * sequences of pseudorandom numbers. The {@link #absorbEntropy(long)} method can be used at any time to give
 * additional entropy. Each pseudorandom number that is generated is a deterministic function of the initial
 * seed, of the entropy given it by all calls to {@link #absorbEntropy(long)} so far, and by when in the sequence
 * those calls were made.
 * <p>
 * It is certainly not cryptographically secure. It might not even be good enough for some simulations. But
 * it may be fine for casual use, such as for a game.
 */
public class RandomExtended extends Random implements SelfSerializable, FastCopyable {

    /**
     * The version history of this class.
     * Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        /**
         * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
         * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
         * specially by the platform.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, serialization was performed by serialize/deserialize.
         */
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
    }

    private static final long CLASS_ID = 0xefb0c8ec488537fL;

    private long seed = 0L;
    private static final long serialVersionUID = 1L;
    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;
    private static final AtomicLong seedUniquifier = new AtomicLong(8682522807148012L);

    private boolean immutable;

    /**
     * Initialize with a random seed, based on the current time.
     */
    public RandomExtended() {
        this(seedUniquifier.incrementAndGet() + System.nanoTime());
        seedUniquifier.getAndIncrement();
    }

    private RandomExtended(final RandomExtended sourceValue) {
        this.seed = sourceValue.seed;
        this.immutable = false;
        sourceValue.immutable = true;
    }

    /**
     * Initialize with the given seed, so all future numbers will be a purely deterministic function of the
     * initial seed, the numbers passed to absorbEntropy, and when absorbEntropy is called in the sequence
     * of generated numbers.
     *
     * @param seed
     * 		the seed that (along with absorbEntropy calls) determines the generated sequence
     */
    public RandomExtended(long seed) {
        this.seed = (seed ^ multiplier) & mask;
    }

    /**
     * copy of superclasses code without the thread safety. So it gives the same sequence.
     */
    @Override
    protected int next(int bits) {
        seed = (seed * multiplier + addend) & mask;
        return (int) (seed >>> (48 - bits));
    }

    /**
     * return a new copy of this object, containing its current seed. So, for example, if you call nextInt
     * several times on the copy, and also call it several times on the original, they will each return the
     * same sequence of pseudorandom numbers.
     */
    @Override
    public RandomExtended clone() {
        // the XOR and mask are needed to prevent changes to
        // the seed that are made in constructor
        return new RandomExtended((seed ^ multiplier) & mask);
    }

    /**
     * Make future pseudorandom numbers a function of the initial seed and of the entropy passed in here.
     * The effect of a call to absorbEntropy depends on the number of bytes of random numbers that have been
     * generated since the last call to absorbEntropy (or to the constructor, if this is the first call).
     *
     * @param entropy
     * 		a number that influences future outputs.
     */
    public void absorbEntropy(long entropy) {
        seed ^= entropy;
        for (int i = 0; i < 10; i++) {
            next(0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized RandomExtended copy() {
        throwIfImmutable();
        return new RandomExtended(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(seed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        seed = in.readLong();
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
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return this.immutable;
    }
}
