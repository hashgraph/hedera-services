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

package com.hedera.node.app.workflows.handle.validation;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link ExpiryValidator}.
 *
 * <p>The implementation is incomplete, and is a placeholder for future work.
 * GitHub Issue <a href="https://github.com/hashgraph/hedera-services/issues/6701">(#6701)</a>
 */
public class ExpiryValidatorImpl implements ExpiryValidator {

    @Override
    public ExpiryMeta resolveCreationAttempt(boolean entityCanSelfFundRenewal, ExpiryMeta creationMetadata) {
        // TODO: Implement resolveCreationAttempt
        return creationMetadata;
    }

    @Override
    public ExpiryMeta resolveUpdateAttempt(ExpiryMeta currentMetadata, ExpiryMeta updateMetadata) {
        // TODO: Implement resolveUpdateAttempt
        return updateMetadata;
    }

    @NonNull
    @Override
    public ResponseCodeEnum expirationStatus(
            @NonNull EntityType entityType, boolean isMarkedExpired, long balanceAvailableForSelfRenewal) {
        // TODO: Implement expirationStatus
        return ResponseCodeEnum.OK;
    }
}
