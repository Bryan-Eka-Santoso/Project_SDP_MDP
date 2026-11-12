package id.sch.santaagnes.sim.ui.task

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.DueCountdown
import id.sch.santaagnes.sim.core.dueCountdown
import id.sch.santaagnes.sim.databinding.ItemStudentTaskBinding
import id.sch.santaagnes.sim.databinding.ItemSubjectHeaderBinding
import id.sch.santaagnes.sim.databinding.ItemTodoBinding
import id.sch.santaagnes.sim.domain.model.Todo
import java.time.LocalDate

/** Baris pada layar tugas siswa: judul mapel, tugas dari guru, atau pengingat pribadi. */
sealed interface StudentTaskItem {
    data class SubjectHeader(val name: String) : StudentTaskItem
    data class TeacherTask(val row: StudentTaskRow) : StudentTaskItem
    data class Reminder(val todo: Todo) : StudentTaskItem
}

/** "Waktu sisa 3 hari", "Hari ini", atau "Terlambat 2 hari". */
fun countdownText(context: Context, due: LocalDate, today: LocalDate): String =
    when (val countdown = dueCountdown(due, today)) {
        is DueCountdown.Overdue -> context.getString(R.string.due_overdue, countdown.days)
        DueCountdown.Today -> context.getString(R.string.due_today)
        is DueCountdown.Remaining -> context.getString(R.string.due_remaining, countdown.days)
    }

class StudentTaskAdapter(
    private val onTaskDone: (taskId: Int, done: Boolean) -> Unit,
    private val onTodoCompleted: (todoId: Long, completed: Boolean) -> Unit,
    private val onTodoDelete: (Todo) -> Unit
) : ListAdapter<StudentTaskItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is StudentTaskItem.SubjectHeader -> TYPE_HEADER
        is StudentTaskItem.TeacherTask -> TYPE_TASK
        is StudentTaskItem.Reminder -> TYPE_REMINDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemSubjectHeaderBinding.inflate(inflater, parent, false))
            TYPE_TASK -> TaskHolder(ItemStudentTaskBinding.inflate(inflater, parent, false))
            else -> ReminderHolder(ItemTodoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StudentTaskItem.SubjectHeader -> (holder as HeaderHolder).bind(item)
            is StudentTaskItem.TeacherTask -> (holder as TaskHolder).bind(item.row, onTaskDone)
            is StudentTaskItem.Reminder -> (holder as ReminderHolder).bind(item.todo, onTodoCompleted, onTodoDelete)
        }
    }

    private class HeaderHolder(private val binding: ItemSubjectHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StudentTaskItem.SubjectHeader) {
            binding.tvSubjectHeader.text = item.name
        }
    }

    private class TaskHolder(private val binding: ItemStudentTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: StudentTaskRow, onDone: (Int, Boolean) -> Unit) {
            val context = binding.root.context
            val task = row.task
            binding.tvTitle.text = task.title
            strike(binding.tvTitle, row.done)
            binding.tvDue.text = context.getString(
                R.string.task_due_with_countdown,
                DateTimeWib.formatDate(task.dueDate),
                countdownText(context, task.dueDate, DateTimeWib.today())
            )
            binding.tvNotes.isVisible = task.description != null
            binding.tvNotes.text = task.description
            // Listener dilepas dulu agar penyetelan isChecked dari data tidak dianggap ketukan pengguna.
            binding.cbDone.setOnCheckedChangeListener(null)
            binding.cbDone.isChecked = row.done
            binding.cbDone.setOnCheckedChangeListener { _, checked -> onDone(task.id, checked) }
        }
    }

    private class ReminderHolder(private val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(todo: Todo, onCompleted: (Long, Boolean) -> Unit, onDelete: (Todo) -> Unit) {
            val context = binding.root.context
            binding.tvTitle.text = todo.title
            strike(binding.tvTitle, todo.isCompleted)
            binding.tvSubject.isVisible = todo.subjectName != null
            binding.tvSubject.text = todo.subjectName
            binding.tvNotes.isVisible = todo.notes != null
            binding.tvNotes.text = todo.notes
            binding.tvCountdown.text = if (todo.isCompleted) {
                context.getString(R.string.todo_done)
            } else {
                context.getString(
                    R.string.task_due_with_countdown,
                    DateTimeWib.formatDate(todo.dueDate),
                    countdownText(context, todo.dueDate, DateTimeWib.today())
                )
            }
            val overdue = !todo.isCompleted && todo.dueDate.isBefore(DateTimeWib.today())
            binding.tvCountdown.setTextColor(
                ContextCompat.getColor(context, if (overdue) R.color.change_cancel else R.color.agnesian_blue)
            )
            binding.cbDone.setOnCheckedChangeListener(null)
            binding.cbDone.isChecked = todo.isCompleted
            binding.cbDone.setOnCheckedChangeListener { _, checked -> onCompleted(todo.id, checked) }
            binding.btnDelete.setOnClickListener { onDelete(todo) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudentTaskItem>() {
        override fun areItemsTheSame(oldItem: StudentTaskItem, newItem: StudentTaskItem): Boolean = when {
            oldItem is StudentTaskItem.SubjectHeader && newItem is StudentTaskItem.SubjectHeader ->
                oldItem.name == newItem.name
            oldItem is StudentTaskItem.TeacherTask && newItem is StudentTaskItem.TeacherTask ->
                oldItem.row.task.id == newItem.row.task.id
            oldItem is StudentTaskItem.Reminder && newItem is StudentTaskItem.Reminder ->
                oldItem.todo.id == newItem.todo.id
            else -> false
        }

        override fun areContentsTheSame(oldItem: StudentTaskItem, newItem: StudentTaskItem): Boolean =
            oldItem == newItem
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TASK = 1
        const val TYPE_REMINDER = 2

        fun strike(view: TextView, struck: Boolean) {
            view.paintFlags = if (struck) {
                view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }
    }
}
