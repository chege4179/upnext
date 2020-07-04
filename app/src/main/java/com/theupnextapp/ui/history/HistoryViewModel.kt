package com.theupnextapp.ui.history

import android.app.Application
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.theupnextapp.domain.ShowDetailArg
import com.theupnextapp.ui.common.TraktViewModel
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : TraktViewModel(application) {

    private val _navigateToSelectedShow = MutableLiveData<ShowDetailArg>()

    val isLoadingHistory = traktRepository.isLoadingTraktHistory

    val navigateToSelectedShow: LiveData<ShowDetailArg> = _navigateToSelectedShow

    val historyEmpty = MediatorLiveData<Boolean>()

    val traktHistory = traktRepository.traktHistory

    init {
        if (ifValidAccessTokenExists()) {
            loadTraktHistory()
            _isAuthorizedOnTrakt.value = true
        }

        historyEmpty.addSource(traktHistory) {
            historyEmpty.value = it.isNullOrEmpty() == true
        }
    }

    private fun loadTraktHistory() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(getApplication())
        val accessToken = preferences.getString(SHARED_PREF_TRAKT_ACCESS_TOKEN, null)

        viewModelScope?.launch {
            traktRepository.refreshTraktHistory(accessToken)
        }
    }

    fun displayShowDetails(showDetailArg: ShowDetailArg) {
        _navigateToSelectedShow.value = showDetailArg
    }

    fun displayShowDetailsComplete() {
        _navigateToSelectedShow.value = null
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    class Factory(val app: Application) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(
                    app
                ) as T
            }
            throw IllegalArgumentException("Unable to construct viewModel")
        }
    }
}