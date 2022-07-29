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
package com.hedera.services.state.exports;

import static java.util.Comparator.comparing;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ToStringAccountsExporter implements AccountsExporter {
    private static final Logger log = LogManager.getLogger(ToStringAccountsExporter.class);

    private final NodeLocalProperties nodeLocalProperties;

    @Inject
    public ToStringAccountsExporter(NodeLocalProperties nodeLocalProperties) {
        this.nodeLocalProperties = nodeLocalProperties;
    }

    @Override
    public void toFile(MerkleMap<EntityNum, MerkleAccount> accounts) {
        if (!nodeLocalProperties.exportAccountsOnStartup()) {
            return;
        }
        final var exportLoc = nodeLocalProperties.accountsExportPath();
        try (var writer = Files.newBufferedWriter(Paths.get(exportLoc))) {
            List<EntityNum> keys = new ArrayList<>(accounts.keySet());
            keys.sort(comparing(EntityNum::toGrpcAccountId, HederaLedger.ACCOUNT_ID_COMPARATOR));
            var first = true;
            for (var key : keys) {
                if (!first) {
                    writer.write("\n");
                }
                first = false;
                writer.write(key.toIdString() + "\n");
                writer.write("---\n");
                writer.write(accounts.get(key).toString() + "\n");
            }
        } catch (IOException e) {
            log.warn("Could not export accounts to '{}'", exportLoc, e);
        }
    }
}
