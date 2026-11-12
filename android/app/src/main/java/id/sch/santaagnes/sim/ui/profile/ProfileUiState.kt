
package id.sch.santaagnes.sim.ui.profile

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isLogoutSuccess: Boolean = false,
    val errorMessage: String? = null
)
