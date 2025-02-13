/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.consensus;

import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.consensus.framework.ConsensusOutput;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;

public interface Intake {
    void addEvent(@NonNull PlatformEvent event);

    void addEvents(@NonNull List<EventImpl> events);

    @NonNull
    LinkedList<ConsensusRound> getConsensusRounds();

    @Nullable
    ConsensusRound getLatestRound();

    void loadSnapshot(@NonNull ConsensusSnapshot snapshot);

    @NonNull
    ConsensusOutput getOutput();

    void reset();
}
