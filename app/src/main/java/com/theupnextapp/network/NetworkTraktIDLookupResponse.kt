package com.theupnextapp.network

class NetworkTraktIDLookupResponse : ArrayList<NetworkTraktIDLookupResponseItem>()

data class NetworkTraktIDLookupResponseItem(
    val show: NetworkTraktIDLookupShow,
    val type: String
)

data class NetworkTraktIDLookupShow(
    val ids: NetworkTraktAddToWatchlistResponseIds,
    val title: String,
    val year: Int
)

data class Ids(
    val imdb: String,
    val slug: String,
    val tmdb: Int,
    val trakt: Int
)