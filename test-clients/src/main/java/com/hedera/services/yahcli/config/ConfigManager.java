package com.hedera.services.yahcli.config;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.domain.GlobalConfig;
import com.hedera.services.yahcli.config.domain.NetConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.yahcli.config.ConfigUtils.asId;
import static com.hedera.services.yahcli.config.ConfigUtils.isLiteral;
import static java.util.stream.Collectors.toList;

public class ConfigManager {
	private final Yahcli yahcli;
	private final GlobalConfig global;

	private String defaultPayer;
	private String targetName;
	private NetConfig targetNet;

	public ConfigManager(Yahcli yahcli, GlobalConfig global) {
		this.yahcli = yahcli;
		this.global = global;
	}

	public static ConfigManager from(Yahcli yahcli) throws IOException {
		var yamlLoc = yahcli.getConfigLoc();
		var yamlIn = new Yaml(new Constructor(GlobalConfig.class));
		try (InputStream fin = Files.newInputStream(Paths.get(yamlLoc))) {
			GlobalConfig globalConfig = yamlIn.load(fin);
			return new ConfigManager(yahcli, globalConfig);
		}
	}

	public Map<String, String> asSpecConfig() {
		assertNoMissingDefaults();
		var specConfig = targetNet.toSpecProperties();
		if (useFixedFee()) {
			specConfig.put("fees.fixedOffer", String.valueOf(useFixedFee()));
			specConfig.put("fees.useFixedOffer", "true");
		}
		var payerId = asId(defaultPayer);
		if (isLiteral(payerId)) {
			addPayerConfig(specConfig, payerId);
		} else {
			fail("Named accounts not yet supported!");
		}
		return specConfig;
	}

	private void addPayerConfig(Map<String, String> specConfig, String payerId) {
		specConfig.put("default.payer", payerId);
		var optKeyFile = ConfigUtils.keyFileFor(keysLoc(), "account" + defaultPayer);
		if (optKeyFile.isEmpty()) {
			fail(String.format("No key available for account %s!", payerId));
		}
		var keyFile = optKeyFile.get();
		if (keyFile.getAbsolutePath().endsWith("pem")) {
			var optPassFile = ConfigUtils.passFileFor(keyFile);
			if (optPassFile.isEmpty()) {
				fail(String.format("No password file available for PEM %s!", keyFile.getName()));
			}
			try {
				var pass = Files.readString(optPassFile.get().toPath());
				specConfig.put("default.payer.pemKeyLoc", keyFile.getPath());
				specConfig.put("default.payer.pemKeyPassphrase", pass.trim());
			} catch (IOException e) {
				fail(String.format(
						"Password file inaccessible for PEM %s ('%s')!",
						keyFile.getName(),
						e.getMessage()));
			}
		} else {
			try {
				var mnemonic = Files.readString(keyFile.toPath());
				specConfig.put("default.payer.mnemonic", mnemonic);
			} catch (IOException e) {
				fail(String.format("Mnemonic file %s is inaccessible!", keyFile.getPath()));
			}
		}
	}

	private String keysLoc() {
		return targetName + "/keys";
	}

	public boolean useFixedFee() {
		return yahcli.getFixedFee()	!= Yahcli.NO_FIXED_FEE;
	}

	public long fixedFee() {
		return yahcli.getFixedFee();
	}

	public void assertNoMissingDefaults() {
		assertTargetNetIsKnown();
		assertDefaultPayerIsKnown();
	}

	private void assertDefaultPayerIsKnown() {
		if (yahcli.getPayer() == null && targetNet.getDefaultPayer() == null) {
			fail(String.format(
					"No payer was specified, and no default is available in %s for network '%s'",
					yahcli.getConfigLoc(),
					targetName));
		}
		defaultPayer = Optional.ofNullable(yahcli.getPayer()).orElse(targetNet.getDefaultPayer());
	}

	private void assertTargetNetIsKnown() {
		if (yahcli.getNet() == null
				&& global.getDefaultNetwork() == null
				&& global.getNetworks().size() != 1) {
			fail(String.format(
					"No target network was specified, and no default from %d networks is given in %s",
					global.getNetworks().size(),
					yahcli.getConfigLoc()));
		}
		targetName = Optional.ofNullable(yahcli.getNet())
				.orElse(Optional.ofNullable(global.getDefaultNetwork())
						.orElse(global.getNetworks().keySet().iterator().next()));
		if (!global.getNetworks().containsKey(targetName)) {
			fail(String.format(
					"Target network '%s' not configured in %s, only %s are known",
					targetName,
					yahcli.getConfigLoc(),
					global.getNetworks().keySet().stream()
							.map(s -> "'" + s + "'")
							.collect(toList())));
		}
		targetNet = global.getNetworks().get(targetName);
	}

	private void fail(String msg) {
		throw new CommandLine.ParameterException(yahcli.getSpec().commandLine(), msg);
	}

	public String getDefaultPayer() {
		return defaultPayer;
	}

	public String getTargetName() {
		return targetName;
	}

	public NetConfig getTargetNet() {
		return targetNet;
	}
}
