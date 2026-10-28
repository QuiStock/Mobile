package com.quistock.quistock.presentation.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal class ProfilePhotoSources(
    private val fragment: Fragment,
    private val model: () -> EditProfileViewModel,
) {
    private val gallery = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected(it) }
    }
    private val camera = fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = model().cameraPath
        if (success && path != null) {
            selected(Uri.fromFile(File(path)), camera = true)
        } else {
            model().clearCamera()
        }
    }

    fun open() {
        val state = model().state.value
        if (state.busy || state.pending != null) return
        val actions = intArrayOf(
            R.string.profile_choose_gallery, R.string.profile_take_photo,
            R.string.profile_remove_photo, R.string.profile_open_settings,
        )
        AlertDialog.Builder(fragment.requireContext())
            .setItems(actions.map { fragment.getString(it) }.toTypedArray()) { _, index ->
                when (index) {
                    0 -> launch(R.string.profile_gallery_unavailable) { gallery.launch("image/*") }
                    1 -> launchCamera()
                    2 -> model().removePhoto()
                    3 -> launch(R.string.profile_permission) {
                        val context = fragment.requireContext()
                        fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}")))
                    }
                }
            }.show()
    }

    fun cleanInterruptedCamera() {
        val directory = File(fragment.requireContext().cacheDir, CAMERA_DIRECTORY)
        directory.listFiles()?.filter { it.absolutePath != model().cameraPath }?.forEach { it.delete() }
    }

    private fun launchCamera() = launch(R.string.profile_camera_unavailable) {
        if (!model().uploadAvailable) {
            feedback(R.string.profile_upload_unavailable)
        } else {
            val context = fragment.requireContext()
            model().clearCamera()
            val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
            val file = File.createTempFile("capture-", ".jpg", directory)
            model().cameraPath = file.absolutePath
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.profile.photos", file))
        }
    }

    private fun selected(uri: Uri, camera: Boolean = false) {
        val resolver = fragment.requireContext().applicationContext.contentResolver
        val viewModel = model()
        viewModel.prepare {
            withContext(Dispatchers.IO) {
                try {
                    resolver.openInputStream(uri)?.use(::readBounded)
                } catch (_: IOException) {
                    null
                } finally {
                    if (camera) viewModel.clearCamera()
                }
            }
        }
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = stream.read(buffer)
        while (read >= 0 && output.size() <= MAX_INPUT) {
            output.write(buffer, 0, read)
            read = stream.read(buffer)
        }
        return output.toByteArray()
    }

    private fun launch(error: Int, action: () -> Unit) {
        try {
            if (!model().uploadAvailable && error != R.string.profile_permission) {
                feedback(R.string.profile_upload_unavailable)
            } else {
                action()
            }
        } catch (_: ActivityNotFoundException) {
            model().clearCamera()
            feedback(error)
        } catch (_: SecurityException) {
            model().clearCamera()
            feedback(R.string.profile_permission)
        } catch (_: IOException) {
            model().clearCamera()
            feedback(error)
        }
    }

    private fun feedback(message: Int) {
        fragment.view?.let { PopupFeedback.showStatus(it, fragment.getString(message), isError = true) }
    }

    private companion object {
        const val CAMERA_DIRECTORY = "profile-camera"
        const val MAX_INPUT = 10 * 1024 * 1024
    }
}
