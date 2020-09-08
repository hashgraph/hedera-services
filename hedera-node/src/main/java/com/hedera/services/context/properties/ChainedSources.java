package com.hedera.services.context.properties;

import java.util.HashSet;
import java.util.Set;

public class ChainedSources implements PropertySource {
	private final PropertySource first;
	private final PropertySource second;

	public ChainedSources(PropertySource first, PropertySource second) {
		this.first = first;
		this.second = second;
	}

	@Override
	public boolean containsProperty(String name) {
		return first.containsProperty(name) || second.containsProperty(name);
	}

	@Override
	public Object getProperty(String name) {
		return first.containsProperty(name) ? first.getProperty(name) : second.getProperty(name);
	}

	@Override
	public Set<String> allPropertyNames() {
		var all = new HashSet<>(first.allPropertyNames());
		all.addAll(second.allPropertyNames());
		return all;
	}
}
