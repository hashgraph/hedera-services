package com.swirlds.platform.poc.framework;

public interface Nexus extends Component{
	@Override
	default ComponentType getType() {
		return ComponentType.NEXUS;
	}
}
