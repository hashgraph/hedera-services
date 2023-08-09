package com.swirlds.platform.startup;

import com.swirlds.common.system.NodeId;

import java.util.HashSet;
import java.util.Set;

public record CommandLineArgs(Set<NodeId> localNodesToStart) {

	public static CommandLineArgs parse(final String[] args){
		// This set contains the nodes set by the command line to start, if none are passed, then IP
		// addresses will be compared to determine which node to start
		final Set<NodeId> localNodesToStart = new HashSet<>();

		// Parse command line arguments (rudimentary parsing)
		String currentOption = null;
		if (args != null) {
			for (final String item : args) {
				final String arg = item.trim().toLowerCase();
				if (arg.equals("-local")) {
					currentOption = arg;
				} else if (currentOption != null) {
					try {
						localNodesToStart.add(new NodeId(Integer.parseInt(arg)));
					} catch (final NumberFormatException ex) {
						// Intentionally suppress the NumberFormatException
					}
				}
			}
		}
		return new CommandLineArgs(localNodesToStart);
	}
}
