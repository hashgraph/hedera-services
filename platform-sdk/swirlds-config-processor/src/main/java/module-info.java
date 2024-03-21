module com.swirlds.config.processor {
    provides javax.annotation.processing.Processor with
            com.swirlds.config.processor.ConfigDataAnnotationProcessor;

    requires com.swirlds.config.api;
    requires com.squareup.javapoet;
    requires java.compiler;
    requires transitive org.antlr.antlr4.runtime;
    requires static transitive com.github.spotbugs.annotations;
    requires static com.google.auto.service;
}
