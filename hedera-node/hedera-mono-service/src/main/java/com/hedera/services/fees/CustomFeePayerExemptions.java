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
package com.hedera.services.fees;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;

/**
 * Defines a type that determines if a custom fee's payer is exempt from a given custom fee. Please
 * note that there are two other cases in which we exempt a custom fee:
 *
 * <ol>
 *   <li>When a fractional fee collector sends units of its collected token, we do not immediately
 *       reclaim any of these from the receiving account (which would be the effective payer).
 *   <li>When a token treasury sends NFTs with a fallback to an account without any value exchanged,
 *       we do not apply the fallback fee to the receiving account (which would be the effective
 *       payer).
 * </ol>
 */
public interface CustomFeePayerExemptions {
    boolean isPayerExempt(CustomFeeMeta feeMeta, FcCustomFee fee, Id payer);
}
