package com.actum.ui.act

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actum.ui.common.Grabber
import com.actum.ui.home.monoStyle
import com.actum.ui.home.sans
import com.actum.ui.home.serif
import com.actum.ui.theme.BoneDim

/**
 * The proof that nothing leaves the phone: the permissions this installed app
 * asks Android for, read from Android now, not written here. Without INTERNET
 * the system gives the app no way to open a connection.
 */
@Composable
fun ColumnScope.ProofSheet() {
    val context = LocalContext.current
    val requested = remember {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()
    }
    val online = Manifest.permission.INTERNET in requested
    Grabber(Modifier.align(Alignment.CenterHorizontally))
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Privacy", style = serif(26.sp, lineHeight = 30.sp))
        Text(
            if (online) "This build asks for internet access." else "Actum cannot reach the internet.",
            style = sans(18.sp, if (online) Missing else Verified, FontWeight.SemiBold, lineHeight = 24.sp),
        )
        Text(
            if (online) {
                "android.permission.INTERNET is among the permissions below."
            } else {
                "Android only lets an app go online if it asks for the INTERNET permission. " +
                    "Actum does not ask for it, so search, reading pictures and the Gemma model all run on this phone. " +
                    "An event you confirm opens in your calendar app, which may sync it itself. " +
                    "The microphone is used only while you are speaking to Actum, and your voice is turned into words " +
                    "by Android's on-device recognizer."
            },
            style = sans(15.sp, BoneDim, lineHeight = 22.sp),
        )
        Text("what this app asks android for, read just now", style = monoStyle(11.sp, BoneDim))
        if (requested.isEmpty()) Text("nothing", style = monoStyle(12.sp, BoneDim))
        requested.forEach { Text(it, style = monoStyle(12.sp, BoneDim, lineHeight = 18.sp)) }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}
