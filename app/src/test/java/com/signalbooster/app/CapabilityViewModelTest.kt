package com.signalbooster.app

import com.signalbooster.app.presentation.capability.CapabilityViewModel
import com.signalbooster.app.testdoubles.FakePrivilegeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapabilityViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: CapabilityViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CapabilityViewModel(FakePrivilegeGateway())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun capabilityScreenDoesNotExposeUnverifiedExecution() = runTest(testDispatcher) {
        assertTrue(viewModel.uiState.value.capabilityStatus.actions.isEmpty())
    }
}
