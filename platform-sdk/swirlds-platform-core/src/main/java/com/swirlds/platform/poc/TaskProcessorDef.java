package com.swirlds.platform.poc;

public record TaskProcessorDef<T>(String moduleName, Class<T> taskType) {
}
