package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** x5.e: ACTION_PICK before API36, the image-only photo picker beginning with API36. */
class ScannerImageContract : ActivityResultContract<Unit, Uri?>() {
    private val picker = ActivityResultContracts.PickVisualMedia()
    override fun createIntent(context: Context, input: Unit): Intent =
        if (Build.VERSION.SDK_INT >= 36) picker.createIntent(context,
            PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
        else Intent(Intent.ACTION_PICK).setType("image/*")
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (Build.VERSION.SDK_INT >= 36) picker.parseResult(resultCode, intent)
        else if (resultCode == Activity.RESULT_OK) intent?.data else null
}
