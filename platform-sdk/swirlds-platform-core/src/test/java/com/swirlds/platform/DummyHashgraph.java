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

package com.swirlds.platform;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import java.util.HashMap;

public class DummyHashgraph {

    public int eventIntakeQueueSize;
    public HashMap<Long, Boolean> isInCriticalQuorum;
    public long nodeId;
    public long numUserTransEvents;
    public long lastRoundReceivedAllTransCons;
    public AddressBook addressBook;

    DummyHashgraph() {
        eventIntakeQueueSize = 0;
        isInCriticalQuorum = new HashMap<>();
        nodeId = 0;
        numUserTransEvents = 0;
        lastRoundReceivedAllTransCons = 0;
        addressBook = new RandomAddressBookGenerator().setSize(5).build();
    }

    int getEventIntakeQueueSize() {
        return eventIntakeQueueSize;
    }

    AddressBook getAddressBook() {
        return addressBook;
    }
}
