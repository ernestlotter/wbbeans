package org.webbeans.core.json;

/** Implemented by objects that know their JSON form (a Map, List, String, Number, Boolean or null). */
public interface JsonValue {
    Object toJson();
}
