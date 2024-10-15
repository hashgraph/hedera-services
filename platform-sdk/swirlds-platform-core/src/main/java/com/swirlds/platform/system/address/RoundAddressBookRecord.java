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

package com.swirlds.platform.system.address;

import com.swirlds.common.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Signals that a new roster has been selected. This happens once per round regardless of whether or not the new roster
 * is any different that the prior roster.
 *
 * @param effectiveRound    the round in which this roster becomes effective
 * @param addressBook        the new address book
 */
public record RoundAddressBookRecord(long effectiveRound, @NonNull AddressBook addressBook) {}
