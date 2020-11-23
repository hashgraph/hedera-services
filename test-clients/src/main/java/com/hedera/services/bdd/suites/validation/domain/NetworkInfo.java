package com.hedera.services.bdd.suites.validation.domain;

import com.hedera.services.bdd.spec.props.NodeConnectInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public class NetworkInfo {
	private static final String DEFAULT_STARTUP_NODE_ACCOUNT = "0.0.3";
	private static final String DEFAULT_PERSISTENT_ENTITIES_DIR_TPL = "%s-persistence";

	private String startupNodeAccount;
	private String persistentEntitiesDir;
	private List<Node> nodes;

	public Map<String, String> toCustomProperties() {
		return Map.of(
				"nodes", nodes.stream().map(Object::toString).collect(joining(",")),
				"persistentEntities.dir.path", persistentEntitiesDir,
				"default.node", startupNodeAccount
		);
	}

	public String getStartupNodeAccount() {
		return startupNodeAccount;
	}

	public void setStartupNodeAccount(String startupNodeAccount) {
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
}
