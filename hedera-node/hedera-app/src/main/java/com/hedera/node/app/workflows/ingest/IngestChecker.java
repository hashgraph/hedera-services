package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.workflows.common.InsufficientBalanceException;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.accessors.TokenWipeAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

/**
 * The {@code IngestChecker} contains checks that are specific to the ingest workflow
 */
public class IngestChecker {

    private static final Logger LOG = LoggerFactory.getLogger(IngestChecker.class);

    private final TransitionLogicLookup transitionLogic;
    private final GlobalDynamicProperties dynamicProperties;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param transitionLogic a {@link TransitionLogicLookup}
     * @param dynamicProperties the {@link GlobalDynamicProperties}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public IngestChecker(
            @Nonnull final TransitionLogicLookup transitionLogic,
            @Nonnull final GlobalDynamicProperties dynamicProperties) {
        this.transitionLogic = requireNonNull(transitionLogic);
        this.dynamicProperties = requireNonNull(dynamicProperties);
    }

    /**
     * Checks a transaction for semantic errors
     *
     * @param txBody the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if a semantic error was discovered. The contained {@code responseCode} provides the error reason.
     */
    public void checkTransactionSemantic(
            @Nonnull final TransactionBody txBody,
            @Nonnull final HederaFunctionality functionality) throws PreCheckException {
        final ResponseCodeEnum errorCode;
        if (functionality == TokenAccountWipe) {
            // TODO: Not really sure why TokenAccountWipe gets special treatment
            errorCode = TokenWipeAccessor.validateSyntax(txBody.getTokenWipe(), dynamicProperties.areNftsEnabled(), dynamicProperties.maxBatchSizeWipe());
        } else {
            final var logic = transitionLogic.lookupFor(functionality, txBody)
                    .orElseThrow(() -> new PreCheckException(NOT_SUPPORTED));
            errorCode = logic.semanticCheck().apply(txBody);
        }
        if (errorCode != OK) {
            throw new PreCheckException(errorCode);
        }
    }

    /**
     * Checks the signature of the payer. <em>Currently not implemented.</em>
     *
     * @param txBody the {@link TransactionBody}
     * @param signatureMap the {@link SignatureMap} contained in the transaction
     * @param payer the {@link HederaAccount} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if an error is found while checking the signature. The contained {@code responseCode} provides the error reason.
     */
    public void checkPayerSignature(
            @Nonnull final TransactionBody txBody,
            @Nonnull final SignatureMap signatureMap,
            @Nonnull final HederaAccount payer)  throws PreCheckException {
        LOG.warn("IngestChecker.checkPayerSignature() has not been implemented yet");
        // TODO: Implement once signature check is implemented
    }

    /**
     * Checks the solvency of the payer
     *
     * @param txBody the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @param payer the {@link HederaAccount} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws InsufficientBalanceException if the balance is sufficient
     */
    public void checkSolvency(
            @Nonnull final TransactionBody txBody,
            @Nonnull final HederaFunctionality functionality,
            @Nonnull final HederaAccount payer) throws InsufficientBalanceException {
        LOG.warn("IngestChecker.checkSolvency() has not been implemented yet");
        // TODO: Implement once fee calculation is implemented
    }

}
