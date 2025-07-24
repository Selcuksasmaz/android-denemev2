package com.example.zedeneme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.zedeneme.data.HomeState
import com.example.zedeneme.repository.FaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: FaceRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeState())
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    init {
        loadRegisteredPeople()
    }

    private fun loadRegisteredPeople() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                repository.getAllProfiles().collect { profiles ->
                    _uiState.value = _uiState.value.copy(
                        registeredPeople = profiles,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Bilinmeyen hata"
                )
            }
        }
    }

    fun deletePerson(profileId: String) {
        viewModelScope.launch {
            try {
                repository.deleteProfile(profileId)
                // Flow otomatik olarak güncellenecek
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Silme işlemi başarısız: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refresh() {
        loadRegisteredPeople()
    }
}

class HomeViewModelFactory(private val repository: FaceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}