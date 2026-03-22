package com.cheeseocean.im.common.api.connection;

import com.cheeseocean.im.common.core.auth.KickoffCommand;

public interface KickoffCommandDubboService {

    void kickoffBySession(KickoffCommand command);

    void kickoffByUser(KickoffCommand command);

    void kickoffByDevice(KickoffCommand command);
}
