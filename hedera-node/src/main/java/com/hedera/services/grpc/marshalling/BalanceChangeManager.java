/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class BalanceChangeManager {
    private final List<BalanceChange> changesSoFar;
    private final Map<Pair<Id, Id>, BalanceChange> indexedChanges = new HashMap<>();

    private int nextCandidateChange;
    private int levelNo = 0;
    private int levelStart = 0;
    private int levelEnd;
    private Set<Pair<Id, Id>> royaltiesPaid = null;

    public interface ChangeManagerFactory {
        BalanceChangeManager from(List<BalanceChange> changesSoFar, int numHbar);
    }

    public BalanceChangeManager(List<BalanceChange> changesSoFar, int numHbar) {
        nextCandidateChange = numHbar;
        this.changesSoFar = changesSoFar;
        changesSoFar.forEach(this::index);
        levelEnd = changesSoFar.size();
    }

    public void markRoyaltyPaid(final Id uniqueToken, final Id account) {
        if (royaltiesPaid == null) {
            royaltiesPaid = new HashSet<>();
        }
        royaltiesPaid.add(Pair.of(uniqueToken, account));
    }

    public boolean isRoyaltyPaid(final Id uniqueToken, final Id account) {
        return royaltiesPaid != null && royaltiesPaid.contains(Pair.of(uniqueToken, account));
    }

    public void includeChange(BalanceChange change) {
        changesSoFar.add(change);
        index(change);
    }

    public List<BalanceChange> creditsInCurrentLevel(Id denom) {
        final List<BalanceChange> ans = new ArrayList<>();
        for (int i = levelStart; i < levelEnd; i++) {
            final var change = changesSoFar.get(i);
            if (change.getAggregatedUnits() > 0L && denom.equals(change.getToken())) {
                ans.add(change);
            }
        }
        return ans;
    }

    public List<BalanceChange> fungibleCreditsInCurrentLevel(Id beneficiary) {
        final List<BalanceChange> ans = new ArrayList<>();
        for (int i = levelStart; i < levelEnd; i++) {
            final var change = changesSoFar.get(i);
            if (change.isForNft()) {
                continue;
            }
            if (beneficiary.equals(change.getAccount()) && change.originalUnits() > 0) {
                ans.add(change);
            }
        }
        return ans;
    }

    public BalanceChange nextAssessableChange() {
        final var numChanges = changesSoFar.size();
        if (nextCandidateChange == numChanges) {
            return null;
        }
        while (nextCandidateChange < numChanges) {
            final var candidate = changesSoFar.get(nextCandidateChange);
            final var changeIsTrigger = couldTriggerCustomFees(candidate);
            if (changeIsTrigger && nextCandidateChange >= levelEnd) {
                levelNo++;
                levelStart = levelEnd;
                levelEnd = numChanges;
            }
            nextCandidateChange++;
            if (changeIsTrigger) {
                return candidate;
            }
        }
        return null;
    }

    public int numChangesSoFar() {
        return changesSoFar.size();
    }

    public BalanceChange changeFor(Id account, Id denom) {
        return indexedChanges.get(Pair.of(account, denom));
    }

    public int getLevelNo() {
        return levelNo;
    }

    List<BalanceChange> getChangesSoFar() {
        return changesSoFar;
    }

    int getLevelStart() {
        return levelStart;
    }

    int getLevelEnd() {
        return levelEnd;
    }

    private void index(BalanceChange change) {
        if (!change.isForNft()) {
            if (change.isForHbar()) {
                if (indexedChanges.put(Pair.of(change.getAccount(), Id.MISSING_ID), change)
                        != null) {
                    throw new IllegalArgumentException("Duplicate balance change :: " + change);
                }
            } else {
                indexedChanges.put(Pair.of(change.getAccount(), change.getToken()), change);
            }
        }
    }

    private boolean couldTriggerCustomFees(BalanceChange candidate) {
        if (candidate.isExemptFromCustomFees()) {
            return false;
        } else {
            return candidate.isForNft()
                    || (!candidate.isForHbar() && candidate.getAggregatedUnits() < 0);
        }
    }
}
