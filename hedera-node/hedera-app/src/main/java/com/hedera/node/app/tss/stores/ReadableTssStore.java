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

package com.hedera.node.app.tss.stores;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.tss.TssMessageTransactionBody;
import com.hedera.hapi.node.tss.TssVoteTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface ReadableTssStore {
    TssMessageTransactionBody getMessage(@NonNull TssMessageMapKey TssMessageMapKey);

    boolean exists(@NonNull TssMessageMapKey TssMessageMapKey);

    TssVoteTransactionBody getVote(@NonNull TssVoteMapKey TssMessageMapKey);

    boolean exists(@NonNull TssVoteMapKey TssMessageMapKey);

    long size();
}
