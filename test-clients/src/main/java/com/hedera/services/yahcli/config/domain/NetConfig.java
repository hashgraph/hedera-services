package com.hedera.services.yahcli.config.domain;

import com.google.common.base.MoreObjects;

import java.util.List;

public class NetConfig {
	private String defaultPayer;
	private String defaultNodeId;
	private List<NodeConfig> nodes;

	public String getDefaultPayer() {
		return defaultPayer;
	}

	public void setDefaultPayer(String defaultPayer) {
		this.defaultPayer = defaultPayer;
	}

	public String getDefaultNodeId() {
		return defaultNodeId;
	}

	public void setDefaultNodeId(String defaultNodeId) {
		this.defaultNodeId = defaultNodeId;
	}

	public List<NodeConfig> getNodes() {
		return nodes;
	}

	public void setNodes(List<NodeConfig> nodes) {
		this.nodes = nodes;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("defaultPayer", defaultPayer)
				.add("defaultNodeId", defaultNodeId)
				.add("nodes", nodes)
				.omitNullValues()
				.toString();
	}
}
