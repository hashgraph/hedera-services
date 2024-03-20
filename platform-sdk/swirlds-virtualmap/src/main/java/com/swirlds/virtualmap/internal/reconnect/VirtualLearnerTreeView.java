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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import java.io.IOException;

public interface VirtualLearnerTreeView extends LearnerTreeView<Long> {

    void setNodeTraveralOrder(final NodeTraversalOrder traversalOrder);

    // Reads the node from the teacher
    void readNode(final SerializableDataInputStream in, final long path, final boolean isClean) throws IOException;

    void anticipateMesssage();

    void applySendBackpressure() throws InterruptedException;
}
