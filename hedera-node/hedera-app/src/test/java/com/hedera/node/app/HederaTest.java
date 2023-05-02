/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

final class HederaTest {
    // Constructor: null registry throws
    // Constructor: bootstrap props throws
    // Constructor: Null version throws (pass the version in)
    // Constructor: #getSoftwareVersion returns the supplied version
    // Constructor: Verify constructable registry is used for registering MerkleHederaState (and for services)
    // Constructor: Verify constructable registry that throws is handled (for services and for MerkleHederaState)

    // TRY: Sending customized bootstrap props
    // TRY: Check logs?

    // newState: Called when there is no saved state, and when there is a saved state

    // onStateInitialized: Called when there is no saved state, and when there is a saved state
    // onStateInitialized: deserializedVersion is < current version
    // onStateInitialized: deserializedVersion is = current version
    // onStateInitialized: deserializedVersion is > current version
    // onStateInitialized: called for genesis
    // onStateInitialized: called for restart
    // onStateInitialized: called for reconnect (no error)
    // onStateInitialized: called for event stream recovery (no error)

    // onMigrate ONLY CALLED if deserializedVersion < current version or if genesis

    // init: Try to init with the system with something other than UTF-8 as the native charset (throws)
    // init: Try to init with sha384 not available (throws)
    // init: ensure JVM is set to UTF-8 after init
    // init: validateLedgerState ....
    // init: configurePlatform ....
    // init: exportAccountsIfDesired ....

    // run: start grpc server for old port
    // run: start grpc server for new port

    // genesis: onMigrate is called
    // genesis: seqStart comes from bootstrap props
    // genesis: results of createSpecialGenesisChildren
    // genesis: ... dagger ...
    // genesis: version info is saved in state (and committed)
    // genesis: other stuff?....
    // genesis: updateStakeDetails....?
    // genesis: markPostUpradeScanStatus?....

    // createSpecialGenesisChildren: What if seqStart is < 0?
    // createSpecialGenesisChildren: other children...?

    // restart: update if needed
    // restart: dagger ....?
    // restart: housekeeping? freeze? etc.

    // rebuild aliases?
}
