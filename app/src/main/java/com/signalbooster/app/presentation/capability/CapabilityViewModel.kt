package com.signalbooster.app.presentation.capability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.models.CapabilityStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CapabilityViewModel @Inject constructor(
    private val privilegeGateway: PrivilegeGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapabilityUiState())
    val uiState: StateFlow<CapabilityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            privilegeGateway.capabilityStatus.collect { status ->
                _uiState.update { it.copy(capabilityStatus = status) }
            }
        }
    }

}

data class CapabilityUiState(
    val capabilityStatus: CapabilityStatus = CapabilityStatus.DEFAULT
)
