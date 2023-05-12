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

package com.swirlds.common.system.address;

/**
 * A tool for accessing address books stored in the most recent finalized state.
 */
public interface AddressBookManager {

    /**
     * Get an address book from a particular round. Waits until an address book becomes available
     * if it is not currently available. Throws if the requested address book is in the far
     * past and will never become available.
     *
     * FUTURE WORK: if a thread is waiting on this method and we reconnect, this get may never actually complete
     *
     * @param round
     * 		the round of the desired address book
     * @return the address book for the requested round
     * @throws java.util.NoSuchElementException
     * 		if the requested address book is very old and no longer around
     */
    AddressBook get(final long round) throws InterruptedException;

    /**
     * Get the latest address book that is currently available.
     *
     * @return a recent address book
     */
    AddressBook getLatest();
}
