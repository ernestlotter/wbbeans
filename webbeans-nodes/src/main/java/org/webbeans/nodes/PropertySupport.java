package org.webbeans.nodes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Ready-made {@link Node.Property} implementations. */
public final class PropertySupport {

    private PropertySupport() {
    }

    /** A property computed by {@link #getValue()}; override it. */
    public abstract static class ReadOnly<T> extends Node.Property<T> {
        public ReadOnly(String name, Class<T> type, String displayName, String shortDescription) {
            super(type);
            setName(name);
            setDisplayName(displayName);
            setShortDescription(shortDescription);
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public void setValue(T value) throws IllegalAccessException {
            throw new IllegalAccessException("Read-only property " + getName());
        }
    }

    /** A property with both accessors; override both. */
    public abstract static class ReadWrite<T> extends Node.Property<T> {
        public ReadWrite(String name, Class<T> type, String displayName, String shortDescription) {
            super(type);
            setName(name);
            setDisplayName(displayName);
            setShortDescription(shortDescription);
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return true;
        }
    }

    /** A property backed by getter/setter methods of a bean (by name; the setter may be null). */
    public static class Reflection<T> extends Node.Property<T> {
        private final Object bean;
        private final Method getter;
        private final Method setter;

        public Reflection(Object bean, Class<T> type, String getter, String setter) throws NoSuchMethodException {
            super(type);
            this.bean = bean;
            this.getter = getter == null ? null : bean.getClass().getMethod(getter);
            this.setter = setter == null ? null : bean.getClass().getMethod(setter, type);
        }

        @Override
        public boolean canRead() {
            return getter != null;
        }

        @Override
        public boolean canWrite() {
            return setter != null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T getValue() throws IllegalAccessException, InvocationTargetException {
            if (getter == null) {
                throw new IllegalAccessException("No getter for " + getName());
            }
            return (T) getter.invoke(bean);
        }

        @Override
        public void setValue(T value) throws IllegalAccessException, InvocationTargetException {
            if (setter == null) {
                throw new IllegalAccessException("No setter for " + getName());
            }
            setter.invoke(bean, value);
        }
    }

    /** The node's name as a writable property (rename). */
    public static final class Name extends Node.Property<String> {
        private final Node node;

        public Name(Node node, String displayName, String shortDescription) {
            super(String.class);
            this.node = node;
            setName("name");
            setDisplayName(displayName);
            setShortDescription(shortDescription);
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return node.canRename();
        }

        @Override
        public String getValue() {
            return node.getName();
        }

        @Override
        public void setValue(String value) {
            node.setName(value);
        }
    }
}
