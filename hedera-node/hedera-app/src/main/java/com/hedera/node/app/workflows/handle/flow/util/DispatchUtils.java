/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.util;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.Set;

public class DispatchUtils {
    public static final String ALERT_MESSAGE = "Possibly CATASTROPHIC failure";
    public static final Set<HederaFunctionality> CONTRACT_OPERATIONS =
            EnumSet.of(HederaFunctionality.CONTRACT_CREATE, HederaFunctionality.CONTRACT_CALL, ETHEREUM_TRANSACTION);

    public static boolean isContractOperation(@NonNull Dispatch dispatch) {
        return CONTRACT_OPERATIONS.contains(dispatch.txnInfo().functionality());
    }
}
