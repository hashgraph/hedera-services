/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.swirlds.platform.bls.crypto;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Iterator that goes through the valid share ids of an address book */
public class ShareIdIterator implements Iterator<ShareId> {
    /** The total number of shares of the address book */
    private final int addressBookTotalShares;

    /** The previous shareId that was returned */
    private ShareId previousShareId;

    /**
     * Constructor
     *
     * @param addressBookTotalShares the total share count of the address book
     */
    public ShareIdIterator(final int addressBookTotalShares) {
        this.addressBookTotalShares = addressBookTotalShares;

        // start previous at 0, since 0 isn't a valid share id to iterate over
        this.previousShareId = new ShareId(0);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() {
        return previousShareId.id() < addressBookTotalShares;
    }

    /** {@inheritDoc} */
    @Override
    public ShareId next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        final ShareId shareId = new ShareId(previousShareId.id() + 1);
        previousShareId = shareId;

        return shareId;
    }
}
