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
package com.hedera.services.yahcli.config;

import static com.hedera.services.bdd.spec.persistence.SpecKey.readFirstKpFromPem;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.FeesAndRatesProvider;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public class ConfigUtils {
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

    public static Optional<File> keyFileAt(String sansExt) {
        var pemFile = Paths.get(sansExt + ".pem").toFile();
        if (pemFile.exists()) {
            return Optional.of(pemFile);
        }

        var wordsFile = Paths.get(sansExt + ".words").toFile();
        if (wordsFile.exists()) {
            return Optional.of(wordsFile);
        }

        return Optional.empty();
    }

    public static Optional<File> passFileFor(File pemFile) {
        var absPath = pemFile.getAbsolutePath();
        var passFile = new File(absPath.replace(".pem", ".pass"));
        return passFile.exists() ? Optional.of(passFile) : Optional.empty();
    }

    public static void ensureDir(String loc) {
        File f = new File(loc);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new IllegalStateException(
                        "Failed to create directory: " + f.getAbsolutePath());
            }
        }
    }

    public static Optional<String> promptForPassphrase(
            String pemLoc, String prompt, int maxAttempts) {
        var pemFile = new File(pemLoc);
        String fullPrompt = prompt + ": ";
        char[] passphrase;
        while (maxAttempts-- > 0) {
            passphrase = readCandidate(fullPrompt);
            var asString = new String(passphrase);
            if (unlocks(pemFile, asString)) {
                return Optional.of(asString);
            } else {
                if (maxAttempts > 0) {
                    System.out.println(
                            "Sorry, that isn't it! (Don't worry, still "
                                    + maxAttempts
                                    + " attempts remaining.)");
                } else {
                    return Optional.empty();
                }
            }
        }
        throw new AssertionError("Impossible!");
    }

    static boolean unlocks(File keyFile, String passphrase) {
        try {
            readFirstKpFromPem(keyFile, passphrase);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static char[] readCandidate(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        if (System.console() != null) {
            return System.console().readPassword();
        } else {
            var reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                return reader.readLine().toCharArray();
            } catch (IOException e) {
                return new char[0];
            }
        }
    }

    public static ConfigManager configFrom(Yahcli yahcli) throws IOException {
        System.out.println("Log level is " + yahcli.getLogLevel());
        setLogLevels(yahcli.getLogLevel());
        var config = ConfigManager.from(yahcli);
        config.assertNoMissingDefaults();
        COMMON_MESSAGES.printGlobalInfo(config);
        return config;
    }

    public static void setLogLevels(Level logLevel) {
        List.of(
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
                        HapiApiSpec.class,
                        VersionInfoSpec.class,
                        SendSuite.class,
                        CreateSuite.class,
                        SpecialFileHashSuite.class,
                        CustomSpecAssert.class)
                .forEach(cls -> setLogLevel(cls, logLevel));
    }

    private static void setLogLevel(Class<?> cls, Level logLevel) {
        ((org.apache.logging.log4j.core.Logger) LogManager.getLogger(cls)).setLevel(logLevel);
    }
}
