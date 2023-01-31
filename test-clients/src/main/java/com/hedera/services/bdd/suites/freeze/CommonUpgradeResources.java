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
package com.hedera.services.bdd.suites.freeze;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

public final class CommonUpgradeResources {
    public static final String DEFAULT_UPGRADE_FILE_ID = "0.0.150";
    public static final String DEFAULT_UPGRADE_FILE_PATH = "upgrade.zip";
    public static final int DEFAULT_APPENDS_PER_BURST = 512;
    public static final int DEFAULT_UPGRADE_DELAY = 2;

    private static final PerfTestLoadSettings settings = new PerfTestLoadSettings();

    public static HapiSpecOperation[] initializeSettings() {
        final HapiSpecOperation[] ops = {
            withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
            logIt(ignore -> settings.toString())
        };
        return ops;
    }

    public static String upgradeFileId() {
        return settings.getProperty("upgradeFileId", DEFAULT_UPGRADE_FILE_ID);
    }

    public static String upgradeFilePath() {
        return settings.getProperty("upgradeFilePath", DEFAULT_UPGRADE_FILE_PATH);
    }

    public static int upgradeFileAppendsPerBurst() {
        return settings.getIntProperty("upgradeFileAppendsPerBurst", DEFAULT_APPENDS_PER_BURST);
    }

    public static byte[] upgradeFileHash() {
        try {
            final var fileBytes = Files.readAllBytes(Paths.get(upgradeFilePath()));
            return noThrowSha384HashOf(fileBytes);
        } catch (final InvalidPathException | IOException ignore) {
        }
        return new byte[0];
    }

    public static int upgradeDelay() {
        return settings.getIntProperty("upgradeDelay", DEFAULT_UPGRADE_DELAY);
    }
}
