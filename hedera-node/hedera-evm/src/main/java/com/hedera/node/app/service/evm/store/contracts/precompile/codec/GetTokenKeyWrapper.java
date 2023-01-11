/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public record GetTokenKeyWrapper<T>(T token, long keyType) {

    public TokenKeyType tokenKeyType() {
        return switch (((int) keyType)) {
            case 1 -> TokenKeyType.ADMIN_KEY;
            case 2 -> TokenKeyType.KYC_KEY;
            case 4 -> TokenKeyType.FREEZE_KEY;
            case 8 -> TokenKeyType.WIPE_KEY;
            case 16 -> TokenKeyType.SUPPLY_KEY;
            case 32 -> TokenKeyType.FEE_SCHEDULE_KEY;
            case 64 -> TokenKeyType.PAUSE_KEY;
            default -> throw new InvalidTransactionException(
                    ResponseCodeEnum.KEY_NOT_PROVIDED, true);
        };
    }
}
