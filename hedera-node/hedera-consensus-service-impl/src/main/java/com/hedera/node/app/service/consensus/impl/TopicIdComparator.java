/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl;

import com.hedera.hapi.node.base.TopicID;
import java.util.Comparator;

/**
 * Comparator used to impose a deterministic ordering on collections of TopicID objects.
 * */
public class TopicIdComparator implements Comparator<TopicID> {
    @Override
    public int compare(TopicID id1, TopicID id2) {
        if (id1 == null && id2 == null) return 0;
        else if (id1 == null) return -1;
        else if (id2 == null) return 1;
        else if (id1.shardNum() != id2.shardNum()) return Long.compare(id1.shardNum(), id2.shardNum());
        else if (id1.realmNum() != id2.realmNum()) return Long.compare(id1.realmNum(), id2.realmNum());
        else return Long.compare(id1.topicNum(), id2.topicNum());
    }
}
