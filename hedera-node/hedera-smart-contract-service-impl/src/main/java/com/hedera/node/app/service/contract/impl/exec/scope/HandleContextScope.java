package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.WritableContractsStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;

/**
 * A {@link Scope} implementation based on a {@link HandleContext}.
 */
@TransactionScope
public class HandleContextScope implements Scope {
    private final HandleContext context;

    @Inject
    public HandleContextScope(@NonNull final HandleContext context) {
        this.context = context;
    }

    @Override
    public WritableContractsStore writableContractStore() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Dispatch dispatch() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Fees fees() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public long payerAccountNumber() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Scope begin() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void commit() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void revert() {
        throw new AssertionError("Not implemented");
    }
}
