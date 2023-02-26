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

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * When the teacher submits a query of "do you have this node", the learner replies with a QueryResponse.
 */
public class QueryResponse implements SelfSerializable {

    private static final long CLASS_ID = 0x7CBF61E166C6E5F7L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    protected boolean learnerHasTheNode;

    public QueryResponse() {}

    /**
     * Construct a query response.
     *
     * @param learnerHasTheNode
     * 		true if this node (the learner) has the given response
     */
    public QueryResponse(boolean learnerHasTheNode) {
        this.learnerHasTheNode = learnerHasTheNode;
    }

    /**
     * Does the learner have the node in question?
     *
     * @return true if the learner has the node
     */
    public boolean doesLearnerHaveTheNode() {
        return learnerHasTheNode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(learnerHasTheNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        learnerHasTheNode = in.readBoolean();
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
        return ClassVersion.ORIGINAL;
    }
}
