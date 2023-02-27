/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.transaction.system;

import com.swirlds.common.system.transaction.internal.SystemTransaction;

/**
 * A record containing information about a pre-consensus system transaction handler
 *
 * @param transactionClass the class of system transaction the handler requires
 * @param handleMethod     the method to handle this type of system transaction
 * @param <T>              the system transaction type
 */
public record PreConsensusSystemTransactionTypedHandler<T extends SystemTransaction>(
        Class<T> transactionClass, PreConsensusSystemTransactionHandler<T> handleMethod) {
}
