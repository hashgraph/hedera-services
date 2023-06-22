/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.InProgressChildRecord;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.ledger.accounts.AliasManager.tryAddressRecovery;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class AutoAccountCreator {

    private WritableAccountStore accountStore;
    private HandleContext handleContext;
    // checks tokenAliasMap if the change consists an alias that is already used in previous
    // iteration of the token transfer list. This map is used to count number of
    // maxAutoAssociations needed on auto created account
    protected final Map<Bytes, Set<AccountID>> tokenAliasMap = new HashMap<>();
    public AutoAccountCreator(@NonNull final WritableAccountStore accountStore,
                              @NonNull final HandleContext handleContext) {
        this.handleContext = handleContext;
        this.accountStore = accountStore;
    }

    public void create(boolean isForToken, final Bytes alias){
        final var accountsConfig = handleContext.configuration().getConfigData(AccountsConfig.class);
        validateTrue(accountStore.sizeOfAccountState() + 1 <= accountsConfig.maxNumber(),
                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);
        if(isForToken){
            validateTrue(tokensConfig.autoCreationsIsEnabled(), ResponseCodeEnum.NOT_SUPPORTED);
        }

        TransactionBody.Builder syntheticCreation;
        String memo;
        HederaAccountCustomizer customizer = new HederaAccountCustomizer();
        // checks tokenAliasMap if the change consists an alias that is already used in previous
        // iteration of the token transfer list. This map is used to count number of
        // maxAutoAssociations needed on auto created account
        if(isForToken){
            tokenAliasMap.putIfAbsent(alias, Collections.emptySet());
        }
        final var maxAutoAssociations =
                tokenAliasMap.getOrDefault(alias, Collections.emptySet()).size();
        customizer.maxAutomaticAssociations(maxAutoAssociations);
        final var isAliasEVMAddress = EntityIdUtils.isOfEvmAddressSize(alias);
        if (isAliasEVMAddress) {
            syntheticCreation = syntheticTxnFactory.createHollowAccount(alias, 0L);
            customizer.key(EMPTY_KEY);
            memo = LAZY_MEMO;
        } else {
            final var key = asPrimitiveKeyUnchecked(alias);
            JKey jKey = asFcKeyUnchecked(key);

            syntheticCreation = syntheticTxnFactory.createAccount(alias, key, 0L, maxAutoAssociations);
            customizer.key(jKey);
            memo = AUTO_MEMO;
        }

        customizer
                .memo(memo)
                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                .expiry(txnCtx.consensusTime().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                .isReceiverSigRequired(false)
                .isSmartContract(false)
                .alias(alias);

        var fee = autoCreationFeeFor(syntheticCreation);
        if (isAliasEVMAddress) {
            fee += getLazyCreationFinalizationFee();
        }

        final var newId = ids.newAccountId();
        accountsLedger.create(newId);
        replaceAliasAndSetBalanceOnChange(change, newId);

        customizer.customize(newId, accountsLedger);

        final var sideEffects = new SideEffectsTracker();
        sideEffects.trackAutoCreation(newId);

        final var childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, memo);

        if (!isAliasEVMAddress) {
            final var key = asPrimitiveKeyUnchecked(alias);

            if (key.hasECDSASecp256K1()) {
                final JKey jKey = asFcKeyUnchecked(key);
                final var evmAddress = tryAddressRecovery(jKey, EthSigsUtils::recoverAddressFromPubKey);
                childRecord.setEvmAddress(evmAddress);
            }
        }

        childRecord.setFee(fee);

        final var inProgress =
                new InProgressChildRecord(DEFAULT_SOURCE_ID, syntheticCreation, childRecord, Collections.emptyList());
        pendingCreations.add(inProgress);

        trackAlias(alias, newId);

        return Pair.of(OK, fee);
    }

}
