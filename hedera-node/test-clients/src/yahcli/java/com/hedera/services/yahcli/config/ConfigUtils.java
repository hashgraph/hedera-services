/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.config;

import static com.hedera.services.bdd.spec.utilops.inventory.AccessoryUtils.keyFileAt;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeesAndRatesProvider;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.inventory.AccessoryUtils;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ConfigUtils {
    private ConfigUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static String asId(String entity) {
        try {
            int number = Integer.parseInt(entity);
            return "0.0." + number;
        } catch (NumberFormatException ignore) {
        }
        return entity;
    }

    public static boolean isLiteral(String entity) {
        return entity.startsWith("0.0.");
    }

    public static Optional<File> keyFileFor(String keysLoc, String typedNum) {
        return keyFileAt(keysLoc + File.separator + typedNum);
    }

    public static File uncheckedKeyFileFor(String keysLoc, String typedNum) {
        final var fileLoc = keysLoc + File.separator + typedNum;
        final var file = keyFileAt(fileLoc);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No such key file " + fileLoc);
        } else {
            return file.get();
        }
    }

    public static void ensureDir(String loc) {
        File f = new File(loc);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new IllegalStateException("Failed to create directory: " + f.getAbsolutePath());
            }
        }
    }

    public static ConfigManager configFrom(Yahcli yahcli) throws IOException {
        System.out.println("Log level is " + yahcli.getLogLevel());
        AccessoryUtils.setLogLevels(yahcli.getLogLevel(), YAHCLI_LOGGING_CLASSES);
        var config = ConfigManager.from(yahcli);
        config.assertNoMissingDefaults();
        COMMON_MESSAGES.printGlobalInfo(config);
        return config;
    }

    public static List<Class<?>> YAHCLI_LOGGING_CLASSES = List.of(
            BalanceSuite.class,
            RekeySuite.class,
            SysFileUploadSuite.class,
            SysFileDownloadSuite.class,
            SchedulesValidationSuite.class,
            FreezeHelperSuite.class,
            UpgradeHelperSuite.class,
            CostOfEveryThingSuite.class,
            MapPropertySource.class,
            HapiApiClients.class,
            FeesAndRatesProvider.class,
            HapiQueryOp.class,
            HapiTxnOp.class,
            HapiGetFileContents.class,
            HapiSpec.class,
            VersionInfoSpec.class,
            SendSuite.class,
            ScheduleSuite.class,
            CreateSuite.class,
            SpecialFileHashSuite.class,
            StakeSuite.class,
            StakeSetupSuite.class,
            CustomSpecAssert.class);
}
