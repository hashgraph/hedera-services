/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.address;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.address.AddressBookManager;

/**
 * An address book manager that can be modified.
 */
public interface MutableAddressBookManager extends AddressBookManager {

    /**
     * Set the most recent address book store from a recently copied state.
     *
     * @param mostRecentAddressBook
     * 		the most recent address book
     */
    void setMostRecentAddressBook(final AddressBook mostRecentAddressBook);

    /**
     * Jump forward in time and use a new store as a basis. Called after reconnect completes.
     *
     * @param newStore
     * 		an address book store from later in time as a basis
     */
    void fastForwardToAddressBook(final AddressBookStore newStore);
}
