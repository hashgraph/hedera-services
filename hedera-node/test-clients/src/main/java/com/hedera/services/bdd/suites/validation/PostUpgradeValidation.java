/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.validation;

import static com.hedera.services.bdd.suites.HapiSuite.FinalOutcome.SUITE_FAILED;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "puv", mixinStandardHelpOptions = true, version = "0.1.0")
public class PostUpgradeValidation implements Callable<Integer> {
    private static final Logger log = LogManager.getLogger(PostUpgradeValidation.class);

    private static final String CONFIG_LOC = "config.yml";

    private TopLevelConfig config = null;

    @Option(
            names = {"-t", "--token"},
            description = "include token service")
    boolean validateTokenService;

    @Option(
            names = {"--no-touch"},
            description = "don't update manifests for created entities",
            defaultValue = "false")
    boolean leaveManifestsAlone;

    @Parameters(index = "0", description = "network to target")
    String target;

    @Override
    public Integer call() {
        loadConfig();
        if (config == null) {
            return 1;
        }

        var miscConfig = new MiscConfig(leaveManifestsAlone);

        if (!config.getNetworks().containsKey(target)) {
            log.error(
                    "Config only includes networks {}, not '{}'!",
                    new ArrayList<>(config.getNetworks().keySet()),
                    target);
            return 1;
        }

        var networkInfo = config.getNetworks().get(target).named(target);
        if (validateTokenService) {
            var tokenPuv = new TokenPuvSuite(miscConfig, networkInfo);
            tokenPuv.initEntitiesIfNeeded();
            var outcome = tokenPuv.runSuiteSync();
            if (outcome == SUITE_FAILED) {
                return 1;
            }
        }

        return 0;
    }

    public static void main(String... args) {
        int rc = new CommandLine(new PostUpgradeValidation()).execute(args);

        System.exit(rc);
    }

    private void loadConfig() {
        var yamlIn = new Yaml(new Constructor(TopLevelConfig.class));
        try {
            config = yamlIn.load(Files.newInputStream(Paths.get(CONFIG_LOC)));
        } catch (IOException e) {
            log.error("Could not load '{}'!", CONFIG_LOC, e);
        }
    }
}
