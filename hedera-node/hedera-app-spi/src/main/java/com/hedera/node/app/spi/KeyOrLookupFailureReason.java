/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Record representing the result of a key lookup for signature requirements. If the key lookup
 * succeeds, failureReason will be null. Else if, key lookup failed failureReason will be set
 * providing information about the failure and the key will be set to null. In some cases, when the
 * key need not be looked up when receiver signature required is false, both key and failureReason
 * are null.
 */
public record KeyOrLookupFailureReason(
        @Nullable HederaKey key, @Nullable ResponseCodeEnum failureReason) {
    public static final KeyOrLookupFailureReason PRESENT_BUT_NOT_REQUIRED =
            new KeyOrLookupFailureReason(null, null);

    public boolean failed() {
        return failureReason != null;
    }

    public static KeyOrLookupFailureReason withFailureReason(final ResponseCodeEnum response) {
        return new KeyOrLookupFailureReason(null, response);
    }

    public static KeyOrLookupFailureReason withKey(final HederaKey key) {
        return new KeyOrLookupFailureReason(key, null);
    }
}
