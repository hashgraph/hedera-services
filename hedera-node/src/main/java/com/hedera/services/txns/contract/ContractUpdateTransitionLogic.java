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

import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.validSentinel;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.utils.EntityIdUtils.unaliased;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.contract.helpers.UpdateCustomizerFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractUpdateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ContractUpdateTransitionLogic.class);

    private final HederaLedger ledger;
    private final AliasManager aliasManager;
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final UpdateCustomizerFactory customizerFactory;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts;
    private final GlobalDynamicProperties properties;
    private final NodeInfo nodeInfo;

    public ContractUpdateTransitionLogic(
            final HederaLedger ledger,
            final AliasManager aliasManager,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final UpdateCustomizerFactory customizerFactory,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts,
            final GlobalDynamicProperties properties,
            final NodeInfo nodeInfo) {
        this.ledger = ledger;
        this.validator = validator;
        this.aliasManager = aliasManager;
        this.txnCtx = txnCtx;
        this.contracts = contracts;
        this.sigImpactHistorian = sigImpactHistorian;
        this.customizerFactory = customizerFactory;
        this.properties = properties;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public void doStateTransition() {
        try {
            final var contractUpdateTxn = txnCtx.accessor().getTxn();
            final var op = contractUpdateTxn.getContractUpdateInstance();
            final var id = unaliased(op.getContractID(), aliasManager);
            final var target = contracts.get().get(id);

            var result = customizerFactory.customizerFor(target, validator, op);
            var contractCustomizer = result.getLeft();
            if (contractCustomizer.isPresent()) {
                final var customizer = contractCustomizer.get();
                if (!properties.areContractAutoAssociationsEnabled()) {
                    customizer.getChanges().remove(MAX_AUTOMATIC_ASSOCIATIONS);
                }
                final var validity = sanityCheckAutoAssociations(id, customizer);
                if (validity != OK) {
                    txnCtx.setStatus(validity);
                    return;
                }

                ledger.customize(id.toGrpcAccountId(), customizer);
                sigImpactHistorian.markEntityChanged(id.longValue());
                if (target.hasAlias()) {
                    sigImpactHistorian.markAliasChanged(target.getAlias());
                }
                txnCtx.setStatus(SUCCESS);
                txnCtx.setTargetedContract(id.toGrpcContractID());
            } else {
                txnCtx.setStatus(result.getRight());
            }
        } catch (Exception e) {
            log.warn("Avoidable exception!", e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private ResponseCodeEnum sanityCheckAutoAssociations(
            final EntityNum target, final HederaAccountCustomizer customizer) {
        final var changes = customizer.getChanges();
        if (changes.containsKey(MAX_AUTOMATIC_ASSOCIATIONS)) {
            final long newMax = (int) changes.get(MAX_AUTOMATIC_ASSOCIATIONS);
            if (newMax < ledger.alreadyUsedAutomaticAssociations(target.toGrpcAccountId())) {
                return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
            }
            if (properties.areTokenAssociationsLimited()
                    && newMax > properties.maxTokensPerAccount()) {
                return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
            }
        }
        return OK;
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasContractUpdateInstance;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody contractUpdateTxn) {
        final var op = contractUpdateTxn.getContractUpdateInstance();

        final var id = unaliased(op.getContractID(), aliasManager);
        var status = validator.queryableContractStatus(id, contracts.get());
        if (status != OK) {
            return status;
        }

        if (op.hasAutoRenewPeriod()) {
            if (op.getAutoRenewPeriod().getSeconds() < 1) {
                return INVALID_RENEWAL_PERIOD;
            }
            if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
                return AUTORENEW_DURATION_NOT_IN_RANGE;
            }
        }

        final var newMemoIfAny =
                op.hasMemoWrapper() ? op.getMemoWrapper().getValue() : op.getMemo();
        if ((status = validator.memoCheck(newMemoIfAny)) != OK) {
            return status;
        }
        if (op.hasProxyAccountID()
                && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }

        final var stakedIdCase = op.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!properties.isStakingEnabled() && (electsStakingId || op.hasDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId) {
            if (validSentinel(stakedIdCase, op.getStakedAccountId(), op.getStakedNodeId())) {
                return OK;
            } else if (!validator.isValidStakedId(
                    stakedIdCase,
                    op.getStakedAccountId(),
                    op.getStakedNodeId(),
                    contracts.get(),
                    nodeInfo)) {
                return INVALID_STAKING_ID;
            }
        }

        return OK;
    }
}
