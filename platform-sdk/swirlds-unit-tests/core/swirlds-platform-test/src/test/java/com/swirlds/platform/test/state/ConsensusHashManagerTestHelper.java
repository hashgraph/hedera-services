package com.swirlds.platform.test.state;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConsensusHashManagerTestHelper extends ConsensusHashManager {
    private final List<IssNotification> issList = new ArrayList<>();

    public ConsensusHashManagerTestHelper(
            @NonNull final PlatformContext platformContext,
            final Time time, final AddressBook addressBook,
            final Hash currentEpochHash,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            final boolean ignorePreconsensusSignatures, final long ignoredRound,
            final IssHandler issHandler) {
        super(platformContext, addressBook, currentEpochHash, currentSoftwareVersion,
                ignorePreconsensusSignatures,
                ignoredRound);
    }

    @Override
    public List<IssNotification> handlePostconsensusSignatures(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        final List<IssNotification> list = super.handlePostconsensusSignatures(transactions);
        Optional.ofNullable(list).ifPresent(issList::addAll);
        return list;
    }

    @Override
    public List<IssNotification> newStateHashed(@NonNull final ReservedSignedState state) {
        final List<IssNotification> list = super.newStateHashed(state);
        Optional.ofNullable(list).ifPresent(issList::addAll);
        return list;
    }

    @Override
    public List<IssNotification> overridingState(@NonNull final ReservedSignedState state) {
        final List<IssNotification> list = super.overridingState(state);
        Optional.ofNullable(list).ifPresent(issList::addAll);
        return list;
    }

    public List<IssNotification> getIssList() {
        return issList;
    }

    public int getIssCount() {
        return issList.size();
    }
}
