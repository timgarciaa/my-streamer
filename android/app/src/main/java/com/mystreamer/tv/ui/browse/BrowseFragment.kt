package com.mystreamer.tv.ui.browse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import com.mystreamer.tv.PlayerActivity
import com.mystreamer.tv.R
import com.mystreamer.tv.data.model.FileItem
import com.mystreamer.tv.data.network.RetrofitClient
import com.mystreamer.tv.data.prefs.ServerPreferences
import kotlinx.coroutines.launch

class BrowseFragment : VerticalGridSupportFragment() {

    companion object {
        private const val TAG = "BrowseFragment"
        const val ARG_PATH = "path"
        private const val NUM_COLUMNS = 5
    }

    private lateinit var serverPrefs: ServerPreferences
    private lateinit var gridAdapter: ArrayObjectAdapter
    private var currentPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverPrefs = ServerPreferences(requireContext())
        currentPath = arguments?.getString(ARG_PATH) ?: ""

        title = if (currentPath.isEmpty()) getString(R.string.browse_title) else currentPath.substringAfterLast('/')

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM)
        gridPresenter.numberOfColumns = NUM_COLUMNS
        setGridPresenter(gridPresenter)

        gridAdapter = ArrayObjectAdapter(FileItemPresenter())
        adapter = gridAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val fileItem = item as FileItem
            if (fileItem.isDirectory) {
                val fragment = BrowseFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_PATH, fileItem.relativePath)
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.main_frame, fragment)
                    .addToBackStack(null)
                    .commit()
            } else if (fileItem.isVideo) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_PATH, fileItem.relativePath)
                    putExtra(PlayerActivity.EXTRA_TITLE, fileItem.name)
                }
                startActivity(intent)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadFiles()
    }

    private fun loadFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.browse(currentPath)
                gridAdapter.clear()
                gridAdapter.addAll(0, response.items)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading files for path='$currentPath'", e)
            }
        }
    }
}
