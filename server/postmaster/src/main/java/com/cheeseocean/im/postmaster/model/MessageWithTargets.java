package com.cheeseocean.im.postmaster.model;

import com.cheeseocean.im.common.api.dto.message.Message;

import java.util.List;

/**
 * Pairs a sequenced message with its resolved delivery targets for batch processing.
 */
public record MessageWithTargets(Message message, List<String> targets) {
}
