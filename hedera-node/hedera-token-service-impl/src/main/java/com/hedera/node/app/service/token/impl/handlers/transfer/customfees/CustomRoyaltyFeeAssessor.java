/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;

import java.util.Map;

public class CustomRoyaltyFeeAssessor {
    public CustomRoyaltyFeeAssessor() {}

    public void assessRoyaltyFees(final CustomFeeMeta feeMeta,
                                  final AccountID sender,
                                  final Map<AccountID, Long> hbarAdjustments,
                                  final Map<TokenID, Map<AccountID, Long>> htsAdjustments) {
    }
}
