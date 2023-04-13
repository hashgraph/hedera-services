package com.hedera.node.app.integration.infra;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.TransactionHandlers;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.node.app.workflows.dispatcher.TransactionDispatcher.TYPE_NOT_SUPPORTED;

@Singleton
public class ReplayFacilityTransactionDispatcher extends TransactionDispatcher {
    @Inject
    public ReplayFacilityTransactionDispatcher(
            @NonNull HandleContext handleContext,
            @NonNull TransactionHandlers handlers,
            @NonNull HederaAccountNumbers accountNumbers,
            @NonNull GlobalDynamicProperties dynamicProperties) {
        super(handleContext, handlers, accountNumbers, dynamicProperties);
    }

    @Override
    public void dispatchHandle(
            final @NonNull HederaFunctionality function,
            final @NonNull TransactionBody txn,
            final @NonNull WritableStoreFactory writableStoreFactory) {
        try {
            super.dispatchHandle(function, txn, writableStoreFactory);
        } catch (IllegalArgumentException e) {
            if (TYPE_NOT_SUPPORTED.equals(e.getMessage())) {
                System.out.println("Skipping unsupported transaction type " + function);
            } else {
                throw e;
            }
        }
    }
}
