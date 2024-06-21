package com.hedera.services.bdd.spec.utilops;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.spec.utilops.embedded.MutateAccountOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewAccountOp;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.function.Consumer;

/**
 * Contains operations that are usable only with an {@link EmbeddedNetwork}.
 */
public class EmbeddedVerbs {
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
}
