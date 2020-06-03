package com.theupnextapp.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.theupnextapp.BuildConfig
import com.theupnextapp.database.DatabaseTraktHistory
import com.theupnextapp.database.DatabaseTraktWatchlist
import com.theupnextapp.database.UpnextDatabase
import com.theupnextapp.database.asDomainModel
import com.theupnextapp.domain.*
import com.theupnextapp.network.models.tvmaze.NetworkShowSearchResponse
import com.theupnextapp.network.TraktNetwork
import com.theupnextapp.network.TvMazeNetwork
import com.theupnextapp.network.models.trakt.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber

class TraktRepository(private val database: UpnextDatabase) {

    val traktWatchlist: LiveData<List<TraktWatchlist>> =
        Transformations.map(database.upnextDao.getTraktWatchlist()) {
            it.asDomainModel()
        }

    val traktHistory: LiveData<List<TraktHistory>> =
        Transformations.map(database.upnextDao.getTraktHistory()) {
            it.asDomainModel()
        }

    fun traktWatchlistItem(imdbID: String): LiveData<TraktHistory?> =
        Transformations.map(database.upnextDao.checkIfInTraktWatchlist(imdbID)) { result ->
            result?.asDomainModel()
        }

    private val _isLoading = MutableLiveData<Boolean>()

    private val _traktAccessToken = MutableLiveData<TraktAccessToken>()

    private val _isLoadingTraktWatchlist = MutableLiveData<Boolean>()

    private val _isLoadingTraktHistory = MutableLiveData<Boolean>()

    private val _traktCollection = MutableLiveData<TraktCollection>()

    private val _traktHistory = MutableLiveData<TraktHistory>()

    private val _addToWatchlistResponse = MutableLiveData<TraktAddToWatchlist>()

    private val _removeFromWatchlistResponse = MutableLiveData<TraktRemoveFromWatchlist>()

    private val _traktShowRating = MutableLiveData<TraktShowRating>()

    private val _traktShowStats = MutableLiveData<TraktShowStats>()

    private val _traktWatchedProgress = MutableLiveData<TraktShowWatchedProgress>()

    private val _invalidToken = MutableLiveData<Boolean>()

    private val _invalidGrant = MutableLiveData<Boolean>()

    val isLoadingTraktWatchlist: LiveData<Boolean> = _isLoadingTraktWatchlist

    val isLoadingTraktHistory: LiveData<Boolean> = _isLoadingTraktHistory

    val traktAccessToken: LiveData<TraktAccessToken> = _traktAccessToken

    val addToWatchlistResponse: LiveData<TraktAddToWatchlist> = _addToWatchlistResponse

    val removeFromWatchlistResponse: LiveData<TraktRemoveFromWatchlist> =
        _removeFromWatchlistResponse

    val isLoading: LiveData<Boolean> = _isLoading

    val traktShowRating: LiveData<TraktShowRating> = _traktShowRating

    val traktShowStats: LiveData<TraktShowStats> = _traktShowStats

    val traktWatchedProgress: LiveData<TraktShowWatchedProgress> = _traktWatchedProgress

    val invalidToken: LiveData<Boolean> = _invalidToken

    val invalidGrant: LiveData<Boolean> = _invalidGrant

