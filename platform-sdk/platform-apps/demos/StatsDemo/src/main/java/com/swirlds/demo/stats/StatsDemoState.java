/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stats;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

/**
 * This demo collects statistics on the running of the network and consensus systems. It writes them to the
 * screen, and also saves them to disk in a comma separated value (.csv) file. Optionally, it can also put a
 * sequence number into each transaction, and check if any are lost, or delayed too long. Each transaction
 * is 100 random bytes. So StatsDemoState.handleTransaction doesn't actually do anything, other than the
 * optional sequence number check.
 */
public class StatsDemoState extends MerkleStateRoot {

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

    private static final long CLASS_ID = 0xc550a1cd94e91ca3L;

    public StatsDemoState(
            @NonNull final MerkleStateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(lifecycles, versionFactory);
    }

    private StatsDemoState(final StatsDemoState sourceState) {
        super(sourceState);
    }

    @Override
    public void handleConsensusRound(final Round round, final PlatformStateModifier platformState) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized StatsDemoState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new StatsDemoState(this);
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
}
