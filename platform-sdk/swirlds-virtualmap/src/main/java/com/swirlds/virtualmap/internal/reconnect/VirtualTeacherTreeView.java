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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import java.io.IOException;

public interface VirtualTeacherTreeView extends TeacherTreeView<Long> {

    // For internal nodes, writes node hashes. For root node, additionally writes virtual
    // state metadata (leaf paths)
    void writeNode(final SerializableDataOutputStream out, final long path, final boolean isClean) throws IOException;

    Hash loadHash(final long path);

    // Only used in async teaching pull model, when teacher sends responses in a different thread
    // than receives requests
    void registerRequest(final PullVirtualTreeRequest request);

    boolean hasPendingResponses();

    PullVirtualTreeResponse getNextResponse() throws InterruptedException;
}
