/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody.StakedIdOneOfType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Account.Builder;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.OneOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_UPDATE}.
 */
@Singleton
public class ContractUpdateHandler implements TransactionHandler {

    private static final Key IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().keyList(KeyList.newBuilder()).build();

    @Inject
    public ContractUpdateHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().contractUpdateInstanceOrThrow();

        if (isAdminSigRequired(op)) {
            context.requireKeyOrThrow(op.contractIDOrElse(ContractID.DEFAULT), INVALID_CONTRACT_ID);
        }
        if (hasCryptoAdminKey(op)) {
            context.requireKey(op.adminKeyOrThrow());
        }
        if (op.hasAutoRenewAccountId() && !op.autoRenewAccountIdOrThrow().equals(AccountID.DEFAULT)) {
            context.requireKeyOrThrow(op.autoRenewAccountIdOrThrow(), INVALID_AUTORENEW_ACCOUNT);
        }

        // TODO: Mono is using UpdateCustomizerFactory.processAdminKey but we don't have JContractIDKey/JKey
        //  implementation in mod. So I stitched this together.
        //  It's probably incorrect so I will wait for the reviews for guidance
        if (op.hasAdminKey()) {
            // TODO: is this correct? Is the contractID field deprecated?
            if (op.adminKey().contractID() != null) {
                throw new PreCheckException(INVALID_ADMIN_KEY);
            }
            if (op.adminKey().hasThresholdKey() && !op.adminKey().thresholdKey().hasKeys()) {
                throw new PreCheckException(INVALID_ADMIN_KEY);
            }
        }
    }

    private boolean isAdminSigRequired(final ContractUpdateTransactionBody op) {
        return !op.hasExpirationTime()
                || hasCryptoAdminKey(op)
                || op.hasProxyAccountID()
                || op.hasAutoRenewPeriod()
                || op.hasFileID()
                || op.memoOrElse("").length() > 0;
    }

    private boolean hasCryptoAdminKey(final ContractUpdateTransactionBody op) {
        return op.hasAdminKey() && !op.adminKeyOrThrow().hasContractID();
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var txn = requireNonNull(context).body();
        final var op = txn.contractUpdateInstanceOrThrow();
        final var target = op.contractIDOrThrow();

        final var accountStore = context.readableStore(ReadableAccountStore.class);

        // validate update account exists
        final var toBeUpdated = accountStore.getContractById(target);
        validateTrue(toBeUpdated != null, INVALID_CONTRACT_ID);

        final var changed = update(toBeUpdated, context, op);

        context.serviceApi(TokenServiceApi.class).updateContract(changed);
    }

    private enum ExtensionType {
        NO_EXTENSION,
        VALID_EXTENSION,
        INVALID_EXTENSION
    }

    public Account update(
            @NonNull final Account contract,
            @NonNull final HandleContext context, // TODO: should I pass the HandleContext or be mre specific?
            @NonNull final ContractUpdateTransactionBody op) {
        final var customizer = new HederaAccountCustomizer(); // TODO: delete
        final var builder = contract.copyBuilder();

        var expiryExtension = ExtensionType.NO_EXTENSION;
        if (op.hasExpirationTime()) {
            // TODO: refactor
            boolean isValid = true;
            try {
                // TODO: seconds?
                context.attributeValidator().validateExpiry(op.expirationTime().seconds());
            } catch (Exception e) {
                isValid = false;
            }

            expiryExtension = isValid ? ExtensionType.VALID_EXTENSION : ExtensionType.INVALID_EXTENSION;
        }
        if (contract.expiredAndPendingRemoval()) {
            if (expiryExtension == ExtensionType.VALID_EXTENSION) {
                // customizer.isExpiredAndPendingRemoval(false); ??
            } else {
                throw new HandleException(CONTRACT_EXPIRED_AND_PENDING_REMOVAL);
            }
        }

        if (!onlyAffectsExpiry(op) && !isMutable(contract)) {
            throw new HandleException(MODIFYING_IMMUTABLE_CONTRACT);
        }

        if (reducesExpiry(op, contract.expirationSecond())) {
            throw new HandleException(EXPIRATION_REDUCTION_NOT_ALLOWED);
        }

        var cid = op.contractID();
        if (op.hasAdminKey() && processAdminKey(op, contract, builder)) {
            throw new HandleException(INVALID_ADMIN_KEY);
        }
        if (op.hasAutoRenewPeriod()) {
            builder.autoRenewSeconds(op.autoRenewPeriod().seconds());
        }
        if (expiryExtension == ExtensionType.INVALID_EXTENSION) {
            throw new HandleException(INVALID_EXPIRATION_TIME);
        }
        if (expiryExtension == ExtensionType.VALID_EXTENSION) {
            builder.expirationSecond(op.expirationTime().seconds());
        }

        // TODO: refactor: new method
        final var newMemo = op.hasMemoWrapper() ? op.memoWrapper() : op.memo();
        context.attributeValidator().validateMemo(newMemo);
        if (affectsMemo(op)) {
            processMemo(op, builder);
        }
        // TODO:
        //        if (op.stakedAccountId() != null) { // TOOD:?
        //            builder.stakedAccountId(op.stakedAccountId());
        //            //customizer.customizeStakedId(op.getStakedIdCase().name(), op.getStakedAccountId(),
        // op.getStakedNodeId());
        //        }

        //        switch (op.stakedId().kind()) {
        //            case STAKED_NODE_ID -> builder.stakedNodeId(op.stakedNodeId())
        //        }

        //        if (op.stakedAccountId() != null) {
        //            builder.stakedAccountId(op.stakedAccountId());
        //        }

        if (op.stakedId().kind() != StakedIdOneOfType.UNSET) {
            // final var stakedId = getStakedId(op.stakedId(), op.stakedAccountId(), op.stakedNodeId());
            // builder.stakedNodeId(stakedId);
            if (op.stakedAccountId() != null) {
                builder.stakedAccountId(
                        AccountID.newBuilder().accountNum(op.stakedAccountId().accountNum()));
            }
            if (op.stakedNodeId() != null) {
                builder.stakedNodeId(op.stakedNodeId());
            }

            // customizer.customizeStakedId(op.getStakedIdCase().name(), op.getStakedAccountId(), op.getStakedNodeId());
        }

        //        if (op.stakedNodeId() != null) {
        //            builder.stakedNodeId(op.stakedNodeId());
        //        }

        if (op.hasDeclineReward()) {
            builder.declineReward(op.declineReward());
        }
        if (op.hasAutoRenewAccountId()) {
            builder.autoRenewAccountId(op.autoRenewAccountId());
        }
        if (op.hasMaxAutomaticTokenAssociations()) {
            final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
            if (op.maxAutomaticTokenAssociations() > ledgerConfig.maxAutoAssociations()) {
                throw new HandleException(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
            }
            builder.maxAutoAssociations(op.maxAutomaticTokenAssociations());
        }

        return builder.build();
    }

    // TODO: order

    /**
     * Gets the stakedId from the provided staked_account_id or staked_node_id.
     *
     * @param stakedAccountId given staked_account_id
     * @param stakedNodeId given staked_node_id
     * @return valid staked id
     */
    static long getStakedId(
            final OneOf<StakedIdOneOfType> stakedId, final AccountID stakedAccountId, final Long stakedNodeId) {
        if (stakedId.kind() == StakedIdOneOfType.STAKED_ACCOUNT_ID) {
            return stakedAccountId.accountNum();
        } else {
            // return a number less than the given node Id, in order to recognize the if nodeId 0 is
            // set
            return -stakedNodeId - 1;
        }
    }

    public static final String STAKED_ID_NOT_SET_CASE = "STAKEDID_NOT_SET";

    public static boolean hasStakedId(final String idCase) {
        return !idCase.equals(STAKED_ID_NOT_SET_CASE);
    }

    private void processMemo(final ContractUpdateTransactionBody op, final Builder builder) {
        final var newMemo = op.hasMemoWrapper() ? op.memoWrapper() : op.memo();
        builder.memo(newMemo);
    }

    // TODO: I butchered this method because I couldn't find JKey implementation in mod
    private boolean processAdminKey(
            final ContractUpdateTransactionBody updateOp, final Account contract, final Builder builder) {
        if (IMMUTABILITY_SENTINEL_KEY.equals(updateOp.adminKey())) {
            builder.key(contract.key());
        } else {
            builder.key(updateOp.adminKey());
            // TODO:
            //            var resolution = keyIfAcceptable(updateOp.adminKey());
            //            if (resolution.isEmpty()) {
            //                return true;
            //            }
            //            builder.key(resolution.get());
            // builder.key() TODO?
            // customizer.key(resolution.get());
        }
        return false;
    }

    private boolean reducesExpiry(ContractUpdateTransactionBody op, long curExpiry) {
        return op.hasExpirationTime() && op.expirationTime().seconds() < curExpiry;
    }

    boolean onlyAffectsExpiry(ContractUpdateTransactionBody op) {
        return !(op.hasProxyAccountID()
                || op.hasFileID()
                || affectsMemo(op)
                || op.hasAutoRenewPeriod()
                || op.hasAdminKey());
    }

    boolean affectsMemo(ContractUpdateTransactionBody op) {
        return op.hasMemoWrapper() || (op.memo() != null && op.memo().length() > 0);
    }

    boolean isMutable(final Account contract) {
        return Optional.ofNullable(contract.key())
                .map(key -> !key.hasContractID())
                .orElse(false);
    }

    //    private Optional<JKey> keyIfAcceptable(Key candidate) {
    //        return Optional.empty();
    //    }
    //
    //    private Optional<JKey> keyIfAcceptable(Key candidate) {
    //        var key = MiscUtils.asUsableFcKey(candidate);
    //        if (key.isEmpty() || key.get() instanceof JContractIDKey) {
    //            return Optional.empty();
    //        }
    //        return key;
    //    }
    //
    //    public static Optional<JKey> asUsableFcKey(final Key key) {
    //        if (key.getKeyCase() == com.hederahashgraph.api.proto.java.Key.KeyCase.KEY_NOT_SET) {
    //            return Optional.empty();
    //        }
    //        try {
    //            final var fcKey = JKey.mapKey(key);
    //            if (!fcKey.isValid()) {
    //                return Optional.empty();
    //            }
    //            return Optional.of(fcKey);
    //        } catch (final InvalidKeyException ignore) {
    //            return Optional.empty();
    //        }
    //    }

}
