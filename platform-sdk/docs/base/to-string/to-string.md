[⇧ Platform Base](../base.md)

# `ToStringBuilder` Utility

Our project utilizes a custom implementation of the `ToStringBuilder` similar to what's available in `org.apache.commons.lang3.builder.ToStringBuilder`. The primary objective behind this is to ensure consistent formatting for our `toString()` methods throughout the application.

## Integration with IntelliJ IDEA

For developers who are familiar with IntelliJ IDEA's code generation methods, we provide templates to seamlessly integrate with our `ToStringBuilder` utility.

### `ToStringBuilder` (swirlds-base) Template:

```java
public java.lang.String toString() {
    return new com.swirlds.base.utility.ToStringBuilder(this)
#foreach ($member in $members)
    .append("$member.name", $member.accessor)
#end
    .toString();
}
```

### `ToStringBuilder` with `appendSuper` (swirlds-base) Template:

```java
public java.lang.String toString() {
    return new com.swirlds.base.utility.ToStringBuilder(this)
    .appendSuper(super.toString())
#foreach ($member in $members)
    .append("$member.name", $member.accessor)
#end
    .toString();
}
```

**Note**: Ensure that your IntelliJ IDEA is set up to interpret and use the above templates accurately.
