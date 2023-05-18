package com.swirlds.platform.poc.infrastructure;

public interface Nexus extends Component{
	@Override
	default ComponentType getType() {
		return ComponentType.NEXUS;
	}
}
