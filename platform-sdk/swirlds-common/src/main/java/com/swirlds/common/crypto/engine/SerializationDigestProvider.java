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

package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.logging.LogMarker;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A {@link CachingOperationProvider} capable of computing hashes for {@link SelfSerializable} objects by hashing the
 * serialized bytes of the object.
 */
public class SerializationDigestProvider
        extends CachingOperationProvider<SelfSerializable, Void, Hash, HashingOutputStream, DigestType> {
    /**
     * {@inheritDoc}
     */
    @Override
    protected HashingOutputStream handleAlgorithmRequired(final DigestType algorithmType)
            throws NoSuchAlgorithmException {
        return new HashingOutputStream(MessageDigest.getInstance(algorithmType.algorithmName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Hash handleItem(
            final HashingOutputStream algorithm,
            final DigestType algorithmType,
            final SelfSerializable item,
            final Void optionalData) {
        algorithm.resetDigest(); // probably not needed, just to be safe
        try (SerializableDataOutputStream out = new SerializableDataOutputStream(algorithm)) {
            out.writeSerializable(item, true);
            out.flush();

            return new Hash(algorithm.getDigest(), algorithmType);
        } catch (IOException ex) {
            throw new CryptographyException(ex, LogMarker.EXCEPTION);
        }
    }
}
