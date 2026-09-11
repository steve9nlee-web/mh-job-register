package com.jobregister.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jobregister.app.util.PhotoUtil
import java.io.File

/**
 * Take-a-photo / pick-from-gallery pair of buttons. The image is downscaled
 * and compressed before it reaches [onPicked], so callers only ever handle
 * a JPEG small enough to upload over a phone connection.
 */
@Composable
fun PhotoPickerButtons(
    takeLabel: String = "📷 Take photo",
    uploadLabel: String = "🖼 Upload",
    onPicked: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) cameraUri?.let { uri -> PhotoUtil.compress(context, uri)?.let(onPicked) }
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { PhotoUtil.compress(context, it)?.let(onPicked) }
    }

    Row {
        OutlinedButton(onClick = {
            val file = File(context.cacheDir, "job_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            cameraUri = uri
            takePicture.launch(uri)
        }) { Text(takeLabel) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text(uploadLabel) }
    }
}
