/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.submission;

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/** A wrapper object to improve readability of {@code TransactionPrecheck}. */
@Singleton
public final class StagedPrechecks {
    private final SyntaxPrecheck syntaxPrecheck;
    private final SystemPrecheck systemPrecheck;
    private final SemanticPrecheck semanticPrecheck;
    private final SolvencyPrecheck solvencyPrecheck;
    private final StructuralPrecheck structuralPrecheck;

    @Inject
    public StagedPrechecks(
            final SyntaxPrecheck syntaxPrecheck,
            final SystemPrecheck systemPrecheck,
            final SemanticPrecheck semanticPrecheck,
            final SolvencyPrecheck solvencyPrecheck,
            final StructuralPrecheck structuralPrecheck) {
        this.syntaxPrecheck = syntaxPrecheck;
        this.systemPrecheck = systemPrecheck;
        this.semanticPrecheck = semanticPrecheck;
        this.solvencyPrecheck = solvencyPrecheck;
        this.structuralPrecheck = structuralPrecheck;
    }

    ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        return syntaxPrecheck.validate(txn);
    }

    ResponseCodeEnum systemScreen(final SignedTxnAccessor accessor) {
        return systemPrecheck.screen(accessor);
    }

    ResponseCodeEnum validateSemantics(
            final TxnAccessor accessor,
            final HederaFunctionality requiredFunction,
            final ResponseCodeEnum failureType) {
        return semanticPrecheck.validate(accessor, requiredFunction, failureType);
    }

    TxnValidityAndFeeReq assessSolvencySansSvcFees(final SignedTxnAccessor accessor) {
        return solvencyPrecheck.assessSansSvcFees(accessor);
    }

    TxnValidityAndFeeReq assessSolvencyWithSvcFees(final SignedTxnAccessor accessor) {
        return solvencyPrecheck.assessWithSvcFees(accessor);
    }

    Pair<TxnValidityAndFeeReq, SignedTxnAccessor> assessStructure(final Transaction signedTxn) {
        return structuralPrecheck.assess(signedTxn);
    }
}
