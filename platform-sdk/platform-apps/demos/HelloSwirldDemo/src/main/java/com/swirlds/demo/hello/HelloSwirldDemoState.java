/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.hello;
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.transaction.Transaction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * This holds the current state of the swirld. For this simple "hello swirld" code, each transaction is just
 * a string, and the state is just a list of the strings in all the transactions handled so far, in the
 * order that they were handled.
 */
public class HelloSwirldDemoState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {

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

    private static final long CLASS_ID = 0xe56b0f87f257d092L;

    private static final int DEFAULT_MAX_ARRAY_SIZE = 1024 * 8;
    private static final int DEFAULT_MAX_STRING_SIZE = 128;

    /**
     * The shared state is just a list of the strings in all transactions, listed in the order received
     * here, which will eventually be the consensus order of the community.
     */
    private List<String> strings = new ArrayList<>();

    /** @return all the strings received so far from the network */
    public synchronized List<String> getStrings() {
        return strings;
    }

    /** @return all the strings received so far from the network, concatenated into one */
    public synchronized String getReceived() {
        return strings.toString();
    }

    /** @return the same as getReceived, so it returns the entire shared state as a single string */
    public synchronized String toString() {
        return strings.toString();
    }

    // ///////////////////////////////////////////////////////////////////

    public HelloSwirldDemoState() {}

    private HelloSwirldDemoState(final HelloSwirldDemoState sourceState) {
        super(sourceState);
        this.strings = new ArrayList<>(sourceState.strings);
    }

    @Override
    public synchronized void handleConsensusRound(final Round round, final PlatformStateModifier platformState) {
        throwIfImmutable();
        round.forEachTransaction(this::handleTransaction);
    }

    @Override
    public synchronized HelloSwirldDemoState copy() {
        throwIfImmutable();
        return new HelloSwirldDemoState(this);
    }

    private void handleTransaction(final Transaction transaction) {
        if (transaction.isSystem()) {
            return;
        }
        strings.add(new String(transaction.getApplicationTransaction().toByteArray(), StandardCharsets.UTF_8));
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeStringList(strings);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        strings = in.readStringList(DEFAULT_MAX_ARRAY_SIZE, DEFAULT_MAX_STRING_SIZE);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }
}
