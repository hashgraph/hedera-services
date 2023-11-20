package com.swirlds.platform.roster;

import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

public record NewRoster(
        long effectiveRound,
        @NonNull AddressBook roster) {
}
