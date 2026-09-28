package com.goldmedal.aillm.ui.image

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.core.database.GeneratedImageDao
import com.goldmedal.aillm.core.database.GeneratedImageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * The gallery: every picture the image screen has produced, newest first.
 *
 * The rows come from Room and the pixels from app-private storage, so the list
 * survives restarts and never depends on a storage permission. Deleting a row
 * deletes its file; there is no separate cleanup step that could be forgotten.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generatedImageDao: GeneratedImageDao
) : ViewModel() {

    val images: StateFlow<List<GeneratedImageEntity>> = generatedImageDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The picture opened in the preview overlay, if any. */
    private val _preview = MutableStateFlow<GeneratedImageEntity?>(null)
    val preview: StateFlow<GeneratedImageEntity?> = _preview.asStateFlow()

    /** A one-line result for the snackbar. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun fileOf(image: GeneratedImageEntity): File? = GeneratedImageFiles.file(context, image.fileName)

    fun open(image: GeneratedImageEntity) {
        _preview.value = image
    }

    fun closePreview() {
        _preview.value = null
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun delete(image: GeneratedImageEntity) {
        if (_preview.value?.id == image.id) _preview.value = null
        GeneratedImageFiles.delete(context, image.fileName)
        viewModelScope.launch {
            runCatching { generatedImageDao.deleteById(image.id) }
            _notice.value = "Deleted"
        }
    }

    /** Publishes [image] to `Pictures/AILLM`. */
    fun saveToGallery(image: GeneratedImageEntity) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                GeneratedImageFiles.loadBitmap(context, image.fileName)
                    ?.let { GeneratedImageStore.saveToGallery(context, it) }
            }
            _notice.value =
                if (saved != null) "Saved to Pictures/AILLM" else "Could not save the image"
        }
    }

    /**
     * Stages [image] for sharing and returns the intent to launch, or null if
     * it could not be prepared. Decoding and the cache write happen off the
     * main thread, because launching the chooser must not wait on them.
     */
    suspend fun shareIntent(image: GeneratedImageEntity): Intent? = withContext(Dispatchers.IO) {
        val bitmap = GeneratedImageFiles.loadBitmap(context, image.fileName) ?: return@withContext null
        GeneratedImageStore.shareIntent(context, bitmap)
    }

    fun report(message: String) {
        _notice.value = message
    }
}
