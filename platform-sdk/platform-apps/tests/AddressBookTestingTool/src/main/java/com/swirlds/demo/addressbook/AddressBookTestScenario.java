/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.addressbook;

public enum AddressBookTestScenario {
    /** Skip validation of the address book initialization. */
    SKIP_VALIDATION,

    /** On Genesis, the config address book is forced to be used. */
    GENESIS_FORCE_CONFIG_AB,

    /** On Genesis, use normal behavior,the config address book is not forced to be used. */
    GENESIS_NORMAL,

    /** On restart, no upgrade, use the saved state address book. */
    NO_UPGRADE_USE_SAVED_STATE,

    /** On restart, no upgrade, force use of the config address book. */
    NO_UPGRADE_FORCE_CONFIG_AB,

    /** On restart, upgrade, update address book weight with behavior 2. */
    UPGRADE_WEIGHT_BEHAVIOR_2,

    /** On restart, upgrade, force use of the config address book. */
    UPGRADE_FORCE_CONFIG_AB;
}
