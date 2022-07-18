/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

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

import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaBlockValuesTest {
    HederaBlockValues subject;

    @Mock private MerkleNetworkContext merkleNetworkContext;

    @Test
    void instancing() {
        final var gasLimit = 1L;
        final var blockNo = 10001L;
        final var consTime = Instant.ofEpochSecond(1_234_567L, 890);

        subject = new HederaBlockValues(gasLimit, blockNo, consTime);
        Assertions.assertEquals(gasLimit, subject.getGasLimit());
        Assertions.assertEquals(consTime.getEpochSecond(), subject.getTimestamp());
        Assertions.assertEquals(Optional.of(Wei.ZERO), subject.getBaseFee());
        Assertions.assertEquals(UInt256.ZERO, subject.getDifficultyBytes());
        Assertions.assertEquals(blockNo, subject.getNumber());
    }
}
