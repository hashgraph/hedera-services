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

package com.hedera.services.bdd.junit.hedera;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

/**
 * Enumerates the possible targets for system functionality (i.e. the
 * {@link HederaFunctionality#SystemDelete} and {@link HederaFunctionality#SystemUndelete}
 * functionalities).
 */
public enum SystemFunctionalityTarget {
    /**
     * There is no applicable target.
     */
    NA,
    /**
     * The target is a file.
     */
    FILE,
    /**
     * The target is a contract.
     */
    CONTRACT
}
