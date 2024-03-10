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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.token.AliasUtils.isSerializedProtoKey;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The context of a token transfer. This is used to pass information between the steps of the transfer.
 */
public class TransferContextImpl implements TransferContext {
    private final WritableAccountStore accountStore;
    private final AutoAccountCreator autoAccountCreator;
    private final HandleContext context;
    private int numAutoCreations;
    private int numLazyCreations;
    private final Map<Bytes, AccountID> resolutions = new LinkedHashMap<>();
    private final AutoCreationConfig autoCreationConfig;
    private final LazyCreationConfig lazyCreationConfig;
    private final TokensConfig tokensConfig;
    private final List<TokenAssociation> automaticAssociations = new ArrayList<>();
    private final List<AssessedCustomFee> assessedCustomFees = new ArrayList<>();
    private final boolean enforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments;

    public TransferContextImpl(final HandleContext context) {
        this(context, true);
    }

    public TransferContextImpl(
            final HandleContext context, final boolean enforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments) {
        this.context = context;
        this.accountStore = context.writableStore(WritableAccountStore.class);
        this.autoAccountCreator = new AutoAccountCreator(context);
        this.autoCreationConfig = context.configuration().getConfigData(AutoCreationConfig.class);
        this.lazyCreationConfig = context.configuration().getConfigData(LazyCreationConfig.class);
        this.tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        this.enforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments =
                enforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments;
    }

    @Override
    public AccountID getFromAlias(final AccountID aliasedId) {
        final var account = accountStore.get(aliasedId);

        if (account != null) {
            final var id = account.accountId();
            resolutions.put(aliasedId.alias(), id);
            return id;
        }
        return null;
    }

    @Override
    public void createFromAlias(final Bytes alias, final int reqMaxAutoAssociations) {
        // if it is a serialized proto key, auto-create account
        if (isOfEvmAddressSize(alias)) {
            // if it is an evm address create a hollow account
            validateTrue(lazyCreationConfig.enabled(), NOT_SUPPORTED);
            numLazyCreations++;
        } else if (isSerializedProtoKey(alias)) {
            validateTrue(autoCreationConfig.enabled(), NOT_SUPPORTED);
            numAutoCreations++;
        } else {
            // Only EVM addresses and key aliases are supported when creating a new account.
            throw new HandleException(INVALID_ALIAS_KEY);
        }
        // if this auto creation is from a token transfer, check if auto creation from tokens is enabled
        if (reqMaxAutoAssociations > 0) {
            validateTrue(tokensConfig.autoCreationsIsEnabled(), NOT_SUPPORTED);
        }
        // Keep the created account in the resolutions map
        AccountID createdAccount;
        try {
            createdAccount = autoAccountCreator.create(alias, reqMaxAutoAssociations);
        } catch (HandleException e) {
            if (getHandleContext().isSelfSubmitted()) {
                final int autoCreationsNumber = numOfAutoCreations() + numOfLazyCreations();
                getHandleContext().reclaimPreviouslyReservedThrottle(autoCreationsNumber, CRYPTO_CREATE);
            }
            // we only want to reclaim the previously reserved throttle for `CRYPTO_CREATE` transaction
            // if there is a failed auto-creation triggered from CryptoTransfer
            // this is why we re-throw the HandleException, so that it will be still tackled the same in HandleWorkflow
            throw e;
        }
        resolutions.put(alias, createdAccount);
    }

    @Override
    public HandleContext getHandleContext() {
        return context;
    }

    @Override
    public int numOfAutoCreations() {
        return numAutoCreations;
    }

    @Override
    public void chargeExtraFeeToHapiPayer(final long amount) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Map<Bytes, AccountID> resolutions() {
        return resolutions;
    }

    @Override
    public int numOfLazyCreations() {
        return numLazyCreations;
    }

    public static boolean isOfEvmAddressSize(final Bytes alias) {
        return alias.length() == EVM_ADDRESS_SIZE;
    }

    /* ------------------- Needed for building records ------------------- */
    public void addToAutomaticAssociations(TokenAssociation newAssociation) {
        automaticAssociations.add(newAssociation);
    }

    public List<TokenAssociation> getAutomaticAssociations() {
        return automaticAssociations;
    }

    public void addToAssessedCustomFee(AssessedCustomFee assessedCustomFee) {
        assessedCustomFees.add(assessedCustomFee);
    }

    @Override
    public List<AssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
    }

    public boolean isEnforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments() {
        return enforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments;
    }

    @Override
    public void validateHbarAllowances() {
        final var topLevelPayer = context.payer();
        final var op = context.body().cryptoTransferOrThrow();
        for (final var aa : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList())) {
            if (aa.isApproval() && aa.amount() < 0L) {
                maybeValidateHbarAllowance(
                        accountStore.get(aa.accountIDOrElse(AccountID.DEFAULT)), topLevelPayer, aa.amount());
            }
        }
    }

    private void maybeValidateHbarAllowance(
            @Nullable final Account account, @NonNull final AccountID topLevelPayer, final long amount) {
        if (account != null) {
            final var cryptoAllowances = account.cryptoAllowancesOrElse(emptyList());
            for (final var allowance : cryptoAllowances) {
                if (topLevelPayer.equals(allowance.spenderId())) {
                    final var newAllowanceAmount = allowance.amount() + amount;
                    validateTrue(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);
                    return;
                }
            }
            throw new HandleException(SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        }
    }
}
