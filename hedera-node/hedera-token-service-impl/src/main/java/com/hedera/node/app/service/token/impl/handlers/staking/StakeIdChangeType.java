/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.staking;

import static com.hedera.hapi.node.state.token.Account.StakedIdOneOfType.STAKED_ACCOUNT_ID;
import static com.hedera.hapi.node.state.token.Account.StakedIdOneOfType.STAKED_NODE_ID;
import static com.hedera.hapi.node.state.token.Account.StakedIdOneOfType.UNSET;

import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Represents an account's previous and current types of staking election.
 * NODE_TO_NODE can mean same or different node. It doesn't denote if stakedNodeId has changed or not.
 * Similarly, ACCOUNT_TO_ACCOUNT can mean same or different account. It doesn't denote if stakedAccountId has
 * changed or not.
 * ABSENT_TO_ABSENT also means that the account was never staked before and not staked now.
 */
public enum StakeIdChangeType {
    /* ---  Cases ending with staking to a node */
    FROM_ABSENT_TO_NODE {
        @Override
        boolean awardsToNode() {
            return true;
        }
    },
    FROM_ACCOUNT_TO_NODE {
        @Override
        boolean withdrawsFromAccount() {
            return true;
        }

        @Override
        boolean awardsToNode() {
            return true;
        }
    },
    FROM_NODE_TO_NODE {
        @Override
        boolean withdrawsFromNode() {
            return true;
        }

        @Override
        boolean awardsToNode() {
            return true;
        }
    },
    /* --- Cases ending with staking to an account */
    FROM_ABSENT_TO_ACCOUNT {
        @Override
        boolean awardsToAccount() {
            return true;
        }
    },
    FROM_NODE_TO_ACCOUNT {
        @Override
        boolean withdrawsFromNode() {
            return true;
        }

        @Override
        boolean awardsToAccount() {
            return true;
        }
    },
    FROM_ACCOUNT_TO_ACCOUNT {
        @Override
        boolean withdrawsFromAccount() {
            return true;
        }

        @Override
        boolean awardsToAccount() {
            return true;
        }
    },
    /* --- Cases ending with absent staking */
    FROM_ABSENT_TO_ABSENT {},
    FROM_ACCOUNT_TO_ABSENT {
        @Override
        boolean withdrawsFromAccount() {
            return true;
        }
    },
    FROM_NODE_TO_ABSENT {
        @Override
        boolean withdrawsFromNode() {
            return true;
        }
    };

    public static StakeIdChangeType forCase(
            @Nullable final Account currentAccount, @NonNull final Account modifiedAccount) {
        final var curStakedIdCase = currentAccount == null ? UNSET : getCurrentStakedIdCase(currentAccount);
        final var newStakedIdCase = getCurrentStakedIdCase(modifiedAccount);

        // Ends with staking to a node
        if (newStakedIdCase.equals(STAKED_NODE_ID)) {
            if (curStakedIdCase.equals(UNSET)) {
                return FROM_ABSENT_TO_NODE;
            } else if (curStakedIdCase.equals(STAKED_ACCOUNT_ID)) {
                return FROM_ACCOUNT_TO_NODE;
            } else {
                // We don't care if newStakedId == curStakedId, just run the withdraw/reward logic.
                // It may be necessary in any case if declineReward changed
                return FROM_NODE_TO_NODE;
            }
        } else if (newStakedIdCase.equals(STAKED_ACCOUNT_ID)) {
            if (curStakedIdCase.equals(UNSET)) {
                return FROM_ABSENT_TO_ACCOUNT;
            } else if (curStakedIdCase.equals(STAKED_ACCOUNT_ID)) {
                return FROM_ACCOUNT_TO_ACCOUNT;
            } else {
                return FROM_NODE_TO_ACCOUNT;
            }
        } else {
            // We are in the newStakedIdCase == UNSET branch now
            if (curStakedIdCase.equals(UNSET)) {
                return FROM_ABSENT_TO_ABSENT;
            } else if (curStakedIdCase.equals(STAKED_ACCOUNT_ID)) {
                return FROM_ACCOUNT_TO_ABSENT;
            } else {
                return FROM_NODE_TO_ABSENT;
            }
        }
    }

    /**
     * Returns the current stakedId case for the given account.
     * A temporary measure until PBJ supports setting UNSET values for OneOfTypes.
     * When we use sentinel value to reset the stakedAccountId or stakedNodeId,
     * we currently don't get UnSET for stakedId().kind() and this causes issues when determining the case.
     * This method will be removed once PBJ supports setting UNSET values for OneOfTypes(https://github.com/hashgraph/pbj/issues/160).
     * @param account the account to check
     * @return the current stakedId case for the given account
     */
    private static Account.StakedIdOneOfType getCurrentStakedIdCase(final Account account) {
        final var kind = account.stakedId().kind();
        if (kind.equals(STAKED_NODE_ID)) {
            return account.stakedNodeIdOrElse(-1L) == -1L ? UNSET : STAKED_NODE_ID;
        } else if (kind.equals(STAKED_ACCOUNT_ID)) {
            return account.stakedAccountId() == null ? UNSET : STAKED_ACCOUNT_ID;
        } else {
            return UNSET;
        }
    }

    boolean withdrawsFromNode() {
        return false;
    }

    boolean withdrawsFromAccount() {
        return false;
    }

    boolean awardsToNode() {
        return false;
    }

    boolean awardsToAccount() {
        return false;
    }
}
