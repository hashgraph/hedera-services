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

package com.hedera.node.app.workflows.handle.stack.savepoints;

import com.hedera.node.app.state.WrappedState;
import com.hedera.node.app.workflows.handle.stack.Savepoint;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents any save point that is not first in the stack. When the save point is committed, the records are
 * flushed into the following list of the parent sink. So this sink has a total capacity equal to the parent's
 * following capacity.
 */
public class FollowingSavepoint extends AbstractSavepoint {

    public FollowingSavepoint(@NonNull WrappedState state, @NonNull Savepoint parent) {
        super(state, parent, parent.followingCapacity());
    }

    @Override
    void commitBuilders() {
        flushFollowing(parentSink);
    }
}
