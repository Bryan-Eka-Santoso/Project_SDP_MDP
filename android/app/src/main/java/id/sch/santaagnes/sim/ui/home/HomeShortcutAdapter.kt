package id.sch.santaagnes.sim.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.databinding.ItemHomeShortcutBinding

/** Satu tile pintasan di Beranda. */
data class HomeShortcut(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit
)

class HomeShortcutAdapter(
    private val items: List<HomeShortcut>
) : RecyclerView.Adapter<HomeShortcutAdapter.ShortcutHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutHolder =
        ShortcutHolder(ItemHomeShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ShortcutHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class ShortcutHolder(private val binding: ItemHomeShortcutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HomeShortcut) {
            binding.ivIcon.setImageResource(item.icon)
            binding.tvLabel.setText(item.label)
            binding.root.setOnClickListener { item.onClick() }
        }
    }
}
