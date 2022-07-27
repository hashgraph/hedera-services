/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context;

import static com.hedera.services.utils.EntityIdUtils.parseAccount;

import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.system.address.AddressBook;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Summarizes useful information about the nodes in the {@link AddressBook} from the Platform. In
 * the future, there may be events that require re-reading the book; but at present nodes may treat
 * the initializing book as static.
 */
@Singleton
public class NodeInfo {
    private static final Logger log = LogManager.getLogger(NodeInfo.class);

    private boolean bookIsRead = false;

    private int numberOfNodes;
    private boolean[] isZeroStake;
    private AccountID[] accounts;
    private EntityNum[] accountKeys;

    private final long selfId;
    private final Supplier<AddressBook> book;

    @Inject
    public NodeInfo(long selfId, Supplier<AddressBook> book) {
        this.book = book;
        this.selfId = selfId;
    }

    /**
     * For a staked node, validates presence of a self-account in the address book.
     *
     * @throws IllegalStateException if the node is staked but has no account
     */
    public void validateSelfAccountIfStaked() {
        if (!isSelfZeroStake() && !hasSelfAccount()) {
            throw new IllegalStateException("Node is not zero-stake, but has no known account");
        }
    }

    /**
     * Returns true if the node in the address book at the given index (casting the argument as an
     * {@code int}) has zero stake.
     *
     * @param nodeId the id of interest
     * @return whether or not the node of interest has zero stake.
     * @throws IllegalArgumentException if the {@code nodeId} cast to an {@code int} is not a usable
     *     index
     */
    public boolean isZeroStake(long nodeId) {
        if (!bookIsRead) {
            readBook();
        }

        final int index = (int) nodeId;
        if (isIndexOutOfBounds(index)) {
            throw new IllegalArgumentException(
                    "The address book does not have a node at index " + index);
        }
        return isZeroStake[index];
    }

    /**
     * Convenience method to check if this node is zero-stake.
     *
     * @return whether or not this node has zero stake.
     */
    public boolean isSelfZeroStake() {
        return isZeroStake(selfId);
    }

    /**
     * Returns the account parsed from the address book memo corresponding to the given node id.
     *
     * @param nodeId the id of interest
     * @return the account parsed from the address book memo corresponding to the given node id.
     * @throws IllegalArgumentException if the book did not contain the id, or was missing an
     *     account for the id
     */
    public AccountID accountOf(long nodeId) {
        final int index = validatedIndexFor(nodeId);

        return accounts[index];
    }

    public EntityNum accountKeyOf(long nodeId) {
        final int index = validatedIndexFor(nodeId);

        return accountKeys[index];
    }

    private int validatedIndexFor(long nodeId) {
        if (!bookIsRead) {
            readBook();
        }

        final int index = (int) nodeId;
        if (isIndexOutOfBounds(index)) {
            throw new IllegalArgumentException(
                    "No node with id " + nodeId + " was in the address book!");
        }
        if (accounts[index] == null) {
            throw new IllegalArgumentException(
                    "The address book did not have an account for node id " + nodeId + "!");
        }
        return index;
    }

    public boolean isValidId(long nodeId) {
        if (!bookIsRead) {
            readBook();
        }
        final int index = (int) nodeId;
        return !isIndexOutOfBounds(index) && accounts[index] != null;
    }

    /**
     * Convenience method to check if this node has an account in the address book.
     *
     * @return whether or not this node has an account in the address book.
     */
    public boolean hasSelfAccount() {
        try {
            accountOf(selfId);
            return true;
        } catch (IllegalArgumentException ignore) {
            return false;
        }
    }

    /**
     * Convenience method to get this node's account from the address book.
     *
     * @return this node's account from the address book.
     * @throws IllegalArgumentException if the node did not have an account
     */
    public AccountID selfAccount() {
        return accountOf(selfId);
    }

    private boolean isIndexOutOfBounds(int index) {
        return index < 0 || index >= numberOfNodes;
    }

    void readBook() {
        final var staticBook = book.get();

        numberOfNodes = staticBook.getSize();
        accounts = new AccountID[numberOfNodes];
        accountKeys = new EntityNum[numberOfNodes];
        isZeroStake = new boolean[numberOfNodes];

        for (int i = 0; i < numberOfNodes; i++) {
            final var address = staticBook.getAddress(i);
            isZeroStake[i] = address.getStake() <= 0;
            try {
                accounts[i] = parseAccount(address.getMemo());
                accountKeys[i] = EntityNum.fromAccountId(accounts[i]);
            } catch (IllegalArgumentException e) {
                if (!isZeroStake[i]) {
                    log.error(
                            "Cannot parse account for staked node id {}, potentially fatal!", i, e);
                }
            }
        }

        bookIsRead = true;
    }
}
