package id.sch.santaagnes.sim.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import id.sch.santaagnes.sim.databinding.FragmentComingSoonBinding

/** Tab milik fitur lain yang belum dibuat; judul dikirim lewat argumen `titleRes`. */
class ComingSoonFragment : Fragment() {

    private var _binding: FragmentComingSoonBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComingSoonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val titleRes = requireArguments().getInt("titleRes")
        binding.tvTitle.setText(titleRes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
