## Handling of null values

In the code we should annotate all parameters of methods and constructors with the `@Nullable` or `@NonNull`
annotations. Next to this all methods that define a return value should be annotated by one of the 2 annotations. This
is done to make the code more readable and to avoid null pointer exceptions.

### Null annotations

We use `edu.umd.cs.findbugs.annotations.Nullable` and `edu.umd.cs.findbugs.annotations.NonNull` annotations. Maybe we
will create our own annotations in the future but for now only the 2 annotations from the findbugs library must be used.
This has been decided based on an analysis of the most used annotations in the open source
community (https://github.com/hashgraph/hedera-services/issues/4234).

### IntelliJ IDEA support

In IntelliJ IDEA you can enable the null annotations support by activating the `@Notnull/@Nullable problems`Inspections.
Next to this the `Configure Annotations` button in the `compiler` section of the settings let you define exactly the 2
given annotations.

### Gradle dependency

The annotations should not be available in the runtime classpath. This is why the dependency must be defined
as `compileOnly`:

```
compileOnly(libs.spotbugs.annotations)
```

### Java Module

The `module-info.java` file should contain the following line:

```
requires static edu.umd.cs.findbugs.annotations;
```
