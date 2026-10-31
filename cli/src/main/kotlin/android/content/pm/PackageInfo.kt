package android.content.pm

import android.os.Bundle
import java.io.File

class PackageInfo {
    var packageName: String = ""
    var versionName: String? = null
    var versionCode: Int = 0
    var applicationInfo: ApplicationInfo? = null
}

class ApplicationInfo {
    var packageName: String = ""
    var metaData: Bundle? = Bundle()
    var sourceDir: String = ""
    var dataDir: String = ""
}

class PackageManager {
    fun getApplicationLabel(info: ApplicationInfo): CharSequence = info.packageName
}
