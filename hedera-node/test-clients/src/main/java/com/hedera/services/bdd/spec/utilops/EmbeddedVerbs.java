/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.spec.utilops.embedded.MutateAccountOp;
import com.hedera.services.bdd.spec.utilops.embedded.MutateNodeOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewAccountOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewNodeOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewPendingAirdropOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Contains operations that are usable only with an {@link EmbeddedNetwork}.
 */
public final class EmbeddedVerbs {
    private EmbeddedVerbs() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an operation that allows the test author to directly mutate an account.
     *
     * @param name the name of the account to mutate
     * @param mutation the mutation to apply to the account
     * @return the operation that will mutate the account
     */
    public static MutateAccountOp mutateAccount(
            @NonNull final String name, @NonNull final Consumer<Account.Builder> mutation) {
        return new MutateAccountOp(name, mutation);
    }

    /**
     * Returns an operation that allows the test author to directly mutate an account.
     *
     * @param name the name of the account to mutate
     * @param observer the mutation to apply to the account
     * @return the operation that will mutate the account
     */
    public static ViewAccountOp viewAccount(@NonNull final String name, @NonNull final Consumer<Account> observer) {
        return new ViewAccountOp(name, observer);
    }

    /**
     * Returns an operation that allows the test author to directly mutate an account.
     *
     * @param name the name of the account to mutate
     * @param mutation the mutation to apply to the account
     * @return the operation that will mutate the account
     */
    public static MutateNodeOp mutateNode(@NonNull final String name, @NonNull final Consumer<Node.Builder> mutation) {
        return new MutateNodeOp(name, mutation);
    }

    /**
     * Returns an operation that allows the test author to directly mutate a node.
     *
     * @param name the name of the node to mutate
     * @param observer the mutation to apply to the node
     * @return the operation that will mutate the node
     */
    public static ViewNodeOp viewNode(@NonNull final String name, @NonNull final Consumer<Node> observer) {
        return new ViewNodeOp(name, observer);
    }

    /***
     * `ViewPendingAirdropOp` is an operation that allows the test author to view the pending airdrop of an account.
     * @param tokenName
     * @param senderName
     * @param receiverName
     * @param observer
     * @return
     */
    public static ViewPendingAirdropOp viewAccountPendingAirdrop(
            @NonNull final String tokenName,
            @NonNull final String senderName,
            @NonNull final String receiverName,
            @NonNull final Consumer<AccountPendingAirdrop> observer) {
        return new ViewPendingAirdropOp(tokenName, senderName, receiverName, observer);
    }
}
