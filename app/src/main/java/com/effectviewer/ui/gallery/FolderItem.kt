package com.effectviewer.ui.gallery

import java.io.File

sealed class FolderItem {
    data class Volume(val file: File, val label: String) : FolderItem()
    data class Folder(val file: File) : FolderItem()
    data class Image(val file: File)  : FolderItem()
}
