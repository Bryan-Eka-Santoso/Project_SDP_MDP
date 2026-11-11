
package id.sch.santaagnes.sim.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Int,
    val title: String,
    val subjectId: Int? = null,
    val isCompleted: Boolean = false
)
