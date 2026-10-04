package dev.ujhhgtg.via.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** x5.e: Android 36 uses PickVisualMedia, including its ClipData result fallback; older uses PICK. */
class OriginalImagePickerContract : ActivityResultContract<Unit?, Uri?>() {
    private val picker = if (Build.VERSION.SDK_INT >= 36) ActivityResultContracts.PickVisualMedia() else null
    override fun createIntent(context: Context, input: Unit?): Intent = picker?.createIntent(context,
        PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
        ?: Intent(Intent.ACTION_PICK).setType("image/*")

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        picker?.parseResult(resultCode, intent) ?: if (picker == null && resultCode == Activity.RESULT_OK) intent?.data else null
}
