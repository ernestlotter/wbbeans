package org.webbeans.core.modules;

/** Why a module could not be enabled. Reported, never thrown. */
public final class Problem {

    public enum Kind {
        /** A module dependency names a module that was not found. */
        MISSING_DEPENDENCY,
        /** The dependency exists but its version does not satisfy the requirement. */
        VERSION_MISMATCH,
        /** A required capability token is not provided by any enabled module or the platform. */
        MISSING_REQUIRES,
        /** A dependency (transitively) could not be enabled. */
        DEPENDENCY_DISABLED,
        /** The module participates in a dependency cycle. */
        CYCLE,
        /** Another module with the same code name and a higher version is present. */
        DUPLICATE,
        /** The module's {@code ModuleInstall.validate()} vetoed enabling, or class loading failed. */
        INSTALL_FAILED
    }

    private final Kind kind;
    private final String message;
    private final Throwable cause;

    public Problem(Kind kind, String message) {
        this(kind, message, null);
    }

    public Problem(Kind kind, String message, Throwable cause) {
        this.kind = kind;
        this.message = message;
        this.cause = cause;
    }

    public Kind getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return kind + ": " + message;
    }
}
