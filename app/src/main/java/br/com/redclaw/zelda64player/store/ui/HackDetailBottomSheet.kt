package br.com.redclaw.zelda64player.store.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.FragmentHackDetailBinding
import br.com.redclaw.zelda64player.store.InstallPhase
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom-sheet detail view for a single hack: full metadata, required base ROM
 * (and whether the user already has a matching one), and a download/update
 * button with determinate progress and inline error states.
 *
 * The [StoreViewModel] is obtained from the host [StoreActivity] so it survives
 * configuration changes. The hack is passed as JSON in the arguments for the
 * same reason.
 */
class HackDetailBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentHackDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StoreViewModel by lazy {
        (requireActivity() as StoreActivity).viewModel
    }

    private lateinit var hack: HackEntry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = requireArguments().getString(ARG_HACK)
        hack = HackEntry.fromJson(org.json.JSONObject(json!!))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHackDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populate()
        observeInstall()
        binding.detailDownload.setOnClickListener { viewModel.install(hack) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun populate() {
        binding.detailName.text = hack.name
        binding.detailAuthor.text =
            getString(R.string.detail_author, hack.author)
        binding.detailVersion.text =
            getString(R.string.detail_version, hack.version)
        binding.detailDescription.text = hack.description

        if (hack.tags.isNotEmpty()) {
            binding.detailTags.text = getString(R.string.detail_tags, hack.tags.joinToString(", "))
            binding.detailTags.visibility = View.VISIBLE
        }
        if (hack.compatibleCores.isNotEmpty()) {
            binding.detailCores.text =
                getString(R.string.detail_cores, hack.compatibleCores.joinToString(", "))
            binding.detailCores.visibility = View.VISIBLE
        }

        binding.detailBaseInfo.text = getString(
            R.string.detail_base_info,
            hack.baseRom.name,
            hack.baseRom.gameCode,
            hack.baseRom.versionByte.toString()
        )

        val matches = viewModel.baseRomMatches(hack.baseRom.checksums.crc32)
        binding.detailBaseMatch.text = if (matches) {
            getString(R.string.detail_base_match)
        } else {
            getString(R.string.detail_base_no_match)
        }

        if (hack.coverImageUrl != null) {
            binding.detailCover.load(hack.coverImageUrl) {
                placeholder(R.drawable.placeholder_cover)
                error(R.drawable.placeholder_cover)
                crossfade(true)
            }
        } else {
            binding.detailCover.setImageResource(R.drawable.placeholder_cover)
        }

        when (val status = viewModel.statusFor(hack)) {
            is StoreStatus.NotInstalled -> {
                binding.detailDownload.setText(R.string.detail_download)
                binding.detailDownload.isEnabled = true
            }
            is StoreStatus.Installed -> {
                binding.detailDownload.text = getString(
                    R.string.store_status_installed, status.version
                )
                binding.detailDownload.isEnabled = false
            }
            is StoreStatus.UpdateAvailable -> {
                binding.detailDownload.setText(R.string.detail_update)
                binding.detailDownload.isEnabled = true
            }
        }
    }

    private fun observeInstall() {
        viewModel.install.observe(viewLifecycleOwner) { state ->
            if (state.hackId != hack.id) return@observe
            when (state) {
                is StoreViewModel.InstallUiState.Progress -> {
                    binding.detailProgress.visibility = View.VISIBLE
                    binding.detailError.visibility = View.GONE
                    binding.detailDownload.isEnabled = false
                    if (state.phase == InstallPhase.PATCHING) {
                        // No byte progress while applying the patch; show an
                        // indeterminate bar with a dedicated message.
                        binding.detailProgress.isIndeterminate = true
                        binding.detailProgressText.visibility = View.VISIBLE
                        binding.detailProgressText.text = getString(R.string.detail_patching)
                    } else {
                        binding.detailProgress.isIndeterminate = false
                        val percent = if (state.total > 0) {
                            (state.downloaded * 100 / state.total).toInt()
                        } else 0
                        binding.detailProgress.progress = percent
                        binding.detailProgressText.visibility = View.VISIBLE
                        binding.detailProgressText.text =
                            getString(R.string.detail_installing, percent)
                    }
                }
                is StoreViewModel.InstallUiState.Success -> {
                    binding.detailProgress.visibility = View.GONE
                    binding.detailProgressText.visibility = View.GONE
                    binding.detailError.visibility = View.GONE
                    binding.detailDownload.setText(R.string.store_status_installed)
                    binding.detailDownload.isEnabled = false
                    Toast.makeText(
                        requireContext(),
                        R.string.detail_installed_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is StoreViewModel.InstallUiState.Error -> {
                    binding.detailProgress.visibility = View.GONE
                    binding.detailProgressText.visibility = View.GONE
                    binding.detailDownload.isEnabled = true
                    binding.detailError.text = state.message
                    binding.detailError.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        private const val ARG_HACK = "arg_hack"

        fun newInstance(hack: HackEntry): HackDetailBottomSheet {
            val f = HackDetailBottomSheet()
            val args = Bundle()
            args.putString(ARG_HACK, hack.toJson().toString())
            f.arguments = args
            return f
        }
    }
}
