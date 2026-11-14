package id.sch.santaagnes.sim.ui.task

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemTeacherTaskBinding
import id.sch.santaagnes.sim.domain.model.Task

class TeacherTaskAdapter(
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TeacherTaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder =
        TaskViewHolder(ItemTeacherTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position), onEdit, onDelete)
    }

    class TaskViewHolder(
        private val binding: ItemTeacherTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task, onEdit: (Task) -> Unit, onDelete: (Task) -> Unit) {
            val context = binding.root.context
            binding.tvTitle.text = task.title
            binding.tvSubject.text = context.getString(R.string.task_subject_class, task.subjectName, task.classGroupName)
            binding.tvDue.text = context.getString(R.string.task_due_label, DateTimeWib.formatDate(task.dueDate))
            // Tenggat yang sudah lewat diredupkan agar tugas yang masih berjalan menonjol.
            val overdue = task.dueDate.isBefore(DateTimeWib.today())
            binding.tvDue.setTextColor(
                ContextCompat.getColor(context, if (overdue) R.color.agnesian_muted else R.color.agnesian_blue)
            )
            binding.tvNotes.isVisible = task.description != null
            binding.tvNotes.text = task.description
            binding.btnEdit.setOnClickListener { onEdit(task) }
            binding.btnDelete.setOnClickListener { onDelete(task) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem == newItem
    }
}
