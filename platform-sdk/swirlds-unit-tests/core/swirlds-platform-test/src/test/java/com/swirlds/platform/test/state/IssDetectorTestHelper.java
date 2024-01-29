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

package com.swirlds.platform.test.state;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.IssNotification.IssType;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class IssDetectorTestHelper extends IssDetector {
    /** the default epoch hash to use */
    private static final Hash DEFAULT_EPOCH_HASH = null;

    private final List<IssNotification> issList = new ArrayList<>();

    public IssDetectorTestHelper(
            @NonNull final PlatformContext platformContext, final AddressBook addressBook, final long ignoredRound) {
        super(platformContext, addressBook, DEFAULT_EPOCH_HASH, new BasicSoftwareVersion(1), false, ignoredRound);
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
        return issList.stream()
                .map(IssNotification::getIssType)
                .filter(Set.of(types)::contains)
                .count();
    }

    private List<IssNotification> processList(final List<IssNotification> list) {
        Optional.ofNullable(list).ifPresent(issList::addAll);
        return list;
    }
}
