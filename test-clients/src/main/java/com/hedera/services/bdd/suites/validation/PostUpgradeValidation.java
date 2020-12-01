package com.hedera.services.bdd.suites.validation;

import com.hedera.services.bdd.suites.validation.domain.PuvConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_FAILED;

@Command(name = "puv", mixinStandardHelpOptions = true, version = "0.1.0")
public class PostUpgradeValidation implements Callable<Integer> {
	private static final Logger log = LogManager.getLogger(PostUpgradeValidation.class);

	private static final String CONFIG_LOC = "config.yml";

	private PuvConfig config = null;

	@Option(names = { "-t", "--token" }, description =  "include token service")
	boolean validateTokenService;

	@Parameters(index = "0", description = "network to target")
	String target;

	@Override
	public Integer call() {
		loadConfig();
		if (config == null) {
			return 1;
		}

		if (!config.getNetworks().containsKey(target)) {
			log.error(
					"Config only includes networks {}, not '{}'!",
					new ArrayList<>(config.getNetworks().keySet()),
					target);
			return 1;
		}

		var networkInfo = config.getNetworks().get(target).named(target);
		if (validateTokenService) {
			var tokenPuv = new TokenPuvSuite(networkInfo);
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
		var yamlIn = new Yaml(new Constructor(PuvConfig.class));
		try {
			config = yamlIn.load(Files.newInputStream(Paths.get(CONFIG_LOC)));
		} catch (IOException e) {
			log.error("Could not load '{}'!", CONFIG_LOC, e);
		}
	}
}
