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

package com.swirlds.common.crypto;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * An OutputStream which creates a hash of all the bytes that go through it
 */
public class HashingOutputStream extends FilterOutputStream {
    private final MessageDigest md;

    /**
     * A constructor used to create an OutputStream that only does hashing
     *
     * @param md
     * 		the MessageDigest object that does the hashing
     */
    public HashingOutputStream(@NonNull final MessageDigest md) {
        this(md, OutputStream.nullOutputStream());
    }

    /**
     * A constructor used to create an OutputStream that hashes all the bytes that go through it, and also
     * writes them to the wrapped OutputStream
     *
     * @param md
     * 		the MessageDigest object that will hash all bytes of the stream
     * @param out
     * 		the OutputStream where bytes will be sent to after being added to the hash
     */
    public HashingOutputStream(@NonNull final MessageDigest md, @NonNull final OutputStream out) {
        super(out);
        this.md = md;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int b) throws IOException {
        super.write(b);
        md.update((byte) b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        md.update(b, off, len);
    }

    /**
     * Reset the digest used by the stream
     */
    public void resetDigest() {
        md.reset();
    }

    /**
     * Calculates and returns the digest of all the bytes that have been written to this stream. It also resets the
     * digest.
     *
     * @return the digest of all bytes written to the stream
     */
    public byte[] getDigest() {
        return md.digest();
    }
}
