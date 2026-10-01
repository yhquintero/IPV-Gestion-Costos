package cu.ipvgc.android.feature.auth

import com.google.common.truth.Truth.assertThat
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.AuthRepository
import cu.ipvgc.android.core.data.LoginResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulLoginMarksDone() = runTest {
        val auth = mockk<AuthRepository>()
        coEvery { auth.login(any(), any()) } returns Outcome.Ok(LoginResult.Ready(null))
        val vm = LoginViewModel(auth)
        vm.setEmail("costeador@alpha.test")
        vm.setPassword("x")
        vm.submit()
        assertThat(vm.state.value.done).isTrue()
    }

    @Test
    fun mfaChallengeStopsBeforeDone() = runTest {
        val auth = mockk<AuthRepository>()
        coEvery { auth.login(any(), any()) } returns Outcome.Ok(LoginResult.Mfa("ch-1"))
        val vm = LoginViewModel(auth)
        vm.submit()
        assertThat(vm.state.value.challengeId).isEqualTo("ch-1")
        assertThat(vm.state.value.done).isFalse()
    }
}
