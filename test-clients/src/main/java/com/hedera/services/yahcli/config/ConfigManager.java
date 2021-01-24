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
import java.util.Optional;
import java.util.stream.Collectors;

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
		if (yahcli.getNet() == null && global.getDefaultNetwork() == null) {
			fail(String.format(
					"No target network was specified, and no default is available in %s",
					yahcli.getConfigLoc()));
		}
		targetName = Optional.ofNullable(yahcli.getNet()).orElse(global.getDefaultNetwork());
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
