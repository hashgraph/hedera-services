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
package com.hedera.services.store.contracts.precompile.codec;

public record TokenKeyWrapper(int keyType, KeyValueWrapper key) {
    public boolean isUsedForAdminKey() {
        return (keyType & 1) != 0;
    }

    public boolean isUsedForKycKey() {
        return (keyType & 2) != 0;
    }

    public boolean isUsedForFreezeKey() {
        return (keyType & 4) != 0;
    }

    public boolean isUsedForWipeKey() {
        return (keyType & 8) != 0;
    }

    public boolean isUsedForSupplyKey() {
        return (keyType & 16) != 0;
    }

    public boolean isUsedForFeeScheduleKey() {
        return (keyType & 32) != 0;
    }

    public boolean isUsedForPauseKey() {
        return (keyType & 64) != 0;
    }
}
