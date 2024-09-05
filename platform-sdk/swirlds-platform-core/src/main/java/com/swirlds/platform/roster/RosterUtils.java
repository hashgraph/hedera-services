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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class RosterUtils {
    private RosterUtils() {}

    /**
     * Hashes the given {@link Roster} object.
     *
     * @param roster the roster to hash
     * @return the hash of the roster
     */
    public static byte[] hashOf(@NonNull final Roster roster) {
        Objects.requireNonNull(roster);
        final Hasher hasher = Hashing.sha256().newHasher();
        roster.rosters().forEach(entry -> {
            hasher.putLong(entry.nodeId());
            hasher.putLong(entry.weight());
            hasher.putBytes(entry.gossipCaCertificate().toByteArray());
            hasher.putBytes(entry.tssEncryptionKey().toByteArray());
            entry.gossipEndpoint().forEach(endpoint -> {
                final byte[] bytes =
                        ServiceEndpoint.PROTOBUF.toBytes(endpoint).toByteArray();
                hasher.putInt(bytes.length);
                hasher.putBytes(bytes);
            });
        });
        return hasher.hash().asBytes();
    }
}
