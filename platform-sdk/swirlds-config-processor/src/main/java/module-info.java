module com.swirlds.config.processor {
    provides javax.annotation.processing.Processor with
            com.swirlds.config.processor.ConfigDataAnnotationProcessor;

    requires static com.google.auto.service;
    requires java.compiler;
    requires transitive com.swirlds.config.api;
    requires static com.github.spotbugs.annotations;
    requires com.squareup.javapoet;
    requires org.antlr.antlr4.runtime;
}
