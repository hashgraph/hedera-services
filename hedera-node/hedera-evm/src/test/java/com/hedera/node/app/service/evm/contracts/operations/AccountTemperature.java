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

package com.hedera.node.app.service.evm.contracts.operations;

/**
 * Besu's `GasCalculator` defines two overloads of `callOperationGasCost` because an argument
 * was added at the Cancun release.  The overload without that argument is deprecated.  But the
 * argument, named `accountIsWarm`, is a boolean, which provides no information at the point-of-call.
 * So this "constants" class simply provides reasonable names for the two alternatives for this
 * argument.
 */
public final class AccountTemperature {

    public static final boolean ACCOUNT_IS_WARM = true;
    public static final boolean ACCOUNT_IS_NOT_WARM = false;

    private AccountTemperature() {}
}
