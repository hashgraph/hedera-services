/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Config for address books and utilities that deal with address books.
 *
 * @param updateAddressBookOnlyAtUpgrade
 * 		If true, then don't change the working address book unless the network restarts with a higher software version.
 * 		Intermediate workaround until the platform is capable of handling an address book that changes at runtime. This
 * 		feature will be removed in a future version. Check if the address book override is enabled. If enabled, then
 * 		the address book will only change when a node starts up. This feature will be removed in a future version.
 */
@ConfigData("addressBook")
public record AddressBookConfig(@ConfigProperty(defaultValue = "true") boolean updateAddressBookOnlyAtUpgrade) {}
