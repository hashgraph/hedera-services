/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.interceptors;

public enum StakeChangeScenario {
    // --- Cases that end with staking to a node ---
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
    // --- Cases that end with staking to an account ---
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
    // --- Cases that end with absent staking ---
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

    public static StakeChangeScenario forCase(final long curStakedId, final long newStakedId) {
        // Ends with staking to a node
        if (newStakedId < 0) {
            if (curStakedId == 0) {
                return FROM_ABSENT_TO_NODE;
            } else if (curStakedId > 0) {
                return FROM_ACCOUNT_TO_NODE;
            } else {
                // We don't care if newStakedId == curStakedId, just run the withdraw/reward logic;
                // it may be necessary in any case if declineReward changed
                return FROM_NODE_TO_NODE;
            }
        } else if (newStakedId > 0) {
            if (curStakedId == 0) {
                return FROM_ABSENT_TO_ACCOUNT;
            } else if (curStakedId > 0) {
                return FROM_ACCOUNT_TO_ACCOUNT;
            } else {
                return FROM_NODE_TO_ACCOUNT;
            }
        } else {
            if (curStakedId == 0) {
                return FROM_ABSENT_TO_ABSENT;
            } else if (curStakedId > 0) {
                return FROM_ACCOUNT_TO_ABSENT;
            } else {
                return FROM_NODE_TO_ABSENT;
            }
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
