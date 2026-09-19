package com.shiv.rally.domain.usecase

import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MatchResult
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent

interface MatcherService {
    suspend fun matchEventToChannels(event: SportEvent, channels: List<IptvChannel>): List<MatchResult>
    suspend fun getRelevantChannelsForEvent(event: SportEvent, channels: List<IptvChannel>): List<RelevantChannel>
}

