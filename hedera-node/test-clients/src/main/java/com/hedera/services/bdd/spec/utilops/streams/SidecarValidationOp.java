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

package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.services.bdd.junit.hedera.NodeSelector.byNodeId;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link UtilOp} that initializes a {@link SidecarWatcher} for the
 * given {@link HapiSpec} and registers it with
 * {@link HapiSpec#setSidecarWatcher(SidecarWatcher)}.
 */
public class SidecarValidationOp extends UtilOp {
    private static final Logger log = LogManager.getLogger(SidecarValidationOp.class);

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var streamsLoc = spec.streamsLoc(byNodeId(0));
        spec.setSidecarWatcher(new SidecarWatcher(streamsLoc));
        log.info("Watching for sidecar files at {}", streamsLoc);
        return false;
    }
}
