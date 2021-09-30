package com.hedera.services.disruptor;

import com.hedera.services.sigs.SignatureExpander;
import com.hedera.services.state.logic.TxnIndependentValidator;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Custom factory for ValidationHandler instances. Would like to use Dagger assisted injection but
 * difficult to workaround generic array instantiation for list of validation actions (which are
 * duck typed to {@code Consumer<PlatformTxnAccessor>}. The factory isn't too verbose so this approach
 * is a decent compromise.
 */
@Singleton
public class ValidationHandlerFactory {
    SignatureExpander expander;
    TxnIndependentValidator validator;

    @Inject
    public ValidationHandlerFactory(SignatureExpander expander, TxnIndependentValidator validator) {
        this.expander = expander;
        this.validator = validator;
    }

    public ValidationHandler createForPreConsensus(long id, int numHandlers, boolean isLastHandler) {
        return new ValidationHandler(id, numHandlers, isLastHandler, expander::accept, validator::accept);
    }

    public ValidationHandler createForConsensus(long id, int numHandlers, boolean isLastHandler) {
        return new ValidationHandler(id, numHandlers, isLastHandler, validator::accept);
    }
}
