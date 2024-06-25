/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.swirlds.platform.system.address.AddressBook;

/**
 * Validates a signed state received via reconnect.
 */
public interface SignedStateValidator {

    /**
     * Determines if a signed state is valid with the address book. Validation usually includes
     * verifying that the signed state is signed with a sufficient number of valid signatures to meet a certain weighting
     * threshold, but other requirements could be included as well.
     *
     * @param signedState       the signed state to validate
     * @param addressBook       the address book used for this signed state
     * @param previousStateData A {@link SignedStateValidationData} containing data from the
     *        {@link com.swirlds.platform.state.PlatformState} in the state before the signed state to be validated.
     *        This may be used to ensure signed state is usable and valid, and also contains useful information for
     *        diagnostics produced when the signed state is not considered valid.
     * @throws SignedStateInvalidException if the signed state is not valid
     */
    void validate(
            final SignedState signedState,
            final AddressBook addressBook,
            final SignedStateValidationData previousStateData)
            throws SignedStateInvalidException;
}
