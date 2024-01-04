/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;

/**
 * Identification information about a platform.
 */
public interface PlatformIdentity {

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    AddressBook getAddressBook();

    /**
     * Get the ID of current node
     *
     * @return node ID
     */
    NodeId getSelfId();

    /**
     * Get the address of the current node.
     *
     * @return this node's address
     */
    default Address getSelfAddress() {
        return getAddressBook().getAddress(getSelfId());
    }
}
