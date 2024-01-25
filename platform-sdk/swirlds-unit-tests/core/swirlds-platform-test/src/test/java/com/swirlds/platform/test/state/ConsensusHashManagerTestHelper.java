package com.swirlds.platform.test.state;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.IssNotification.IssType;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ConsensusHashManagerTestHelper extends ConsensusHashManager {
    /** the default epoch hash to use */
    private static final Hash DEFAULT_EPOCH_HASH = null;
    private final List<IssNotification> issList = new ArrayList<>();

    public ConsensusHashManagerTestHelper(
            @NonNull final PlatformContext platformContext,
            final AddressBook addressBook,
            final long ignoredRound) {
        super(platformContext, addressBook, DEFAULT_EPOCH_HASH, new BasicSoftwareVersion(1),
                false,
                ignoredRound);
    }

    @Override
    public List<IssNotification> roundCompleted(final long round) {
        return processList(super.roundCompleted(round));
    }

    @Override
    public List<IssNotification> handlePostconsensusSignatures(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        return processList(super.handlePostconsensusSignatures(transactions));
    }

    @Override
    public List<IssNotification> newStateHashed(@NonNull final ReservedSignedState state) {
        return processList(super.newStateHashed(state));
    }

    @Override
    public List<IssNotification> overridingState(@NonNull final ReservedSignedState state) {
        return processList(super.overridingState(state));
    }

    public List<IssNotification> getIssList() {
        return issList;
    }

    public int getIssCount() {
        return issList.size();
    }

    public long getIssCount(final IssType... types) {
        return issList.stream().map(IssNotification::getIssType).filter(Set.of(types)::contains).count();
    }

    private List<IssNotification> processList(final List<IssNotification> list) {
        Optional.ofNullable(list).ifPresent(issList::addAll);
        return list;
    }
}
