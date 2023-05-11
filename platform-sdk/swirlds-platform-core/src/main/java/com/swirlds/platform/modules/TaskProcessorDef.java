package com.swirlds.platform.modules;

public record TaskProcessorDef<T>(String moduleName, Class<T> taskType) {
}
