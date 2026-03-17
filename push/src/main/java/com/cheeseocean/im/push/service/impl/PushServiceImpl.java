package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.push.service.PushService;
import com.cheeseocean.im.push.service.PushStatisticsService;
import org.springframework.stereotype.Service;

@Service
public class PushServiceImpl implements PushService {

    private final PushStatisticsService pushStatisticsService;

    public PushServiceImpl(PushStatisticsService pushStatisticsService) {
        this.pushStatisticsService = pushStatisticsService;
    }

    @Override
    public boolean isPushAvailable(Integer platformId) {
        return platformId != null && platformId > 0;
    }

    @Override
    public PushStatistics getPushStatistics() {
        return pushStatisticsService.getPushStatistics();
    }
}
