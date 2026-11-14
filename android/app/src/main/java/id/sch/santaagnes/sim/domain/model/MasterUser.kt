package id.sch.santaagnes.sim.domain.model

data class MasterUser(
    val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val status: String,
    val isActive: Boolean = status == "ACTIVE",
    val createdAt: String? = null
)

data class UserFilter(
    val search: String? = null,
    val role: String? = null,
    val status: String? = null
)
