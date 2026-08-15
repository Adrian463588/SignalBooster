package com.signalbooster.app

import com.signalbooster.app.domain.models.ActionResult
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.presentation.capability.CapabilityViewModel
import com.signalbooster.app.testdoubles.FakePrivilegeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapabilityViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePrivilegeGateway: FakePrivilegeGateway
    private lateinit var viewModel: CapabilityViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePrivilegeGateway = FakePrivilegeGateway()
        viewModel = CapabilityViewModel(fakePrivilegeGateway)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testExecuteAllowlistedAction() = runTest(testDispatcher) {
        viewModel.executeAction(AllowlistedAction.NETWORK_PROBE_START)

        val state = viewModel.uiState.value
        assertNotNull(state.lastActionResult)
        assertEquals(ActionResult.SUCCESS, state.lastActionResult?.result)

        viewModel.clearLastResult()
        assertNull(viewModel.uiState.value.lastActionResult)
    }
}
