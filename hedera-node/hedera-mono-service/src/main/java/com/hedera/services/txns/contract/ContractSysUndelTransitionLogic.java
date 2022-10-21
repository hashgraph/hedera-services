/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.contract;

import static com.hedera.services.context.properties.EntityType.CONTRACT;
import static com.hedera.services.context.properties.PropertyNames.ENTITIES_SYSTEM_DELETABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ContractSysUndelTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ContractSysUndelTransitionLogic.class);

    private final boolean supported;
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final LegacySystemUndeleter delegate;
    private final Supplier<AccountStorageAdapter> contracts;

    @Inject
    public ContractSysUndelTransitionLogic(
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final LegacySystemUndeleter delegate,
            final Supplier<AccountStorageAdapter> contracts,
            @CompositeProps final PropertySource properties) {
        this.validator = validator;
        this.txnCtx = txnCtx;
        this.delegate = delegate;
        this.contracts = contracts;
        this.sigImpactHistorian = sigImpactHistorian;
        this.supported = properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE).contains(CONTRACT);
    }

    @FunctionalInterface
    public interface LegacySystemUndeleter {
        TransactionRecord perform(TransactionBody txn, Instant consensusTime);
    }

    @Override
    public void doStateTransition() {
        try {
            var contractSysUndelTxn = txnCtx.accessor().getTxn();

            final var legacyRecord = delegate.perform(contractSysUndelTxn, txnCtx.consensusTime());
            final var status = legacyRecord.getReceipt().getStatus();
            if (status == SUCCESS) {
                final var target = contractSysUndelTxn.getSystemUndelete().getContractID();
                sigImpactHistorian.markEntityChanged(target.getContractNum());
            }
            txnCtx.setStatus(status);
        } catch (Exception e) {
            log.warn("Avoidable exception!", e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return txn -> txn.hasSystemUndelete() && txn.getSystemUndelete().hasContractID();
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody contractSysUndelTxn) {
        if (!supported) {
            return NOT_SUPPORTED;
        }
        var op = contractSysUndelTxn.getSystemUndelete();
        var status = validator.queryableContractStatus(op.getContractID(), contracts.get());
        return (status != INVALID_CONTRACT_ID) ? OK : INVALID_CONTRACT_ID;
    }
}
