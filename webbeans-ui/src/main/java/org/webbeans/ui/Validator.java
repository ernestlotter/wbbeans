package org.webbeans.ui;

/** Validates a value; returns an error message (may be a {@code #KEY}) or null when valid. */
public interface Validator<T> {
    String validate(T value);
}
