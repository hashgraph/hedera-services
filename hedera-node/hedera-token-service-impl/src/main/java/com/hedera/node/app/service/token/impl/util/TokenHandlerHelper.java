/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.util;

/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Class for retrieving objects in a certain context, e.g. during a {@code handler.handle(...)} call.
 * This allows compartmentalizing common validation logic without requiring store implementations to
 * throw inappropriately-contextual exceptions, and also abstracts duplicated business logic out of
 * multiple handlers.
 */
public class TokenHandlerHelper {

    private TokenHandlerHelper() {
        throw new UnsupportedOperationException("Utility class only");
    }

    /**
     * Returns the account if it exists and is usable. A {@link HandleException} is thrown if the account is invalid.
     * Note that this method should also work with account ID's that represent smart contracts
     * If the account is deleted the return error code is ACCOUNT_DELETED
     *
     * @param accountId the ID of the account to get
     * @param accountStore the {@link ReadableTokenStore} to use for account retrieval
     * @param expiryValidator the {@link ExpiryValidator} to determine if the account is expired
     * @param errorIfNotUsable the {@link ResponseCodeEnum} to use if the account is not found/usable
     * @throws HandleException if any of the account conditions are not met
     */
    @NonNull
    public static Account getIfUsable(
            @NonNull final AccountID accountId,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ResponseCodeEnum errorIfNotUsable) {
        return getIfUsable(accountId, accountStore, expiryValidator, errorIfNotUsable, ACCOUNT_DELETED);
    }

    /**
     * Returns the account if it exists and is usable. A {@link HandleException} is thrown if the account is invalid.
     * Note that this method should also work with account ID's that represent smart contracts
     * If the account is deleted the return error code is INVALID_AUTORENEW_ACCOUNT
     *
     * @param accountId the ID of the account to get
     * @param accountStore the {@link ReadableTokenStore} to use for account retrieval
     * @param expiryValidator the {@link ExpiryValidator} to determine if the account is expired
     * @throws HandleException if any of the account conditions are not met
     */
    @NonNull
    public static Account getIfUsableForAutoRenew(
            @NonNull final AccountID accountId,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ResponseCodeEnum errorIfNotUsable) {
        return getIfUsable(accountId, accountStore, expiryValidator, errorIfNotUsable, INVALID_AUTORENEW_ACCOUNT);
    }

    /**
     * Returns the account if it exists and is usable. A {@link HandleException} is thrown if the account is invalid.
     * Note that this method should also work with account ID's that represent smart contracts.
     * If the account is deleted the return error code is INVALID_TREASURY_ACCOUNT_FOR_TOKEN
     *
     * @param accountId the ID of the account to get
     * @param accountStore the {@link ReadableTokenStore} to use for account retrieval
     * @param expiryValidator the {@link ExpiryValidator} to determine if the account is expired
     * @throws HandleException if any of the account conditions are not met
     */
    @NonNull
    public static Account getIfUsableWithTreasury(
            @NonNull final AccountID accountId,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ResponseCodeEnum errorIfNotUsable) {
        return getIfUsable(
                accountId, accountStore, expiryValidator, errorIfNotUsable, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @NonNull
    public static Account getIfUsable(
            @NonNull final AccountID accountId,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ResponseCodeEnum errorIfNotUsable,
            @NonNull final ResponseCodeEnum errorOnAccountDeleted) {
        requireNonNull(accountId);
        requireNonNull(accountStore);
        requireNonNull(expiryValidator);
        requireNonNull(errorIfNotUsable);
        requireNonNull(errorOnAccountDeleted);

        final var acct = accountStore.getAccountById(accountId);
        validateTrue(acct != null, errorIfNotUsable);
        final var isContract = acct.smartContract();

        validateFalse(acct.deleted(), isContract ? CONTRACT_DELETED : errorOnAccountDeleted);
        final var type = isContract ? EntityType.CONTRACT : EntityType.ACCOUNT;

        final var expiryStatus =
                expiryValidator.expirationStatus(type, acct.expiredAndPendingRemoval(), acct.tinybarBalance());
        validateTrue(expiryStatus == OK, expiryStatus);

        return acct;
    }

    /**
     * Returns the token if it exists and is usable. A {@link HandleException} is thrown if the token is invalid
     *
     * @param tokenId the ID of the token to get
     * @param tokenStore the {@link ReadableTokenStore} to use for token retrieval
     * @throws HandleException if any of the token conditions are not met
     */
    @NonNull
    public static Token getIfUsable(@NonNull final TokenID tokenId, @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(tokenId);
        requireNonNull(tokenStore);

        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);
        validateFalse(token.deleted(), TOKEN_WAS_DELETED);
        validateFalse(token.paused(), TOKEN_IS_PAUSED);
        return token;
    }

    /**
     * Returns the token relation if it exists and is usable
     *
     * @param accountId the ID of the account
     * @param tokenId the ID of the token
     * @param tokenRelStore the {@link ReadableTokenRelationStore} to use for token relation retrieval
     * @throws HandleException if any of the token relation conditions are not met
     */
    @NonNull
    public static TokenRelation getIfUsable(
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
            @NonNull final ReadableTokenRelationStore tokenRelStore) {
        requireNonNull(accountId);
        requireNonNull(tokenId);
        requireNonNull(tokenRelStore);

        final var tokenRel = tokenRelStore.get(accountId, tokenId);
        validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        validateTrue(!tokenRel.frozen(), ACCOUNT_FROZEN_FOR_TOKEN);

        return tokenRel;
    }

    /**
     * Checks that a key does not represent an immutable account, e.g. the staking rewards account.
     * Throws a {@link PreCheckException} with the designated response code otherwise.
     * @param key the key to check
     * @param responseCode the response code to throw
     * @throws PreCheckException if the account is considered immutable
     */
    public static void verifyIsNotImmutableAccount(
            @Nullable final Key key, @NonNull final ResponseCodeEnum responseCode) throws PreCheckException {
        if (EMPTY_KEY_LIST.equals(key)) {
            throw new PreCheckException(responseCode);
        }
    }
}
