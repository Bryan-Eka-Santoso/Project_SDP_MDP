package id.sch.santaagnes.sim.ui.auth

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import id.sch.santaagnes.sim.R

/** Graph navigasi tujuan untuk tiap role; null bila role tidak dikenal. */
fun roleGraphId(role: String?): Int? = when (role) {
    "ADMIN" -> R.id.admin_graph
    "TEACHER" -> R.id.teacher_graph
    "STUDENT" -> R.id.student_graph
    "GUARDIAN" -> R.id.guardian_graph
    else -> null
}

/** Kembali ke login dan kosongkan seluruh back stack (setelah keluar atau sesi berakhir). */
fun NavController.navigateToLogin() {
    if (currentDestination?.id == R.id.loginFragment) return
    navigate(
        R.id.loginFragment,
        null,
        NavOptions.Builder().setPopUpTo(graph.id, true).build()
    )
}
