package com.hedera.services.yahcli.config.domain;

import com.google.common.base.MoreObjects;
import com.hedera.services.yahcli.output.CommonMessages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class NetConfig {
	public static final Integer TRAD_DEFAULT_NODE_ACCOUNT = 3;

	private String defaultPayer;
	private Integer defaultNodeAccount = TRAD_DEFAULT_NODE_ACCOUNT;
	private List<NodeConfig> nodes;

	public String getDefaultPayer() {
		return defaultPayer;
	}

	public void setDefaultPayer(String defaultPayer) {
		this.defaultPayer = defaultPayer;
	}

	public Integer getDefaultNodeAccount() {
		return defaultNodeAccount;
	}

	public void setDefaultNodeAccount(Integer defaultNodeAccount) {
		this.defaultNodeAccount = defaultNodeAccount;
	}

	public List<NodeConfig> getNodes() {
		return nodes;
	}

	public void setNodes(List<NodeConfig> nodes) {
		this.nodes = nodes;
	}

	public String fqDefaultNodeAccount() {
		return CommonMessages.COMMON_MESSAGES.fq(defaultNodeAccount);
	}

	public Map<String, String> toCustomProperties() {
		Map<String, String> customProps = new HashMap<>();
		customProps.put("nodes", nodes.stream().map(NodeConfig::asNodesItem).collect(joining(",")));
		addBootstrapPayerConfig(customProps);
		return customProps;
	}

	private void addBootstrapPayerConfig(Map<String, String> customProps) {
		/* TODO: accommodate mnemonic-based keys (blocked by SpecKey, HapiSpecRegistry not supporting mnemonics) */

	}


	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("defaultPayer", defaultPayer)
				.add("defaultNodeAccount", "0.0." + defaultNodeAccount)
				.add("nodes", nodes)
				.omitNullValues()
				.toString();
	}
}
