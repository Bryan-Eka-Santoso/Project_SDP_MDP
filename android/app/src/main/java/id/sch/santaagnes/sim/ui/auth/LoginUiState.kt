package id.sch.santaagnes.sim.ui.auth

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false,
    val role: String? = null
)
