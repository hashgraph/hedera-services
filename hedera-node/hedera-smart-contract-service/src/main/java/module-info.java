/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

/**
 * Provides the classes necessary to manage Hedera Smart Contract Service.
 */
module com.hedera.node.app.service.contract {
    exports com.hedera.node.app.service.contract;

    uses com.hedera.node.app.service.contract.ContractService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires com.hedera.node.hapi;
    requires static transitive com.github.spotbugs.annotations;
}
