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
package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.ledger.properties.TokenProperty.ADMIN_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.FEE_SCHEDULE_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.FREEZE_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.KYC_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.PAUSE_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.SUPPLY_KEY;
import static com.hedera.services.ledger.properties.TokenProperty.WIPE_KEY;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

public record GetTokenKeyWrapper(TokenID tokenID, long keyType) {

    public TokenProperty tokenKeyType() {
        return switch (((int) keyType)) {
            case 1 -> ADMIN_KEY;
            case 2 -> KYC_KEY;
            case 4 -> FREEZE_KEY;
            case 8 -> WIPE_KEY;
            case 16 -> SUPPLY_KEY;
            case 32 -> FEE_SCHEDULE_KEY;
            case 64 -> PAUSE_KEY;
            default -> throw new InvalidTransactionException(
                    ResponseCodeEnum.KEY_NOT_PROVIDED, true);
        };
    }
}
