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

package com.hedera.node.app.spi.validation;

import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * A type that any {@link TransactionHandler} can use to validate the expiry
 * metadata for an attempt to create or update an entity. (The policies
 * governing the validity of such metadata are universally applicable.)
 */
public interface EntityExpiryValidator {
    /**
     * Validates the expiry metadata for an attempt to create an entity.
     *
     * @param entityCanSelfFundRenewal whether the entity can self-fund its own auto-renewal
     * @param creationMetadata the expiry metadata for the attempted creation
     * @return the result of the validation
     */
    ResponseCodeEnum validateCreationAttempt(boolean entityCanSelfFundRenewal, EntityExpiryMetadata creationMetadata);

    /**
     * Validates the expiry metadata for an attempt to update an entity.
     *
     * @param currentMetadata the current expiry metadata for the entity
     * @param updateMetadata the expiry metadata for the attempted update
     * @return the result of the validation
     */
    UpdateEntityExpiryMetadata resolveAndValidateUpdateAttempt(
            EntityExpiryMetadata currentMetadata, EntityExpiryMetadata updateMetadata);
}
