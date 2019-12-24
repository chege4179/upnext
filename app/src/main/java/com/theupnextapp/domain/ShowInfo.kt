package com.theupnextapp.domain

data class ShowInfo(
    var id : Int,
    val mediumImageUrl: String?,
    val originalImageUrl: String?,
    val genres: String?,
    val language: String?,
    val averageRating: String?,
    val airDays: String?,
    val time: String?,
    val nextEpisodeId: Int?,
    val nextEpisodeAirdate: String?,
    val nextEpisodeAirstamp: String?,
    val nextEpisodeAirtime: String?,
    val nextEpisodeMediumImageUrl: String?,
    val nextEpisodeOriginalImageUrl: String?,
    val nextEpisodeName: String?,
    val nextEpisodeNumber: String?,
    val nextEpisodeRuntime: String?,
    val nextEpisodeSeason: String?,
    val nextEpisodeSummary: String?,
    val nextEpisodeUrl: String?,
    val previousEpisodeId: Int?,
    val previousEpisodeAirdate: String?,
    val previousEpisodeAirstamp: String?,
    val previousEpisodeAirtime: String?,
    val previousEpisodeMediumImageUrl: String?,
    val previousEpisodeOriginalImageUrl: String?,
    val previousEpisodeName: String?,
    val previousEpisodeNumber: String?,
    val previousEpisodeRuntime: String?,
    val previousEpisodeSeason: String?,
    val previousEpisodeSummary: String?,
    val previousEpisodeUrl: String?
)