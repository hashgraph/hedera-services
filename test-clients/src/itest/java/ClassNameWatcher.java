import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * JUnit Extension for capturing the name of the executing class for use in the Before* and After* static methods.
 */
public class ClassNameWatcher extends TestWatcher {

	private String className = ClassNameWatcher.class.getSimpleName();

	public String getClassName() {
		return className;
	}

	private void setClassName(final String className) {
		this.className = className;
	}

	@Override
	protected void starting(@NotNull final Description description) {
		super.starting(description);
		setClassName(description.getClassName());
	}
}
