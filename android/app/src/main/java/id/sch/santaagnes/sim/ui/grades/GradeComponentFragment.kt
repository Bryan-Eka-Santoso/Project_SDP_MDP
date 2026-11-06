
package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentGradeComponentBinding
import id.sch.santaagnes.sim.domain.model.GradeComponent
import kotlinx.coroutines.launch

class GradeComponentFragment : Fragment() {

    private var _binding: FragmentGradeComponentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GradeComponentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGradeComponentBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveComponent.setOnClickListener {
            viewModel.saveComponent(
                binding.etComponentName.text.toString(),
                binding.etWeight.text.toString()
            )
        }

        binding.btnCancelEdit.setOnClickListener {
            viewModel.cancelEdit()
            clearForm()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: GradeComponentUiState) {
        binding.tvTotalWeight.text =
            "Total Bobot: ${state.totalWeight}%"

        binding.tvValidation.setText(
            if (state.isValid) R.string.grade_valid
            else R.string.grade_invalid
        )

        binding.btnCancelEdit.visibility =
            if (state.selectedId != null) View.VISIBLE
            else View.GONE

        if (state.selectedId != null) {
            val item = state.components.find {
                it.id == state.selectedId
            }
            if (item != null) {
                binding.etComponentName.setText(item.name)
                binding.etWeight.setText(item.weight.toString())
            }
        } else {
            clearForm()
        }

        renderComponents(state.components)

        state.message?.let { message ->
            Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
            ).show()
            viewModel.clearMessage()
        }
    }

    private fun renderComponents(
        components: List<GradeComponent>
    ) {
        binding.containerComponents.removeAllViews()

        components.forEach { component ->

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val title = TextView(requireContext()).apply {
                text = "${component.name} — ${component.weight}%"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val editButton = Button(requireContext()).apply {
                setText(R.string.grade_edit)
                setOnClickListener {
                    viewModel.selectComponent(component.id)
                }
            }

            val deleteButton = Button(requireContext()).apply {
                setText(R.string.grade_delete)
                setOnClickListener {
                    viewModel.deleteComponent(component.id)
                }
            }

            row.addView(title)
            row.addView(editButton)
            row.addView(deleteButton)

            binding.containerComponents.addView(row)
        }
    }

    private fun clearForm() {
        binding.etComponentName.text?.clear()
        binding.etWeight.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
