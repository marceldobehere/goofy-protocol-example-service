package com.masl.goofy_protocol_core.crypto.exceptions;

public class PubSplitKeyNotFound extends Exception {
    public final String handle;

    public PubSplitKeyNotFound(String handle) {
        super("Public Split key for \"" + handle + "\" not found.");
        this.handle = handle;
    }
}
