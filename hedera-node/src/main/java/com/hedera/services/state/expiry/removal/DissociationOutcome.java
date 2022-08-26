package com.hedera.services.state.expiry.removal;

import com.hedera.services.utils.EntityNumPair;

public record DissociationOutcome(int numDissociated, EntityNumPair newRoot) {
}
