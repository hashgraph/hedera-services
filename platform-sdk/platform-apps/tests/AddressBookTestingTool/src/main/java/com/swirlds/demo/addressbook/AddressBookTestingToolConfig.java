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

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Config for address books and utilities that deal with address books.
 *
 * @param softwareVersion
 * 		The integer value of the software version of the AddressBookTestingToolMain SwirldMain application.
 * @param weightingBehavior
 *      The integer value of the weighting behavior of the AddressBookTestingToolState SwirldState.
 * @param testScenario
 *     The string value of the test scenario being run for validation. This must match an enumerated value in {@link AddressBookTestScenario}.
 */
@ConfigData("addressBookTestingTool")
public record AddressBookTestingToolConfig(
        @ConfigProperty(defaultValue = "1") int softwareVersion,
        @ConfigProperty(defaultValue = "0") int weightingBehavior,
        @ConfigProperty(defaultValue = "SKIP_VALIDATION") String testScenario) {}
