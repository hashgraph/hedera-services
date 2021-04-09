package com.hedera.test.extensions;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.lang.reflect.Field;
import java.util.stream.Stream;

public class LogCaptureExtension implements TestInstancePostProcessor, AfterEachCallback {
	LogCaptor injectedCaptor = null;

	@Override
	public void afterEach(ExtensionContext extensionContext) throws Exception {
		if (injectedCaptor != null) {
			injectedCaptor.stopCapture();
		}
	}

	@Override
	public void postProcessTestInstance(Object o, ExtensionContext extensionContext) throws Exception {
		Class<?> testCls = o.getClass();

		Field subject = null, logCaptor = null;

		for (var field : testCls.getDeclaredFields()) {
			if (subject == null && isSubject(field)) {
				subject = field;
			} else if (field.getType().equals(LogCaptor.class)) {
				logCaptor = field;
			}
		}

		if (subject == null) {
			throw new IllegalStateException("The test class has no designated subject");
		}
		if (logCaptor == null) {
			throw new IllegalStateException("The test class has no LogCaptor field");
		}

		injectCaptor(o, subject, logCaptor);
	}

	private void injectCaptor(Object test, Field subject, Field logCaptor) throws IllegalAccessException {
		logCaptor.setAccessible(true);
		injectedCaptor = new LogCaptor(LogManager.getLogger(subject.getType()));
		logCaptor.set(test, injectedCaptor);
	}


	private boolean isSubject(Field field) {
		var annotations = field.getDeclaredAnnotations();
		return Stream.of(annotations).anyMatch(a -> a.annotationType().equals(LoggingSubject.class));
	}
}
