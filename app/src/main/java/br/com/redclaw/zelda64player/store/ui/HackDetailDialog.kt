/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.store.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import br.com.redclaw.zelda64player.databinding.DialogHackDetailBinding
import br.com.redclaw.zelda64player.store.DownloadPhase
import br.com.redclaw.zelda64player.store.DownloadQueueManager
import br.com.redclaw.zelda64player.store.DownloadTarget
import br.com.redclaw.zelda64player.store.GitHubPatchResolver
import br.com.redclaw.zelda64player.ui.switchui.AccentManager
import br.com.redclaw.zelda64player.ui.switchui.BadgeBinder
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Fullscreen hack-detail dialog (replaces the old bottom sheet). Shows the full metadata, the
 * required base ROM (and whether the user already has a matching one), a screenshots gallery,
 * supported-game/completion badges, an expandable changelog, optional video links, and a
 * download/update button whose behavior is driven by [HackEntry.downloadTarget]:
 *
 * - [DownloadTarget.DirectPatch] (or a legacy [HackEntry.patch]) enqueues the
 * ```
 *    download + patch pipeline directly.
 * ```
 * - [DownloadTarget.GitHubRelease] resolves a concrete patch asset from the
 * ```
 *    GitHub Releases API at click time, then enqueues it; if resolution fails
 *    the source page is opened in a browser.
 * ```
 * - [DownloadTarget.ExternalLink] opens the source in a browser.
 *
 * The [StoreViewModel] is obtained from the host [StoreActivity] so it survives configuration
 * changes. The hack is passed as JSON in the arguments for the same reason.
 */
class HackDetailDialog : DialogFragment() {
    private var _binding: DialogHackDetailBinding? = null
    private val binding
        get() = _binding!!

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    private val viewModel: StoreViewModel by lazy { (requireActivity() as StoreActivity).viewModel }

    private lateinit var hack: HackEntry

    /** When set, the download button opens this URL in a browser instead. */
    private var pendingBrowserUrl: String? = null

