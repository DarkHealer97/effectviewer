package com.effectviewer.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.effectviewer.R
import java.io.File

class FolderAdapter(
    private val onFolderClick: (File) -> Unit,
    private val onImageClick:  (File) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FolderItem>()

    companion object {
        private const val TYPE_VOLUME = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_IMAGE  = 2
    }

    fun setItems(newItems: List<FolderItem>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is FolderItem.Volume -> TYPE_VOLUME
        is FolderItem.Folder -> TYPE_FOLDER
        is FolderItem.Image  -> TYPE_IMAGE
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE  -> ImageVH(inf.inflate(R.layout.item_gallery, parent, false))
            else        -> FolderVH(inf.inflate(R.layout.item_folder, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FolderItem.Volume -> (holder as FolderVH).bind(item.file, item.label,   isVolume = true)
            is FolderItem.Folder -> (holder as FolderVH).bind(item.file, item.file.name, isVolume = false)
            is FolderItem.Image  -> (holder as ImageVH).bind(item.file)
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    inner class FolderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val icon:  ImageView = view.findViewById(R.id.folderIcon)
        private val label: TextView  = view.findViewById(R.id.folderName)

        fun bind(file: File, name: String, isVolume: Boolean) {
            label.text = name
            icon.setImageResource(
                if (isVolume) android.R.drawable.ic_menu_agenda
                else R.drawable.ic_folder
            )
            val click = View.OnClickListener { onFolderClick(file) }
            itemView.setOnClickListener(click)
            itemView.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == android.view.KeyEvent.KEYCODE_ENTER) &&
                    event.action == android.view.KeyEvent.ACTION_DOWN) {
                    onFolderClick(file); true
                } else false
            }
        }
    }

    inner class ImageVH(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.thumbImage)

        fun bind(file: File) {
            Glide.with(image).load(file).centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery).into(image)

            itemView.setOnClickListener { onImageClick(file) }
            itemView.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == android.view.KeyEvent.KEYCODE_ENTER) &&
                    event.action == android.view.KeyEvent.ACTION_DOWN) {
                    onImageClick(file); true
                } else false
            }
        }
    }
}
