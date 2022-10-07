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
package com.hedera.services.state.forensics;

import com.hedera.services.ServicesState;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class HashLogger {
    private static final Logger log = LogManager.getLogger(HashLogger.class);

    private static final String UNAVAILABLE_VIRTUAL_MAP_HASH = "<N/A>";

    @Inject
    public HashLogger() {
        // Default Constructor
    }

    public void logHashesFor(ServicesState state) {
        final var summaryTpl =
                """
				[SwirldState Hashes]
				Overall                :: {}
				Accounts               :: {}
				Storage                :: {}
				Topics                 :: {}
				Tokens                 :: {}
				TokenAssociations      :: {}
				SpecialFiles           :: {}
				ScheduledTxs           :: {}
				NetworkContext         :: {}
				AddressBook            :: {}
				RecordsRunningHashLeaf :: {}
				  ↪ Running hash       :: {}
				UniqueTokens           :: {}
				ContractStorage        :: {}""";
        log.info(
                summaryTpl,
                state.getHash(),
                state.accounts().getHash(),
                UNAVAILABLE_VIRTUAL_MAP_HASH,
                state.topics().getHash(),
                state.tokens().getHash(),
                state.tokenAssociations().getHash(),
                state.specialFiles().getHash(),
                state.scheduleTxs().getHash(),
                state.networkCtx().getHash(),
                state.addressBook().getHash(),
                state.runningHashLeaf().getHash(),
                state.runningHashLeaf().getRunningHash().getHash(),
                state.uniqueTokens().getHash(),
                UNAVAILABLE_VIRTUAL_MAP_HASH);
    }
}
