package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;

import java.util.List;

/** Pairs a sequenced message with its resolved delivery targets for batch processing. */
public record MessageWithTargets(SequencedMessage message, List<String> targets) {}
