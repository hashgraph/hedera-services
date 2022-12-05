/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.ingest;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.txns.TransitionLogicLookup;
import com.hedera.node.app.service.mono.utils.accessors.TokenWipeAccessor;
import com.hedera.node.app.workflows.common.InsufficientBalanceException;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@code IngestChecker} contains checks that are specific to the ingest workflow */
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
            @NonNull final TransitionLogicLookup transitionLogic,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        this.transitionLogic = requireNonNull(transitionLogic);
        this.dynamicProperties = requireNonNull(dynamicProperties);
    }

    /**
     * Checks a transaction for semantic errors
     *
     * @param txBody the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if a semantic error was discovered. The contained {@code
     *     responseCode} provides the error reason.
     */
    public void checkTransactionSemantics(
            @NonNull final TransactionBody txBody, @NonNull final HederaFunctionality functionality)
            throws PreCheckException {
        final ResponseCodeEnum errorCode;
        if (functionality == TokenAccountWipe) {
            // TODO: Not really sure why TokenAccountWipe gets special treatment
            errorCode =
                    TokenWipeAccessor.validateSyntax(
                            txBody.getTokenWipe(),
                            dynamicProperties.areNftsEnabled(),
                            dynamicProperties.maxBatchSizeWipe());
        } else {
            final var logic =
                    transitionLogic
                            .lookupFor(functionality, txBody)
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
     * @param payer the {@code Account} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if an error is found while checking the signature. The contained
     *     {@code responseCode} provides the error reason.
     */
    public void checkPayerSignature(
            @NonNull final TransactionBody txBody,
            @NonNull final SignatureMap signatureMap,
            @NonNull final Object /* Account */ payer)
            throws PreCheckException {
        LOG.warn("IngestChecker.checkPayerSignature() has not been implemented yet");
        // TODO: Implement once signature check is implemented
    }

    /**
     * Checks the solvency of the payer
     *
     * @param txBody the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @param payer the {@code Account} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws InsufficientBalanceException if the balance is sufficient
     */
    public void checkSolvency(
            @NonNull final TransactionBody txBody,
            @NonNull final HederaFunctionality functionality,
            @NonNull final Object /* Account */ payer)
            throws InsufficientBalanceException {
        LOG.warn("IngestChecker.checkSolvency() has not been implemented yet");
        // TODO: Implement once fee calculation is implemented
    }
}
