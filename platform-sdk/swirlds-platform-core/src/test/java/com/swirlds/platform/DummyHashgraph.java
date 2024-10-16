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

package com.swirlds.platform;

import com.swirlds.common.AddressBook;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Random;

public class DummyHashgraph {

    public int eventIntakeQueueSize;
    public HashMap<NodeId, Boolean> isInCriticalQuorum;
    public NodeId selfId;
    public long numUserTransEvents;
    public long lastRoundReceivedAllTransCons;
    public AddressBook addressBook;

    DummyHashgraph(@NonNull final Random random, final int selfIndex) {
        eventIntakeQueueSize = 0;
        isInCriticalQuorum = new HashMap<>();
        numUserTransEvents = 0;
        lastRoundReceivedAllTransCons = 0;
        addressBook = RandomAddressBookBuilder.create(random).withSize(41).build();
        this.selfId = addressBook.getNodeId(selfIndex);
    }

    int getEventIntakeQueueSize() {
        return eventIntakeQueueSize;
    }

    AddressBook getAddressBook() {
        return addressBook;
    }
}
