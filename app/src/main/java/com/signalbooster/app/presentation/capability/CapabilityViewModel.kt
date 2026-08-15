package com.signalbooster.app.presentation.capability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.PrivilegedActionResult
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

    fun executeAction(action: AllowlistedAction) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            val result = privilegeGateway.executePrivilegedAction(action)
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    lastActionResult = result
                )
            }
        }
    }

    fun clearLastResult() {
        _uiState.update { it.copy(lastActionResult = null) }
    }
}

data class CapabilityUiState(
    val capabilityStatus: CapabilityStatus = CapabilityStatus.DEFAULT,
    val isExecuting: Boolean = false,
    val lastActionResult: PrivilegedActionResult? = null
)
