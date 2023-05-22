package com.swirlds.platform.poc.moduledefs;

import com.swirlds.platform.poc.framework.Nexus;

public interface Notion extends Nexus {
	void write(String text);
}
