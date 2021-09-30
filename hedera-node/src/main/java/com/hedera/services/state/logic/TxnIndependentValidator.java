package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.BiPredicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;

/**
 * Performs transaction independent validation (that is, checks that rely solely upon the given
 * transaction and no other). This type of validation is a candidate for parallel execution.
 */
@Singleton
public class TxnIndependentValidator {
    private static final Logger logger = LogManager.getLogger(TxnIndependentValidator.class);

    SignatureScreen signatureScreen;
    InHandleActivationHelper activationHelper;
    BiPredicate<JKey, TransactionSignature> validityTest;

    @Inject
    public TxnIndependentValidator(
            SignatureScreen signatureScreen,
            InHandleActivationHelper activationHelper,
            BiPredicate<JKey, TransactionSignature> validityTest
    ) {
        this.signatureScreen = signatureScreen;
        this.activationHelper = activationHelper;
        this.validityTest = validityTest;
    }

    public void accept(PlatformTxnAccessor accessor) {
        // Validation can occur during pre-consensus + consensus. Check that the work has not
        // already been done. Setting of the transaction status will be done during consensus
        // execution.
        if (accessor.getValidationStatus() != null)
            return;

        try {
            final ResponseCodeEnum sigStatus = signatureScreen.applyTo(accessor);

            // Store the signature check results in the accessor so downstream execution operations
            // can either by-pass the check or transition the transaction into an error state.
            accessor.setValidationStatus(sigStatus);

            if (TerminalSigStatuses.TERMINAL_SIG_STATUSES.test(sigStatus))
                return;
            if (!activationHelper.areOtherPartiesActive(accessor, validityTest))
                accessor.setValidationStatus(INVALID_SIGNATURE);

        } catch (Exception e) {
            logger.warn("Unable to verify transaction", e);
            accessor.setValidationStatus(INVALID_TRANSACTION);
        }
    }
}
