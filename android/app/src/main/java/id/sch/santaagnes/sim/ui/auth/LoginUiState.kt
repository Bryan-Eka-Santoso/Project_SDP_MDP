package id.sch.santaagnes.sim.ui.auth

import androidx.annotation.StringRes

data class LoginUiState(
    val isLoading: Boolean = false,
    // pesan dari server atau lapisan jaringan (sudah berbahasa Indonesia)
    val errorMessage: String? = null,
    // pesan validasi lokal; teksnya ada di strings.xml
    @param:StringRes val errorRes: Int? = null,
    val isLoginSuccess: Boolean = false,
    val role: String? = null
)