    suspend fun getTraktAccessToken(code: String?) {
        if (code.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val traktAccessTokenRequest =
                    NetworkTraktAccessTokenRequest(
                        code = code,
                        client_id = BuildConfig.TRAKT_CLIENT_ID,
                        client_secret = BuildConfig.TRAKT_CLIENT_SECRET,
                        redirect_uri = BuildConfig.TRAKT_REDIRECT_URI,
                        grant_type = "authorization_code"
                    )

                val accessTokenResponse =
                    TraktNetwork.traktApi.getAccessTokenAsync(traktAccessTokenRequest).await()
                _traktAccessToken.postValue(accessTokenResponse.asDomainModel())
                _isLoading.postValue(false)
            } catch (e: Exception) {
                _isLoading.postValue(false)
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    suspend fun getTraktAccessRefreshToken(refreshToken: String?) {
        if (refreshToken.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)

                val traktAccessTokenRequest =
                    NetworkTraktAccessRefreshTokenRequest(
                        refresh_token = refreshToken,
                        client_id = BuildConfig.TRAKT_CLIENT_ID,
                        client_secret = BuildConfig.TRAKT_CLIENT_SECRET,
                        redirect_uri = BuildConfig.TRAKT_REDIRECT_URI,
                        grant_type = "refresh_token"
                    )

                val accessTokenResponse =
                    TraktNetwork.traktApi.getAccessRefreshTokenAsync(traktAccessTokenRequest)
                        .await()
                _traktAccessToken.postValue(accessTokenResponse.asDomainModel())
                _isLoading.postValue(false)
            } catch (e: HttpException) {
                handleTraktError(e)
                _isLoading.postValue(false)
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    suspend fun refreshTraktWatchlist(accessToken: String?) {
        if (accessToken.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                _isLoadingTraktWatchlist.postValue(true)
                val updatedWatchList: MutableList<DatabaseTraktWatchlist> = arrayListOf()

                val watchlistResponse = TraktNetwork.traktApi.getWatchlistAsync(
                    token = "Bearer $accessToken"
                ).await()

                // loop through each watchlist item to get the title and IMDB link
                if (!watchlistResponse.isNullOrEmpty()) {
                    for (item in watchlistResponse) {
                        var watchListItem: NetworkTraktWatchlistResponseItem = item
                        val imdbID = watchListItem.show?.ids?.imdb
                        val traktTitle = watchListItem.show?.title

                        // perform a TvMaze search for the Trakt item using the Trakt title
                        val tvMazeSearch =
                            traktTitle?.let {
                                TvMazeNetwork.tvMazeApi.getSuggestionListAsync(it).await()
                            }

                        // loop through the search results from TvMaze and find a match for the IMDb ID
                        // and update the watchlist item by adding the TvMaze ID
                        if (!tvMazeSearch.isNullOrEmpty()) {
                            for (searchItem in tvMazeSearch) {
                                val showSearchItem: NetworkShowSearchResponse = searchItem
                                if (showSearchItem.show.externals.imdb == imdbID) {
                                    watchListItem.show?.originalImageUrl =
                                        showSearchItem.show.image?.medium
                                    watchListItem.show?.mediumImageUrl =
                                        showSearchItem.show.image?.original
                                    watchListItem.show?.ids?.tvMaze = showSearchItem.show.id
                                }
                            }
                        }
                        updatedWatchList.add(watchListItem.asDatabaseModel())
                    }
                }
                saveTraktWatchlist(updatedWatchList)
                _isLoading.postValue(false)
                _isLoadingTraktWatchlist.postValue(false)
            } catch (e: HttpException) {
                handleTraktError(e)
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLoading.postValue(false)
                _isLoadingTraktWatchlist.postValue(false)
            }
        }
    }

    private suspend fun saveTraktWatchlist(list: List<DatabaseTraktWatchlist>) {
        if (!list.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    database.upnextDao.apply {
                        deleteAllTraktWatchlist()
                        insertAllTraktWatchlist(*list.toTypedArray())
                    }

                } catch (e: Exception) {
                    Timber.d(e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    suspend fun refreshTraktHistory(accessToken: String?) {
        withContext(Dispatchers.IO) {
            try {
                _isLoadingTraktHistory.postValue(true)
                val updatedHistoryList: MutableList<DatabaseTraktHistory> = arrayListOf()

                val historyResponse = TraktNetwork.traktApi.getHistoryAsync(
                    token = "Bearer $accessToken"
                ).await()

                // loop through each watchlist item to get the title and IMDB link
                if (!historyResponse.isNullOrEmpty()) {
                    for (item in historyResponse) {
                        var historyItem: NetworkTraktHistoryResponseItem = item
                        if (item.type == "episode") {
                            val imdbID = historyItem.show?.ids?.imdb
                            val traktTitle = historyItem.show?.title

                            // perform a TvMaze search for the Trakt item using the Trakt title
                            val tvMazeSearch =
                                traktTitle?.let {
                                    TvMazeNetwork.tvMazeApi.getSuggestionListAsync(it).await()
                                }

                            // loop through the search results from TvMaze and find a match for the IMDb ID
                            // and update the watchlist item by adding the TvMaze ID
                            if (!tvMazeSearch.isNullOrEmpty()) {
                                for (searchItem in tvMazeSearch) {
                                    val showSearchItem: NetworkShowSearchResponse = searchItem
                                    if (showSearchItem.show.externals.imdb == imdbID) {
                                        historyItem.show?.originalImageUrl =
                                            showSearchItem.show.image?.medium
                                        historyItem.show?.mediumImageUrl =
                                            showSearchItem.show.image?.original
                                        historyItem.show?.ids?.tvMaze = showSearchItem.show.id
                                    }
                                }
                            }
                            updatedHistoryList.add(historyItem.asDatabaseModel())
                        }
                        saveTraktHistory(updatedHistoryList)
                    }
                }
                _isLoadingTraktHistory.postValue(false)
            } catch (e: HttpException) {
                handleTraktError(e)
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLoadingTraktHistory.postValue(false)
            }
        }
    }

    private suspend fun saveTraktHistory(list: List<DatabaseTraktHistory>) {
        if (!list.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    database.upnextDao.apply {
                        deleteAllTraktHistory()
                        insertAllTraktHistory(*list.toTypedArray())
                    }

                } catch (e: Exception) {
                    Timber.d(e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    suspend fun refreshTraktWatched(accessToken: String?) {
        withContext(Dispatchers.IO) {
            try {
                val watchlistResponse = TraktNetwork.traktApi.getWatchedAsync(
                    token = "Bearer $accessToken"
                ).await()
                Timber.d(watchlistResponse.toString())
            } catch (e: Exception) {
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    suspend fun refreshTraktCollection(accessToken: String?) {
        withContext(Dispatchers.IO) {
            try {
                val collectionResponse = TraktNetwork.traktApi.getCollectionAsync(
                    token = "Bearer $accessToken"
                ).await()
            } catch (e: Exception) {
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    suspend fun traktAddToWatchlist(accessToken: String?, imdbID: String) {
        traktPerformWatchlistAction(accessToken, imdbID, WATCHLIST_ACTION_ADD)
    }

    suspend fun traktRemoveFromWatchlist(accessToken: String?, imdbID: String) {
        traktPerformWatchlistAction(accessToken, imdbID, WATCHLIST_ACTION_REMOVE)
    }

    private suspend fun traktPerformWatchlistAction(
        accessToken: String?,
        imdbID: String,
        action: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val addToWatchlistShowsList: MutableList<NetworkTraktAddToWatchlistRequestShow> =
                    arrayListOf()
                val removeFromWatchlistShowsList: MutableList<NetworkTraktRemoveFromWatchlistRequestShow> =
                    arrayListOf()

                val searchList = TraktNetwork.traktApi.getIDLookupAsync(
                    token = "Bearer $accessToken",
                    id = imdbID,
                    type = "show"
                ).await()

                if (!searchList.isNullOrEmpty()) {
                    when (action) {
                        WATCHLIST_ACTION_ADD -> {
                            performAddToTraktRequestAction(
                                accessToken = accessToken,
                                response = searchList,
                                addToWatchlistShowsList = addToWatchlistShowsList
                            )
                        }
                        WATCHLIST_ACTION_REMOVE -> {
                            performRemoveFromTraktRequestAction(
                                accessToken = accessToken,
                                response = searchList,
                                removeFromWatchlistShowsList = removeFromWatchlistShowsList
                            )
                        }
                    }
                }
                _isLoading.postValue(false)
            } catch (e: Exception) {
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun performAddToTraktRequestAction(
        accessToken: String?,
        response: NetworkTraktIDLookupResponse,
        addToWatchlistShowsList: MutableList<NetworkTraktAddToWatchlistRequestShow>
    ) {
        for (item in response) {
            val watchlistItem: NetworkTraktIDLookupResponseItem = item
            val addToWatchlistItem =
                NetworkTraktAddToWatchlistRequestShow(
                    ids = watchlistItem.show.ids,
                    title = watchlistItem.show.title,
                    year = watchlistItem.show.year,
                    seasons = emptyList()
                )
            addToWatchlistShowsList.add(addToWatchlistItem)
            val addToWatchlist = TraktNetwork.traktApi.addToWatchlistAsync(
                token = "Bearer $accessToken",
                request = NetworkTraktAddToWatchlistRequest(
                    shows = addToWatchlistShowsList,
                    movies = emptyList(),
                    seasons = emptyList(),
                    episodes = emptyList()
                )
            ).await()
            _addToWatchlistResponse.postValue(addToWatchlist.asDomainModel())
        }
    }

    private suspend fun performRemoveFromTraktRequestAction(
        accessToken: String?,
        response: NetworkTraktIDLookupResponse,
        removeFromWatchlistShowsList: MutableList<NetworkTraktRemoveFromWatchlistRequestShow>
    ) {
        for (item in response) {
            val watchlistItem: NetworkTraktIDLookupResponseItem = item
            val removeFromWatchlistItem =
                NetworkTraktRemoveFromWatchlistRequestShow(
                    ids = watchlistItem.show.ids,
                    title = watchlistItem.show.title,
                    year = watchlistItem.show.year,
                    seasons = emptyList()
                )
            removeFromWatchlistShowsList.add(removeFromWatchlistItem)
            val removeFromWatchlist =
                TraktNetwork.traktApi.removeFromWatchlistAsync(
                    token = "Bearer $accessToken",
                    request = NetworkTraktRemoveFromWatchlistRequest(
                        shows = removeFromWatchlistShowsList,
                        movies = emptyList(),
                        seasons = emptyList(),
                        episodes = emptyList()
                    )
                ).await()
            _removeFromWatchlistResponse.postValue(removeFromWatchlist.asDomainModel())
        }
    }

    suspend fun getTraktShowRating(
        accessToken: String?,
        imdbID: String?
    ) {
        if (imdbID.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val showRatingResponse = TraktNetwork.traktApi.getShowRatingsAsync(
                    token = "Bearer $accessToken",
                    id = imdbID
                ).await()
                _traktShowRating.postValue(showRatingResponse.asDomainModel())
                _isLoading.postValue(false)
            } catch (e: Exception) {
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLoading.postValue(false)
            }
        }
    }

    suspend fun getTraktShowStats(
        accessToken: String?,
        imdbID: String?
    ) {
        if (imdbID.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val showStatsResponse = TraktNetwork.traktApi.getShowStatsAsync(
                    token = "Bearer $accessToken",
                    id = imdbID
                ).await()
                _traktShowStats.postValue(showStatsResponse.asDomainModel())
                _isLoading.postValue(false)
            } catch (e: Exception) {
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLoading.postValue(false)
            }
        }
    }

    suspend fun getTraktWatchedProgress(
        accessToken: String?,
        imdbID: String?
    ) {
        if (imdbID.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val showWatchedProgress = TraktNetwork.traktApi.getShowWatchedProgressAsync(
                    token = "Bearer $accessToken",
                    id = imdbID
                ).await()
                _traktWatchedProgress.postValue(showWatchedProgress.asDomainModel())
                _isLoading.postValue(false)
            } catch (e: HttpException) {
                handleTraktError(e)
                Timber.d(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLoading.postValue(false)
            }
        }
    }

    private fun handleTraktError(exception: HttpException) {
        when (exception.code()) {
            401 -> {
                when (exception.message) {
                    TRAKT_ERROR_INVALID_TOKEN -> {
                        _invalidToken.postValue(true)
                    }
                    TRAKT_ERROR_INVALID_GRANT -> {
                        _invalidGrant.postValue(true)
                    }
                    else -> {
                        _invalidToken.postValue(true)
                    }
                }
            }
        }
    }

    companion object {
        const val WATCHLIST_ACTION_ADD = "add_to_watchlist"
        const val WATCHLIST_ACTION_REMOVE = "remove_from_watchlist"

        const val TRAKT_ERROR_INVALID_TOKEN = "invalid_token"
        const val TRAKT_ERROR_INVALID_GRANT = "invalid_grant"
    }
}