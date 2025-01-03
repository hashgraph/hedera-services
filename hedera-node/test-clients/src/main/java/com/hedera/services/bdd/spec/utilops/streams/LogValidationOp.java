/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.parseEd25519NodeAdminKeys;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.APPLICATION_LOG;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.NODE_ADMIN_KEYS_JSON;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.doIfNotInterrupted;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.hapi.node.base.Key;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.support.validators.HgcaaLogValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

/**
 * A {@link UtilOp} that initializes validates the streams produced by the
 * target network of the given {@link HapiSpec}. Note it suffices to validate
 * the streams produced by a single node in the network since at minimum
 * a subsequent log validation will fail.
 */
public class LogValidationOp extends UtilOp {
    public enum Scope {
        ANY_NODE,
        ALL_NODES
    }

    private final Scope scope;
    private final Duration delay;

    public LogValidationOp(@NonNull final Scope scope, @NonNull final Duration delay) {
        this.scope = scope;
        this.delay = delay;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        doIfNotInterrupted(() -> MILLISECONDS.sleep(delay.toMillis()));
        nodesToValidate(spec).forEach(node -> {
            try {
                final var overrideNodeAdminKeys =
                        parseEd25519NodeAdminKeysFrom(node.getExternalPath(NODE_ADMIN_KEYS_JSON)
                                .toAbsolutePath()
                                .normalize()
                                .toString());
                new HgcaaLogValidator(
                                node.getExternalPath(APPLICATION_LOG)
                                        .toAbsolutePath()
                                        .normalize()
                                        .toString(),
                                overrideNodeAdminKeys)
                        .validate();
            } catch (IOException e) {
                Assertions.fail("Could not read log for node '" + node.getName() + "' " + e);
            }
        });
        return false;
    }

    private List<HederaNode> nodesToValidate(@NonNull final HapiSpec spec) {
        return scope == Scope.ANY_NODE
                ? List.of(spec.targetNetworkOrThrow().nodes().getFirst())
                : spec.targetNetworkOrThrow().nodes();
    }

    private static Map<Long, Key> parseEd25519NodeAdminKeysFrom(@NonNull final String loc) {
        try {
            final var json = Files.readString(Paths.get(loc));
            return parseEd25519NodeAdminKeys(json);
        } catch (IOException e) {
            throw new AssertionError("Could not read node admin keys from '" + loc + "' " + e);
        }
    }
}
