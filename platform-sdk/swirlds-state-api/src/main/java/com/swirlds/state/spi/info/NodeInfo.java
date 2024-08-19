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

package com.swirlds.state.spi.info;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * Summarizes useful information about the nodes in the AddressBook from the Platform. In
 * the future, there may be events that require re-reading the book; but at present nodes may treat
 * the initializing book as static.
 */
public interface NodeInfo {

    /**
     * Convenience method to check if this node is zero-stake.
     *
     * @return whether this node has zero stake.
     */
    default boolean zeroStake() {
        return stake() == 0;
    }

    /**
     * Gets the node ID. This is a separate identifier from the node's account. This IS NOT IN ANY WAY related to the
     * node AccountID.
     *
     * <p>FUTURE: Should we expose NodeId from the platform? It would make this really hard to misuse as the node
     * accountID, whereas as a long, it could be.
     *
     * @return The node ID.
     */
    long nodeId();

    /**
     * Returns the account ID corresponding with this node.
     *
     * @return the account ID of the node.
     * @throws IllegalStateException if the book did not contain the id, or was missing an account for the id
     */
    AccountID accountId();

    /**
     * Convenience method to get the memo of this node's account which is in the address book.
     *
     * @return this node's account memo
     */
    String memo();

    /**
     * The host name of this node, as known by the external world. This is an IP address.
     *
     * @return The host name (IP Address) of this node
     */
    String externalHostName();

    /**
     * The port the node is listening on.
     * @return the port. Non-negative.
     */
    int externalPort();

    /**
     * The public key of this node, as a hex-encoded string.
     * @return the public key
     */
    String hexEncodedPublicKey();

    /**
     * The stake weight of this node.
     * @return the stake weight
     */
    long stake();

    /**
     * The signing x509 certificate bytes of the member
     * @return the signing x509 certificate bytes
     */
    Bytes sigCertBytes();

    /**
     * The host name of this node, as known by the internal world. This is an IP address.
     *
     * @return The host name (IP Address) of this node
     */
    String internalHostName();

    /**
     * The internal port the node is listening on.
     * @return the port. Non-negative.
     */
    int internalPort();

    /**
     * The "self name" of this node.
     * @return the self name
     */
    String selfName();
}
