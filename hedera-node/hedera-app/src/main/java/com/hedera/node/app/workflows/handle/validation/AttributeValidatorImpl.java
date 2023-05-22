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

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.validation.AttributeValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

public class AttributeValidatorImpl implements AttributeValidator {
    @Override
    public void validateKey(Key key) {
        // TODO: Implement AttributeValidatorImpl.validateKey()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void validateMemo(String memo) {
        // TODO: Implement AttributeValidatorImpl.validateMemo()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void validateExpiry(long expiry) {
        // TODO: Implement AttributeValidatorImpl.validateExpiry()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void validateAutoRenewPeriod(long autoRenewPeriod) {
        // TODO: Implement AttributeValidatorImpl.validateAutoRenewPeriod()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isImmutableKey(@NonNull Key key) {
        // TODO: Implement AttributeValidatorImpl.isImmutableKey()
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
