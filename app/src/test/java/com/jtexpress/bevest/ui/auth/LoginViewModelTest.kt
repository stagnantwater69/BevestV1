package com.jtexpress.bevest.ui.auth

import app.cash.turbine.test
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val authRepository: AuthRepository = mockk(relaxed = true) {
        coEvery { authState } returns emptyFlow()
    }
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LoginViewModel(authRepository)

    @Test
    fun `invalid input surfaces field errors and never calls the repository`() = runTest {
        val vm = viewModel()
        vm.onEmailChange("not-an-email")
        vm.onPasswordChange("123")

        vm.submit()

        val state = vm.state.value
        assertEquals("Enter a valid email address", state.emailError)
        assertEquals("Password must be at least 6 characters", state.passwordError)
        assertFalse(state.submitting)
        io.mockk.coVerify(exactly = 0) { authRepository.signIn(any(), any()) }
    }

    @Test
    fun `failed sign-in clears submitting and shows the error message`() = runTest {
        coEvery { authRepository.signIn("worker@site.com", "secret1") } returns
            Outcome.Failure(AppError.NotAuthorized("Wrong password."))
        val vm = viewModel()
        vm.onEmailChange("worker@site.com")
        vm.onPasswordChange("secret1")

        vm.state.test {
            assertEquals(LoginUiState(email = "worker@site.com", password = "secret1"), awaitItem())
            vm.submit()
            assertEquals(true, awaitItem().submitting)
            val done = awaitItem()
            assertFalse(done.submitting)
            assertEquals("Wrong password.", done.formError)
        }
    }

    @Test
    fun `successful sign-in ends the submitting state with no form error`() = runTest {
        coEvery { authRepository.signIn(any(), any()) } returns Outcome.Success("uid-1")
        val vm = viewModel()
        vm.onEmailChange("admin@site.com")
        vm.onPasswordChange("secret1")

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertNull(state.formError)
    }
}
