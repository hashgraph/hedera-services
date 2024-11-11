/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates associate and dissociate calls to the HTS system contract. There are no special cases for
 * these calls, so the returned {@link Call} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
@Singleton
public class AssociationsTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /**
     * Selector for associate() method.
     */
    public static final Function HRC_ASSOCIATE = new Function("associate()", ReturnTypes.INT);
    /**
     * Selector for associateToken(address,address) method.
     */
    public static final Function ASSOCIATE_ONE = new Function("associateToken(address,address)", ReturnTypes.INT_64);
    /**
     * Selector for dissociateToken(address,address) method.
     */
    public static final Function DISSOCIATE_ONE = new Function("dissociateToken(address,address)", ReturnTypes.INT_64);
    /**
     * Selector for dissociate() method.
     */
    public static final Function HRC_DISSOCIATE = new Function("dissociate()", ReturnTypes.INT);
    /**
     * Selector for associateTokens(address,address[]) method.
     */
    public static final Function ASSOCIATE_MANY =
            new Function("associateTokens(address,address[])", ReturnTypes.INT_64);
    /**
     * Selector for dissociateTokens(address,address[]) method.
     */
    public static final Function DISSOCIATE_MANY =
            new Function("dissociateTokens(address,address[])", ReturnTypes.INT_64);

    private final AssociationsDecoder decoder;

    /**
     * Constructor for injection.
     * @param decoder
     */
    @Inject
    public AssociationsTranslator(@NonNull final AssociationsDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isTokenRedirect()
                ? attempt.isSelector(HRC_ASSOCIATE, HRC_DISSOCIATE)
                : attempt.isSelector(ASSOCIATE_ONE, ASSOCIATE_MANY, DISSOCIATE_ONE, DISSOCIATE_MANY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(
                attempt,
                attempt.isSelector(HRC_ASSOCIATE, HRC_DISSOCIATE) ? bodyForHrc(attempt) : bodyForClassic(attempt),
                AssociationsTranslator::gasRequirement);
    }

    /**
     * @param body                          the transaction body to be dispatched
     * @param systemContractGasCalculator   the gas calculator for the system contract
     * @param enhancement                   the enhancement to use
     * @param payerId                       the payer of the transaction
     * @return the required gas
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.ASSOCIATE, payerId);
    }

    private TransactionBody bodyForHrc(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(HRC_ASSOCIATE)) {
            return decoder.decodeHrcAssociate(attempt);
        } else {
            return decoder.decodeHrcDissociate(attempt);
        }
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(ASSOCIATE_ONE)) {
            return decoder.decodeAssociateOne(attempt);
        } else if (attempt.isSelector(ASSOCIATE_MANY)) {
            return decoder.decodeAssociateMany(attempt);
        } else if (attempt.isSelector(DISSOCIATE_ONE)) {
            return decoder.decodeDissociateOne(attempt);
        } else {
            return decoder.decodeDissociateMany(attempt);
        }
    }
}
