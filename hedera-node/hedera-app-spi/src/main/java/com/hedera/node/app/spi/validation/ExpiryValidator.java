/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.validation;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.hapi.utils.EntityType;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A type that any {@link TransactionHandler} can use to validate the expiry
 * metadata for an attempt to create or update an entity. (The policies
 * governing the validity of such metadata are universally applicable.)
 */
public interface ExpiryValidator {
    /**
     * Validates the expiry metadata for an attempt to create an entity.
     * @param entityCanSelfFundRenewal whether the entity can self-fund its own auto-renewal
     * @param creationMetadata the expiry metadata for the attempted creation
     * @param functionality the HederaFunctionality that is being attempted
     *                           This is only needed until differential testing is completed to comply with
     *                           mono-service response codes.
     *
     * @return the expiry metadata that will result from the creation
     * @throws HandleException if the metadata is invalid
     */
    @NonNull
    ExpiryMeta resolveCreationAttempt(
            boolean entityCanSelfFundRenewal,
            @NonNull final ExpiryMeta creationMetadata,
            @NonNull final HederaFunctionality functionality);

    /**
     * Validates the expiry metadata for an attempt to update an entity, and returns the
     * expiry metadata that will result from the update if it is valid. Otherwise throws
     * a {@link HandleException}.
     *
     * @param currentMetadata the current expiry metadata for the entity
     * @param updateMetadata the expiry metadata for the attempted update
     * @param isForTokenUpdate if the expiry metadata is for token update
     * @return the expiry metadata that will result from the update
     * @throws HandleException if the metadata is invalid
     */
    @NonNull
    ExpiryMeta resolveUpdateAttempt(
            @NonNull ExpiryMeta currentMetadata, @NonNull ExpiryMeta updateMetadata, boolean isForTokenUpdate);

    /**
     * Gets the expiration status of an entity based on the {@link EntityType}.
     * @param entityType entity type
     * @param isMarkedExpired if the entity is marked as expired and pending removal
     * @param balanceAvailableForSelfRenewal if balance is available for self renewal
     * @return OK if the entity is not expired, otherwise the appropriate error code
     */
    @NonNull
    ResponseCodeEnum expirationStatus(
            @NonNull EntityType entityType, boolean isMarkedExpired, long balanceAvailableForSelfRenewal);

    /**
     * Gets the expiration status of an account and returns if the account is detached
     * @param entityType entity type
     * @param isMarkedExpired if the entity is marked as expired and pending removal
     * @param balanceAvailableForSelfRenewal if balance is available for self renewal
     * @return true if the account is detached, otherwise false
     */
    default boolean isDetached(
            @NonNull EntityType entityType, boolean isMarkedExpired, long balanceAvailableForSelfRenewal) {
        return expirationStatus(entityType, isMarkedExpired, balanceAvailableForSelfRenewal) != ResponseCodeEnum.OK;
    }
}
