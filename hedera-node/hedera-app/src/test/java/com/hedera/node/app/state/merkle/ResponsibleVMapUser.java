/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle;

import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;

public abstract class ResponsibleVMapUser {
    private static final AtomicInteger numReleased = new AtomicInteger();
    private final List<MerkleHederaState> statesToRelease = new ArrayList<>();
    private final List<VirtualMap<?, ?>> mapsToRelease = new ArrayList<>();

    protected <K extends VirtualKey<? super K>, V extends VirtualValue> VirtualMap<K, V> trackedMap(
            @Nullable final VirtualMap<K, V> map) {
        if (map != null) {
            mapsToRelease.add(map);
        }
        return map;
    }

    protected MerkleHederaState tracked(@Nullable final MerkleHederaState state) {
        if (state != null) {
            statesToRelease.add(state);
        }

        return state;
    }

    @AfterEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releaseTracked() throws IOException {
        for (final var map : mapsToRelease) {
            release(VirtualMapLike.from((VirtualMap) map));
        }
        for (final var state : statesToRelease) {
            release(state);
        }
    }

    private void release(@NonNull final MerkleHederaState state) throws IOException {
        //        release(state.storage());
        //        release(state.contractStorage());
        //
        //        final var accounts = state.accounts();
        //        if (accounts != null && accounts.areOnDisk()) {
        //            release(accounts.getOnDiskAccounts());
        //        }
        //        final var tokenRels = state.tokenAssociations();
        //        if (tokenRels != null && tokenRels.areOnDisk()) {
        //            release(tokenRels.getOnDiskRels());
        //        }
        //        final var nfts = state.uniqueTokens();
        //        if (nfts != null && nfts.isVirtual()) {
        //            release(nfts.getOnDiskNfts());
        //        }
    }

    private void release(@Nullable final VirtualMapLike<?, ?> map) throws IOException {
        if (map != null) {
            if (map.toString().contains("Mock")) {
                System.out.println("Skipping mock " + map);
                return;
            }
            map.release();
            System.out.println("Released #" + numReleased.incrementAndGet());
            try {
                map.getDataSource().close();
            } catch (final NullPointerException ignore) {
                // A few tests use the VirtualMap default constructor, which doesn't initialize root
            }
        }
    }
}
