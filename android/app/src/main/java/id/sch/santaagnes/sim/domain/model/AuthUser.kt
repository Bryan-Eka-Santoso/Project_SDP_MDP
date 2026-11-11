
package id.sch.santaagnes.sim.domain.model

data class AuthUser(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val isHomeroomTeacher: Boolean
)
