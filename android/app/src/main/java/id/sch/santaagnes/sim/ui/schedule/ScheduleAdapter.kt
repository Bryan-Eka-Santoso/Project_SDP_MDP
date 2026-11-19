package id.sch.santaagnes.sim.ui.schedule

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemScheduleEntryBinding
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ScheduleEntry

class ScheduleAdapter : ListAdapter<ScheduleEntry, ScheduleAdapter.LessonViewHolder>(DiffCallback) {

    /** true: baris kedua berisi nama guru; false: nama kelas (jadwal mengajar guru). */
    var showTeacher: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder =
        LessonViewHolder(ItemScheduleEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** true hanya di jadwal mengajar guru: pertemuan miliknya yang belum lewat bisa dipindah atau dibatalkan. */
    var canReschedule: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    var onReschedule: ((ScheduleEntry) -> Unit)? = null

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry, showTeacher)
        holder.bindAction(entry, canReschedule && isReschedulable(entry), onReschedule)
    }

    // Aturan yang sama dengan server: belum lewat, tanpa guru pengganti dari admin, dan bukan tanda
    // "sudah dipindah" (pindah lintas hari dikelola dari entri di tanggal tujuan).
    private fun isReschedulable(entry: ScheduleEntry): Boolean {
        val change = entry.change
        val editable = change == null ||
            change.type == ChangeType.CANCEL ||
            (change.type == ChangeType.RESCHEDULE && change.movedTo == null)
        return editable && !entry.date.isBefore(DateTimeWib.today())
    }

    class LessonViewHolder(
        private val binding: ItemScheduleEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ScheduleEntry, showTeacher: Boolean) {
            val context = binding.root.context
            // Dicoret: tidak berlangsung pada tanggal ini (dibatalkan atau dipindah ke tanggal lain).
            val cancelled = entry.isAway

            binding.tvTime.text = context.getString(
                R.string.schedule_time_range,
                DateTimeWib.formatTime(entry.startTime),
                DateTimeWib.formatTime(entry.endTime)
            )
            binding.tvSubject.text = entry.subjectName
            binding.tvSubject.paintFlags = if (cancelled) {
                binding.tvSubject.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvSubject.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.tvTeacher.text = if (showTeacher) {
                entry.teacherName
            } else {
                context.getString(R.string.schedule_class, entry.classGroupName)
            }
            binding.tvRoom.text = entry.roomName
                ?.let { context.getString(R.string.schedule_room, it) }
                ?: context.getString(R.string.schedule_room_none)

            bindChange(context, entry)
        }

        fun bindAction(entry: ScheduleEntry, enabled: Boolean, onReschedule: ((ScheduleEntry) -> Unit)?) {
            binding.tvAction.isVisible = enabled
            binding.tvAction.setText(
                when (entry.change?.type) {
                    null -> R.string.schedule_action_reschedule
                    ChangeType.CANCEL -> R.string.schedule_action_restore
                    else -> R.string.schedule_action_edit_reschedule
                }
            )
            binding.cardLesson.isClickable = enabled
            binding.cardLesson.setOnClickListener(if (enabled) { _ -> onReschedule?.invoke(entry) } else null)
        }

        private fun bindChange(context: Context, entry: ScheduleEntry) {
            val change = entry.change
            if (change == null) {
                binding.tvChange.isVisible = false
                binding.tvOriginal.isVisible = false
                return
            }

            val (label, color) = when (change.type) {
                ChangeType.SUBSTITUTE -> R.string.schedule_change_substitute to R.color.change_substitute
                ChangeType.RESCHEDULE -> R.string.schedule_change_reschedule to R.color.change_reschedule
                ChangeType.CANCEL -> R.string.schedule_change_cancel to R.color.change_cancel
            }
            binding.tvChange.isVisible = true
            binding.tvChange.setText(label)
            binding.tvChange.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, color))

            val lines = originalLines(context, entry)
            binding.tvOriginal.isVisible = lines.isNotEmpty()
            binding.tvOriginal.text = lines.joinToString("\n")
        }

        // Nilai semula yang berbeda dari tampilan sekarang, ditambah catatan dari admin bila ada.
        private fun originalLines(context: Context, entry: ScheduleEntry): List<String> {
            val change = entry.change ?: return emptyList()
            val original = entry.original
            val lines = mutableListOf<String>()

            change.movedTo?.let { lines += context.getString(R.string.schedule_moved_to, DateTimeWib.formatDate(it)) }
            change.movedFrom?.let { lines += context.getString(R.string.schedule_moved_from, DateTimeWib.formatDate(it)) }
            if (original != null) {
                when (change.type) {
                    ChangeType.SUBSTITUTE -> original.teacherName?.let {
                        lines += context.getString(R.string.schedule_original_teacher, it)
                    }
                    ChangeType.RESCHEDULE -> {
                        if (original.startTime != entry.startTime || original.endTime != entry.endTime) {
                            lines += context.getString(
                                R.string.schedule_original_time,
                                DateTimeWib.formatTime(original.startTime),
                                DateTimeWib.formatTime(original.endTime)
                            )
                        }
                        if (original.roomName != null && original.roomName != entry.roomName) {
                            lines += context.getString(R.string.schedule_original_room, original.roomName)
                        }
                    }
                    ChangeType.CANCEL -> Unit
                }
            }
            change.description?.takeIf { it.isNotBlank() }?.let { lines += it }
            return lines
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScheduleEntry>() {
        override fun areItemsTheSame(oldItem: ScheduleEntry, newItem: ScheduleEntry): Boolean =
            oldItem.date == newItem.date && oldItem.slotId == newItem.slotId

        override fun areContentsTheSame(oldItem: ScheduleEntry, newItem: ScheduleEntry): Boolean =
            oldItem == newItem
    }
}
