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

package com.swirlds.platform.event.validation;

import static com.swirlds.logging.LogMarker.EVENT_SIG;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.event.GossipEvent;
import java.security.PublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link GossipEventValidator} which validates the event's signature
 */
public class SignatureValidator implements GossipEventValidator {
    private static final Logger logger = LogManager.getLogger(SignatureValidator.class);
    private final AddressBook addressBook;

    public SignatureValidator(final AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    /**
     * Validates an event's signature
     *
     * @param event
     * 		the event to be verified
     * @param publicKey
     * 		the public used to validate the event signature
     * @return true iff the signature is crypto-verified to be correct
     */
    private static boolean isValidSignature(final BaseEvent event, final PublicKey publicKey) {
        logger.debug(
                EVENT_SIG.getMarker(),
                "event signature is about to be verified. {}",
                () -> EventStrings.toShortString(event));

        final boolean valid = CryptoStatic.verifySignature(
                event.getHashedData().getHash().getValue(),
                event.getUnhashedData().getSignature(),
                publicKey);

        if (!valid) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    """
							failed the signature check {}
							with sig {}
							and hash {}
							transactions are null: {} (if true, this is probably an event from a signed state)""",
                    () -> event,
                    () -> CommonUtils.hex(event.getUnhashedData().getSignature()),
                    () -> event.getHashedData().getHash(),
                    () -> event.getHashedData().getTransactions() == null);
        }

        return valid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventValid(final GossipEvent event) {
        final long creatorId = event.getHashedData().getCreatorId();
        final Address address = addressBook.getAddress(creatorId);
        if (address == null) {
            logger.error(EXCEPTION.getMarker(), "Cannot find address for creator with ID: {}", () -> creatorId);
            return false;
        }
        final PublicKey publicKey = address.getSigPublicKey();
        return isValidSignature(event, publicKey);
    }
}
