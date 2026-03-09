package com.mystreamer.tv.ui.browse

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.mystreamer.tv.R
import com.mystreamer.tv.data.model.FileItem

class FileItemPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_file_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val fileItem = item as? FileItem ?: return
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.card_title).text = fileItem.name
        view.findViewById<ImageView>(R.id.card_icon).setImageResource(
            if (fileItem.isDirectory) R.drawable.ic_folder else R.drawable.ic_video
        )
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // no-op
    }
}
