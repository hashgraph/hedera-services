package com.hedera.services.bdd.suites.validation.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public class NetworkInfo {
	private static final Long DEFAULT_BOOTSTRAP_ACCOUNT = 2L;
	private static final Long DEFAULT_STARTUP_NODE_ACCOUNT = 3L;
	private static final String DEFAULT_NAME = "default";
	private static final String DEFAULT_BOOTSTRAP_PEM_KEY_LOC_TPL = "%s/keys/account%d.pem";
	private static final String DEFAULT_BOOTSTRAP_PEM_KEY_PASSPHRASE = "swirlds";
	private static final String DEFAULT_PERSISTENT_ENTITIES_DIR_TPL = "%s-persistence";

	private Long bootstrapAccount = DEFAULT_BOOTSTRAP_ACCOUNT;
	private Long startupNodeAccount = DEFAULT_STARTUP_NODE_ACCOUNT;
	private String name = DEFAULT_NAME;
	private String bootstrapPemKeyLoc;
	private String persistentEntitiesDir;
	private String bootstrapPemKeyPassphrase = DEFAULT_BOOTSTRAP_PEM_KEY_PASSPHRASE;
	private List<Node> nodes;

	public NetworkInfo named(String name) {
		this.name = name;
		return this;
	}

	public Map<String, String> toCustomProperties() {
		return Map.of(
				"nodes", nodes.stream().map(Object::toString).collect(joining(",")),
				"default.payer.pemKeyLoc", effBootstrapPemKeyLoc(),
				"default.payer.pemKeyPassphrase", bootstrapPemKeyPassphrase,
				"persistentEntities.dir.path", effPersistentEntitiesDir(),
				"default.node", String.format("0.0.%d", startupNodeAccount)
		);
	}

	private String effBootstrapPemKeyLoc() {
		return Optional.ofNullable(bootstrapPemKeyLoc)
				.orElseGet(() -> String.format(
						DEFAULT_BOOTSTRAP_PEM_KEY_LOC_TPL,
						effPersistentEntitiesDir(),
						bootstrapAccount));
	}

	private String effPersistentEntitiesDir() {
		return Optional.ofNullable(persistentEntitiesDir)
				.orElseGet(() -> String.format(DEFAULT_PERSISTENT_ENTITIES_DIR_TPL, name));
	}

	public Long getStartupNodeAccount() {
		return startupNodeAccount;
	}

	public void setStartupNodeAccount(Long startupNodeAccount) {
		this.startupNodeAccount = startupNodeAccount;
	}

	public String getPersistentEntitiesDir() {
		return persistentEntitiesDir;
	}

	public void setPersistentEntitiesDir(String persistentEntitiesDir) {
		this.persistentEntitiesDir = persistentEntitiesDir;
	}

	public List<Node> getNodes() {
		return nodes;
	}

	public void setNodes(List<Node> nodes) {
		this.nodes = nodes;
	}

	public String getBootstrapPemKeyLoc() {
		return bootstrapPemKeyLoc;
	}

	public void setBootstrapPemKeyLoc(String bootstrapPemKeyLoc) {
		this.bootstrapPemKeyLoc = bootstrapPemKeyLoc;
	}

	public String getBootstrapPemKeyPassphrase() {
		return bootstrapPemKeyPassphrase;
	}

	public void setBootstrapPemKeyPassphrase(String bootstrapPemKeyPassphrase) {
		this.bootstrapPemKeyPassphrase = bootstrapPemKeyPassphrase;
	}

	public Long getBootstrapAccount() {
		return bootstrapAccount;
	}

	public void setBootstrapAccount(Long bootstrapAccount) {
		this.bootstrapAccount = bootstrapAccount;
	}
}