    override fun getTheme(): Int = R.style.StoreDetailFullscreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = requireArguments().getString(ARG_HACK)
        hack = HackEntry.fromJson(JSONObject(json!!))
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = DialogHackDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populate()
        observeQueue()
        binding.detailClose.setOnClickListener {
            sfx?.back()
            dismiss()
        }
        binding.detailDownload.setOnClickListener {
            Log.d(
                    "HackDetailDialog",
                    "Download button clicked! pendingBrowserUrl=$pendingBrowserUrl, hack.id=${hack.id}"
            )
            if (pendingBrowserUrl != null) {
                openWebView(pendingBrowserUrl!!)
            } else {
                initiateDownload()
            }
        }
        binding.detailChangelogHeader.setOnClickListener { toggleChangelog() }
    }

    override fun onStart() {
        super.onStart()
        // The dialog uses windowIsFloating=false, so it owns its own Window.
        // Re-enter sticky immersive on the dialog's window so the status/nav
        // bars don't reappear (the activity's immersive mode doesn't carry over
        // to a separate dialog window).
        dialog?.window?.let { SwitchImmersive.enterFullscreen(it) }
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                sfx?.back()
            }
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun populate() {
        binding.detailName.text = hack.name
        binding.detailAuthor.text = getString(R.string.detail_author, hack.author)
        binding.detailVersion.text = getString(R.string.detail_version, hack.version)
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

        binding.detailBaseInfo.text =
                getString(
                        R.string.detail_base_info,
                        hack.baseRom.name,
                        hack.baseRom.gameCode,
                        hack.baseRom.versionByte.toString()
                )

        val matches = viewModel.baseRomMatches(hack)
        binding.detailBaseMatch.text =
                if (matches) {
                    getString(R.string.detail_base_match)
                } else {
                    getString(R.string.detail_base_no_match)
                }
        binding.detailBaseMatch.setTextColor(
                if (matches) AccentManager.getAccentColor(requireContext())
                else ContextCompat.getColor(requireContext(), R.color.switch_text_secondary)
        )

        if (hack.coverImageUrl != null) {
            binding.detailCover.load(hack.coverImageUrl) {
                placeholder(R.drawable.placeholder_cover)
                error(R.drawable.placeholder_cover)
                crossfade(true)
            }
        } else {
            binding.detailCover.setImageResource(R.drawable.placeholder_cover)
        }

        populateScreenshots()
        populateBadges()
        populateChangelog()
        populateVideos()
        updateDownloadButton()
        showInstalledAsOtherNote()
    }

    private fun populateScreenshots() {
        if (hack.screenshots.isEmpty()) return
        binding.detailScreenshotsLabel.visibility = View.VISIBLE
        binding.detailScreenshots.visibility = View.VISIBLE
        binding.detailScreenshots.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.detailScreenshots.adapter = ScreenshotAdapter(hack.screenshots)
    }

    private fun populateBadges() {
        // Always show the family badge: prefer supportedGames, fall back to baseRom.gameCode
        // so curated PICKS entries without supportedGames still render OoT/MM correctly.
        BadgeBinder.bindFamily(binding.detailGameBadge, BadgeBinder.familyForHack(hack))
        val completion = hack.completionStatus
        if (!completion.isNullOrBlank()) {
            binding.detailCompletionBadge.text = completion
            binding.detailCompletionBadge.visibility = View.VISIBLE
        }
    }

    private fun populateChangelog() {
        if (hack.changelog.isEmpty()) return
        binding.detailChangelogHeader.visibility = View.VISIBLE
        hack.changelog.forEach { entry ->
            val line = buildString {
                if (!entry.date.isNullOrBlank()) append("${entry.date}: ")
                append(entry.content ?: "")
            }
            val tv =
                    android.widget.TextView(requireContext()).apply {
                        text = line
                        textSize = 13f
                        setTextColor(
                                ContextCompat.getColor(
                                        requireContext(),
                                        R.color.color_on_surface_variant
                                )
                        )
                        setPadding(0, 4, 0, 4)
                    }
            binding.detailChangelogContainer.addView(tv)
        }
    }

    private fun toggleChangelog() {
        sfx?.select()
        val visible = binding.detailChangelogContainer.visibility == View.VISIBLE
        binding.detailChangelogContainer.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun populateVideos() {
        if (hack.videos.isEmpty()) return
        binding.detailVideosLabel.visibility = View.VISIBLE
        binding.detailVideosContainer.visibility = View.VISIBLE
        hack.videos.forEach { url ->
            val tv =
                    android.widget.TextView(requireContext()).apply {
                        text = url
                        textSize = 13f
                        setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.switch_accent)
                        )
                        setPadding(0, 4, 0, 4)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { openWebView(url) }
                    }
            binding.detailVideosContainer.addView(tv)
        }
    }

    /**
     * Shows a subtle "Installed as X (version Y)" note when [hack] is installed via a different
     * store id (cross-catalog match) rather than its exact id. Hidden otherwise.
     */
    private fun showInstalledAsOtherNote() {
        val other = viewModel.installedAsOther(hack)
        if (other == null) {
            binding.detailInstalledAsOther.visibility = View.GONE
            return
        }
        binding.detailInstalledAsOther.text =
                getString(R.string.store_note_installed_as_other, other.first, other.second)
        binding.detailInstalledAsOther.visibility = View.VISIBLE
    }

    /** Sets the download button label/state from install status + download target. */
    private fun updateDownloadButton() {
        pendingBrowserUrl = null
        when (val status = viewModel.statusFor(hack)) {
            is StoreStatus.NotInstalled -> {
                binding.detailDownload.setText(R.string.detail_download)
                binding.detailDownload.isEnabled = true
            }
            is StoreStatus.Installed -> {
                binding.detailDownload.text =
                        getString(R.string.store_status_installed, status.version)
                binding.detailDownload.isEnabled = false
            }
            is StoreStatus.UpdateAvailable -> {
                binding.detailDownload.setText(R.string.detail_update)
                binding.detailDownload.isEnabled = true
            }
        }
    }

    private fun initiateDownload() {
        Log.d(
                "HackDetailDialog",
                "initiateDownload: downloadTarget=${hack.downloadTarget}, patch=${hack.patch}, hack.id=${hack.id}"
        )
        sfx?.select()
        when (val target = hack.downloadTarget) {
            is DownloadTarget.DirectPatch -> {
                val toEnqueue = if (hack.patch != null) hack else hack.copy(patch = target.patch)
                viewModel.enqueue(toEnqueue)
            }
            null -> {
                if (hack.patch != null) {
                    viewModel.enqueue(hack)
                } else {
                    openWebView(hack.coverImageUrl ?: "")
                }
            }
            is DownloadTarget.GitHubRelease -> resolveGitHubAndDownload(target.repoUrl)
            is DownloadTarget.ExternalLink -> openWebView(target.url)
        }
    }

    /** Resolve a GitHub release to a concrete patch URL, then enqueue or fall back to browser. */
    private fun resolveGitHubAndDownload(repoUrl: String) {
        binding.detailDownload.setText(R.string.detail_resolving)
        binding.detailDownload.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val resolved = runCatching { GitHubPatchResolver().resolve(repoUrl) }.getOrNull()
            launch(Dispatchers.Main) {
                if (resolved != null) {
                    val filename =
                            resolved.substringAfterLast('/').takeIf { it.isNotBlank() }
                                    ?: "patch.bps"
                    val patch =
                            PatchRef(
                                    url = resolved,
                                    filename = filename,
                                    size = 0,
                                    checksums = Checksums("", null, null)
                            )
                    val toEnqueue =
                            hack.copy(
                                    patch = patch,
                                    downloadTarget = DownloadTarget.DirectPatch(patch)
                            )
                    viewModel.enqueue(toEnqueue)
                } else {
                    pendingBrowserUrl = repoUrl
                    binding.detailDownload.setText(R.string.detail_open_browser)
                    binding.detailDownload.isEnabled = true
                }
            }
        }
    }

    private fun openWebView(url: String) {
        if (url.isBlank()) return
        val intent =
                Intent(requireContext(), WebViewDownloadActivity::class.java).apply {
                    putExtra(WebViewDownloadActivity.EXTRA_HACK_JSON, hack.toJson().toString())
                    putExtra(WebViewDownloadActivity.EXTRA_URL, url)
                }
        startActivity(intent)
    }

    private fun observeQueue() {
        Log.d("HackDetailDialog", "observeQueue: setting up observer for hackId=${hack.id}")
        // Observe the full queue list and filter by hackId, instead of using
        // queue.map {} which can silently drop postValue updates from background threads.
        DownloadQueueManager.queue.observe(viewLifecycleOwner) { list ->
            val ui = list?.firstOrNull { it.hackId == hack.id }
            Log.d(
                    "HackDetailDialog",
                    "observeQueue callback: hackId=${hack.id}, ui=$ui, phase=${ui?.phase}"
            )
            if (ui == null) return@observe
            when (ui.phase) {
                DownloadPhase.QUEUED, DownloadPhase.DOWNLOADING, DownloadPhase.PATCHING -> {
                    binding.detailProgress.visibility = View.VISIBLE
                    binding.detailError.visibility = View.GONE
                    binding.detailDownload.isEnabled = false
                    when (ui.phase) {
                        DownloadPhase.PATCHING -> {
                            binding.detailProgress.isIndeterminate = true
                            binding.detailProgressText.visibility = View.VISIBLE
                            binding.detailProgressText.text = getString(R.string.detail_patching)
                        }
                        DownloadPhase.DOWNLOADING -> {
                            binding.detailProgress.isIndeterminate = false
                            binding.detailProgress.progress = ui.progressPercent
                            binding.detailProgressText.visibility = View.VISIBLE
                            binding.detailProgressText.text =
                                    getString(R.string.detail_installing, ui.progressPercent)
                        }
                        else -> {
                            binding.detailProgress.isIndeterminate = false
                            binding.detailProgress.progress = 0
                            binding.detailProgressText.visibility = View.VISIBLE
                            binding.detailProgressText.text =
                                    getString(R.string.store_status_queued)
                        }
                    }
                }
                DownloadPhase.SUCCESS -> {
                    binding.detailProgress.visibility = View.GONE
                    binding.detailProgressText.visibility = View.GONE
                    binding.detailError.visibility = View.GONE
                    binding.detailDownload.setText(R.string.store_status_installed)
                    binding.detailDownload.isEnabled = false
                }
                DownloadPhase.ERROR -> {
                    binding.detailProgress.visibility = View.GONE
                    binding.detailProgressText.visibility = View.GONE
                    binding.detailDownload.isEnabled = true
                    binding.detailError.text = ui.error
                    binding.detailError.visibility = View.VISIBLE
                }
                DownloadPhase.CANCELLED -> {
                    binding.detailProgress.visibility = View.GONE
                    binding.detailProgressText.visibility = View.GONE
                    binding.detailDownload.isEnabled = true
                    binding.detailError.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val ARG_HACK = "arg_hack"

        fun newInstance(hack: HackEntry): HackDetailDialog {
            val f = HackDetailDialog()
            val args = Bundle()
            args.putString(ARG_HACK, hack.toJson().toString())
            f.arguments = args
            return f
        }
    }
}
