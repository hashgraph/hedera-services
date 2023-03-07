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

package com.hedera.node.app.authorization;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Verifies whether an account is authorized to perform a specific function.
 */
public interface Authorizer {
    /**
     * Checks if the given account is authorized to perform the given function.
     *
     * @param id The ID of the account to check
     * @param function The specific functionality to check
     * @return true if the account is authorized, otherwise false.
     */
    boolean isAuthorized(@NonNull AccountID id, @NonNull HederaFunctionality function);
}
