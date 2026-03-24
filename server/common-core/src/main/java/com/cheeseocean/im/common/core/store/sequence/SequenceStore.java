package com.cheeseocean.im.common.core.store.sequence;

public interface SequenceStore {

    SequenceRange reserve(String conversationId, int size);
}
