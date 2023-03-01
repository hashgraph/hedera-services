/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * Provides a stacked Hedera adapted world view. Utilised by {@link
 * org.hyperledger.besu.evm.frame.MessageFrame} in order to provide a layered view for read/writes
 * of the state during EVM transaction execution
 */
public interface HederaWorldUpdater extends HederaEvmWorldUpdater, WorldUpdater {

    /**
     * Tracks how much Gas should be refunded to the sender account for the TX. SBH price is
     * refunded for the first allocation of new contract storage in order to prevent double charging
     * the client.
     *
     * @return the amount of Gas to refund;
     */
    long getSbhRefund();

    /**
     * Used to keep track of SBH gas refunds between all instances of HederaWorldUpdater. Lower
     * level updaters should add the value of Gas to refund to their respective parent Updater on
     * commit.
     *
     * @param refund the amount of Gas to refund;
     */
    void addSbhRefund(long refund);

    void countIdsAllocatedByStacked(int n);
}
