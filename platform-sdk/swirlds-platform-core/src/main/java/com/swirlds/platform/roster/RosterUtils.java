/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.roster;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * A utility class to help use Rooster and RosterEntry instances.
 */
public class RosterUtils {

    /**
     * Prevents instantiation of this utility class.
     */
    private RosterUtils() {}

    /**
     * Formats a "node name" for a given node id, e.g. "node1" for nodeId == 0.
     * This name can be used for logging purposes, or to support code that
     * uses strings to identify nodes.
     *
     * @param nodeId a node id
     * @return a "node name"
     */
    @NonNull
    public static String formatNodeName(final long nodeId) {
        return "node" + (nodeId + 1);
    }

    /**
     * Hashes the given {@link Roster} object.
     *
     * @param roster the roster to hash
     * @return the hash of the roster
     */
    @NonNull
    public static Hash hashOf(@NonNull final Roster roster) {
        Objects.requireNonNull(roster);
        final HashBuilder hashBuilder;
        try {
            hashBuilder = new HashBuilder(MessageDigest.getInstance(DigestType.SHA_384.algorithmName()));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        hashBuilder.reset();
        roster.rosters().forEach(entry -> {
            hashBuilder
                    .update(entry.nodeId())
                    .update(entry.weight())
                    .update(entry.gossipCaCertificate().toByteArray())
                    .update(entry.tssEncryptionKey().toByteArray());
            entry.gossipEndpoint().forEach(endpoint -> {
                final byte[] bytes = ServiceEndpoint.PROTOBUF.toBytes(endpoint).toByteArray();
                hashBuilder.update(bytes.length);
                hashBuilder.update(bytes);
            });
        });
        return hashBuilder.build();
    }
}
