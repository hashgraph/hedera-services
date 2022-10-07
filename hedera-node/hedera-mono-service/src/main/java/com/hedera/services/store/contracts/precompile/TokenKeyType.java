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
package com.hedera.services.store.contracts.precompile;

/** All key types used by {@link Precompile} implementations, in one place for easy review. */
public enum TokenKeyType {
    ADMIN_KEY(1),
    KYC_KEY(2),
    FREEZE_KEY(4),
    WIPE_KEY(8),
    SUPPLY_KEY(16),
    FEE_SCHEDULE_KEY(32),
    PAUSE_KEY(64);

    private final int value;

    TokenKeyType(final int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
