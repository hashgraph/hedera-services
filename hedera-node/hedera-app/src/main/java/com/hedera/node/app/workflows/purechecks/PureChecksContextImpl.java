package com.hedera.node.app.workflows.purechecks;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class PureChecksContextImpl implements PureChecksContext {
    /**
     * The transaction body.
     */
    private final TransactionBody txn;

    /**
     * Configuration to be used during pre-handle
     */
    private final Configuration configuration;
    private final TransactionDispatcher dispatcher;
    private final TransactionChecker transactionChecker;


    /**
     * Create a new instance of {@link PureChecksContextImpl}.
     * @throws PreCheckException if the payer account does not exist
     */
    public PureChecksContextImpl(
            @NonNull final TransactionBody txn,
            @NonNull final Configuration configuration,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final TransactionChecker transactionChecker)
            throws PreCheckException {
        this.txn = requireNonNull(txn, "txn must not be null!");
        this.configuration = requireNonNull(configuration, "configuration must not be null!");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null!");
        this.transactionChecker = requireNonNull(transactionChecker, "transactionChecker must not be null!");
    }


    @NonNull
    @Override
    public TransactionBody body() {
        return txn;
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @NonNull
    @Override
    public void pureChecks(@NonNull TransactionBody body) throws PreCheckException {
        final var pureChecksContext = new PureChecksContextImpl(
                body, configuration, dispatcher, transactionChecker);
        dispatcher.dispatchPureChecks(pureChecksContext);
    }

    @Nullable
    @Override
    public TransactionBody bodyFromTransaction(@NonNull Transaction tx) throws PreCheckException {
        final var transactionInfo = transactionChecker.check(tx, null);
        return transactionInfo.txBody();
    }
}
