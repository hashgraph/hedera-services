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
package com.hedera.services.store.models;

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

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.store.contracts.precompile.codec.KeyValue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class KeyValueTest {

    @Test
    void hashCodeDiscriminates() {
        final var aKeyValue =
                new KeyValue(false, parentContractAddress, new byte[] {}, new byte[] {}, null);
        final var bKeyValue =
                new KeyValue(true, parentContractAddress, new byte[] {}, new byte[] {}, null);
        final var cKeyValue =
                new KeyValue(false, null, new byte[] {}, new byte[] {}, parentContractAddress);
        final var dKeyValue =
                new KeyValue(false, parentContractAddress, new byte[] {}, new byte[] {}, null);

        assertNotEquals(bKeyValue.hashCode(), aKeyValue.hashCode());
        assertNotEquals(cKeyValue.hashCode(), aKeyValue.hashCode());
        assertEquals(dKeyValue.hashCode(), aKeyValue.hashCode());
    }

    @Test
    void equalsDiscriminates() {
        final var aKeyValue =
                new KeyValue(false, parentContractAddress, new byte[] {}, new byte[] {}, null);
        final var bKeyValue =
                new KeyValue(true, parentContractAddress, new byte[] {}, new byte[] {}, null);
        final var cKeyValue =
                new KeyValue(false, null, new byte[] {}, new byte[] {}, parentContractAddress);
        final var dKeyValue =
            new KeyValue(false, null, new byte[] {}, new byte[] {}, contractAddr);
        final var eKeyValue =
            new KeyValue(false, parentContractAddress, new byte[] {}, new byte[] {}, null);

        assertNotEquals(bKeyValue, aKeyValue);
        assertNotEquals(cKeyValue, aKeyValue);
        assertNotEquals(dKeyValue, aKeyValue);
        assertEquals(eKeyValue, aKeyValue);
        assertNotEquals(aKeyValue, new Object());
        assertNotEquals(null, aKeyValue);
        assertNotEquals(1, aKeyValue);
        assertEquals(aKeyValue, aKeyValue);
    }

    @Test
    void toStringWorks() {
        final var keyValue =
                new KeyValue(false, parentContractAddress, new byte[] {}, new byte[] {}, null);

        assertEquals(
                "KeyValue{"
                        + "inheritAccountKey="
                        + false
                        + ", contractId="
                        + parentContractAddress
                        + ", ed25519="
                        + Arrays.toString(new byte[] {})
                        + ", ECDSA_secp256k1="
                        + Arrays.toString(new byte[] {})
                        + ", delegatableContractId="
                        + "null"
                        + '}',
                keyValue.toString());
    }
}
