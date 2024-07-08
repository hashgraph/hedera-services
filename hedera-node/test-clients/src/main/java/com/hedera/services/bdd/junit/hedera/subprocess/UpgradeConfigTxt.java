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

package com.hedera.services.bdd.junit.hedera.subprocess;

import com.hedera.services.bdd.junit.hedera.ExternalPath;

/**
 * Enumerates the possible sources of the <i>config.txt</i> to use for the next upgrade for a {@link SubProcessNetwork}.
 */
public enum UpgradeConfigTxt {
    /**
     * The <i>config.txt</i> should be generated to match the {@link SubProcessNetwork} nodes.
     */
    IMPLIED_BY_NETWORK_NODES,
    /**
     * The <i>config.txt</i> for each node should be copied from its {@link ExternalPath#UPGRADE_ARTIFACTS_DIR}.
     */
    DAB_GENERATED
}
