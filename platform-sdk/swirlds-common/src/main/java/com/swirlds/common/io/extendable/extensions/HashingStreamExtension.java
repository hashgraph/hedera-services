/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.extendable.extensions;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

/**
 * An stream extension that can hash bytes that pass through it. It will not automatically hash all the bytes
 * that pass through. In order to start hashing, the {@link #startHashing()} method needs to be called. Once all the
 * required have passed through, {@link #finishHashing()} should be called.
 */
public class HashingStreamExtension extends AbstractStreamExtension {

    /**
     * the digest type used to hash
     */
    private final DigestType digestType;

    /**
     * the object used for hashing
     */
    private final MessageDigest digest;

    /**
     * is hashing enabled or not
     */
    private volatile boolean enabled;

    /**
     * Constructs a stream extension that can hash bytes that pass though it
     *
     * @param digestType
     * 		the type of hash to be calculated
     */
    public HashingStreamExtension(DigestType digestType) {
        this.digestType = digestType;
        try {
            this.digest = MessageDigest.getInstance(digestType.algorithmName(), digestType.provider());
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            // Algorithms and providers that are specified in DigestType are the ones we expect to have at runtime. If
            // they are not available, there really is no way to recover from that, so this might as well be an
            // unchecked exception
            throw new RuntimeException(e);
        }
        this.enabled = false;
    }

    /**
     * Start hashing all the bytes read from this point on until {@link #finishHashing()} is called.
     */
    public void startHashing() {
        enabled = true;
    }

    /**
     * Return the hash of the bytes that were read since {@link #startHashing()} was called. This will stop hashing
     * any further bytes until {@link #startHashing()} is called again.
     *
     * @return the hash calculated
     */
    public Hash finishHashing() {
        return new Hash(digest.digest(), digestType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newByte(int aByte) {
        if (enabled) {
            digest.update((byte) aByte);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newBytes(byte[] bytes, int offset, int length) {
        if (enabled) {
            digest.update(bytes, offset, length);
        }
    }
}
