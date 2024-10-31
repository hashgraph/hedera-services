/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.crypto.internal;

import static com.swirlds.logging.legacy.LogMarker.TESTING_EXCEPTIONS;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.crypto.engine.CachingOperationProvider;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildHashException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link CachingOperationProvider} implementation that is capable of computing a hash for a supplied {@link
 * MerkleInternal} instance.
 */
public class MerkleInternalDigestProvider
        extends CachingOperationProvider<MerkleInternal, List<Hash>, Hash, HashBuilder, DigestType> {

    private static final Logger logger = LogManager.getLogger(MerkleInternalDigestProvider.class);

    /**
     * {@inheritDoc}
     */
    @Override
    protected HashBuilder handleAlgorithmRequired(DigestType algorithmType) throws NoSuchAlgorithmException {
        return new HashBuilder(MessageDigest.getInstance(algorithmType.algorithmName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Hash handleItem(
            final HashBuilder hashBuilder,
            final DigestType algorithmType,
            final MerkleInternal node,
            List<Hash> childHashes) {

        hashBuilder.reset();

        hashBuilder.update(node.getClassId());
        hashBuilder.update(node.getClassVersion());
        for (int index = 0; index < childHashes.size(); index++) {
            final Hash childHash = childHashes.get(index);

            if (childHash == null || childHash.getBytes() == null) {
                final MerkleNode childNode = node.getChild(index);
                final String msg = String.format(
                        "Child has an unexpected null hash "
                                + "[ parentClass = '%s', childClass = '%s', childRoute = %s ]",
                        node.getClass().getName(), childNode.getClass().getName(), childNode.getRoute());

                logger.trace(TESTING_EXCEPTIONS.getMarker(), msg);
                throw new IllegalChildHashException(msg);
            }

            hashBuilder.update(childHash);
        }

        return hashBuilder.build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash compute(final MerkleInternal node, final List<Hash> childHashes, final DigestType algorithmType)
            throws NoSuchAlgorithmException {
        return handleItem(loadAlgorithm(algorithmType), algorithmType, node, childHashes);
    }
}
