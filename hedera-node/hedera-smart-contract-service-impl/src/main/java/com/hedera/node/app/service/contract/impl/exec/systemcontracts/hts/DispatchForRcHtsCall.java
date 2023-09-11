package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;

/**
 * An HTS call that simply dispatches a synthetic transaction body and returns a result that is
 * an encoded {@link com.hedera.hapi.node.base.ResponseCodeEnum}.
 *
 * @param <T> the type of the record builder to expect from the dispatch
 */
public class DispatchForRcHtsCall<T extends SingleTransactionRecordBuilder> extends AbstractHtsCall {
    private final AccountID spenderId;
    private final TransactionBody syntheticBody;
    private final Class<T> recordBuilderType;
    private final VerificationStrategy verificationStrategy;

    /**
     * Convenience constructor that slightly eases construction for the most common case.
     *
     * @param onlyDelegatable whether the call can activate only delegatable contract id keys
     * @param attempt the attempt to translate to a dispatching
     * @param sender the address of the sender for the EVM call
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     */
    public DispatchForRcHtsCall(
            final boolean onlyDelegatable,
            @NonNull final HtsCallAttempt attempt,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType) {
        this(
                attempt.enhancement(),
                attempt.addressIdConverter().convert(asHeadlongAddress(sender.toArrayUnsafe())),
                syntheticBody,
                recordBuilderType,
                attempt.verificationStrategies()
                        .activatingOnlyContractKeysFor(sender, onlyDelegatable, attempt.nativeOperations()));
    }

    /**
     * More general constructor, for cases where perhaps a custom {@link VerificationStrategy} is needed.
     *
     * @param enhancement the enhancement to use
     * @param spenderId the id of the spender
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param verificationStrategy the verification strategy to use
     */
    public <U extends SingleTransactionRecordBuilder> DispatchForRcHtsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID spenderId,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final VerificationStrategy verificationStrategy) {
        super(enhancement);
        this.spenderId = Objects.requireNonNull(spenderId);
        this.syntheticBody = Objects.requireNonNull(syntheticBody);
        this.recordBuilderType = Objects.requireNonNull(recordBuilderType);
        this.verificationStrategy = Objects.requireNonNull(verificationStrategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticBody, verificationStrategy, spenderId, recordBuilderType);
        return completionWith(standardized(recordBuilder.status()));
    }
}
