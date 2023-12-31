package moe.fuqiuluo.unidbg.env

import com.github.unidbg.Emulator
import com.github.unidbg.file.FileResult
import com.github.unidbg.file.linux.AndroidFileIO
import com.github.unidbg.linux.android.AndroidResolver
import com.github.unidbg.linux.file.ByteArrayFileIO
import com.github.unidbg.linux.file.DirectoryFileIO
import com.github.unidbg.linux.file.SimpleFileIO
import com.github.unidbg.unix.UnixEmulator
import moe.fuqiuluo.ext.hex2ByteArray
import moe.fuqiuluo.unidbg.QSecVM
import moe.fuqiuluo.unidbg.env.files.fetchCpuInfo
import moe.fuqiuluo.unidbg.env.files.fetchMemInfo
import moe.fuqiuluo.unidbg.env.files.fetchStat
import moe.fuqiuluo.unidbg.env.files.fetchStatus
import org.slf4j.LoggerFactory
import top.mrxiaom.qsign.CommonConfig
import java.io.File
import java.util.UUID

class FileResolver(
    sdk: Int,
    val vm: QSecVM
): AndroidResolver(sdk) {
    private val tmpFilePath = vm.coreLibPath
    private val uuid = UUID.randomUUID()
    companion object {
        val logger = LoggerFactory.getLogger("FileResolver")

        fun getAppInstallFolder(packageName: String): String {
            return CommonConfig.appInstallFolder.replace("\${packageName}", packageName)
        }
    }
    init {
        for (s in arrayOf("stdin", "stdout", "stderr")) {
            tmpFilePath.resolve(s).also {
                if (it.exists()) it.delete()
            }
        }
    }

    override fun resolve(emulator: Emulator<AndroidFileIO>, path: String, oflags: Int): FileResult<AndroidFileIO>? {
        val result = super.resolve(emulator, path, oflags)
        if (result == null || !result.isSuccess) {
            return this.resolve(emulator, path, oflags, result)
        }
        return result
    }

    private fun resolve(emulator: Emulator<AndroidFileIO>, path: String, oflags: Int, def: FileResult<AndroidFileIO>?): FileResult<AndroidFileIO>? {
        if (path == "stdin" || path == "stdout" || path == "stderr") {
            return FileResult.success(SimpleFileIO(oflags, tmpFilePath.resolve(path).also {
                if (!it.exists()) it.createNewFile()
            }, path))
        }

        if (CommonConfig.virtualRootPath != null) {
            val dataFile = File(CommonConfig.virtualRootPath, path)
            if (dataFile.exists()) {
                if (dataFile.isFile) {
                    return FileResult.success(SimpleFileIO(oflags, dataFile, path))
                }
                if (dataFile.isDirectory) {
                    return FileResult.success(DirectoryFileIO(oflags, path, dataFile))
                }
            }
        }

        if (path == "/data/data/com.tencent.tim/lib/libwtecdh.so") {
            return FileResult.failed(UnixEmulator.ENOENT)
        }


        if (path == "/proc/sys/kernel/random/boot_id") {
            return FileResult.success(ByteArrayFileIO(oflags, path, uuid.toString().toByteArray()))
        }
        if (path == "/proc/self/status") {
            return FileResult.success(ByteArrayFileIO(oflags, path, fetchStatus(emulator.pid).toByteArray()))
        }
        if (path == "/proc/stat") {
            return FileResult.success(ByteArrayFileIO(oflags, path, fetchStat()))
        }
        if (path == "/proc/meminfo") {
            return FileResult.success(ByteArrayFileIO(oflags, path, fetchMemInfo()))
        }
        if (path == "/proc/cpuinfo") {
            return FileResult.success(ByteArrayFileIO(oflags, path, fetchCpuInfo()))
        }
        if (path == "/dev/__properties__") {
            return FileResult.success(DirectoryFileIO(oflags, path,
                DirectoryFileIO.DirectoryEntry(true, "properties_serial"),
                DirectoryFileIO.DirectoryEntry(true, "property_info"),
            ))
        }

        if ("/proc/self/maps" == path) {
            return FileResult.success(ByteArrayFileIO(oflags, path, byteArrayOf()))
        }

        if (path == "/system/lib") {
            return FileResult.success(DirectoryFileIO(oflags, path,
                DirectoryFileIO.DirectoryEntry(true, "libhwui.so"),
            ))
        }

        if (path == "/data/data/com.tencent.mobileqq") {
            return FileResult.success(DirectoryFileIO(oflags, path,
                DirectoryFileIO.DirectoryEntry(false, "files"),
                DirectoryFileIO.DirectoryEntry(false, "shared_prefs"),
                DirectoryFileIO.DirectoryEntry(false, "cache"),
                DirectoryFileIO.DirectoryEntry(false, "code_cache"),
            ))
        }

        if (path == "/dev/urandom" ||
            path == "/data/local/su" ||
            path == "/data/local/bin/su" ||
            path == "/data/local/xbin/su" ||
            path == "/sbin/su" ||
            path == "/su/bin/su" ||
            path == "/system/bin/su" ||
            path == "/system/bin/.ext/su" ||
            path == "/system/bin/failsafe/su" ||
            path == "/system/sd/xbin/su" ||
            path == "/system/usr/we-need-root/su" ||
            path == "/system/xbin/su" ||
            path == "/cache/su" ||
            path == "/data/su" ||
            path == "/dev/su" ||
            path.contains("busybox") ||
            path.contains("magisk") ||
            path.contains("supolicy")
        ) {
            return FileResult.failed(UnixEmulator.ENOENT)
        }

        if (path == "/sdcard/Android/" || path == "/storage/emulated/0/Android/") {
            return FileResult.success(DirectoryFileIO(oflags, path,
                DirectoryFileIO.DirectoryEntry(false, "data"),
                DirectoryFileIO.DirectoryEntry(false, "obb"),
            ))
        }

        if (path == "/system/lib64/libhoudini.so" || path == "/system/lib/libhoudini.so") {
            return FileResult.failed(UnixEmulator.ENOENT)
        }

        if (path == "/proc/self/cmdline"
            || path == "/proc/${emulator.pid}/cmdline"
            || path == "/proc/stat/cmdline" // an error case
        ) {
            //if (vm.envData.packageName == "com.tencent.tim") {
            return FileResult.success(ByteArrayFileIO(oflags, path, "${vm.envData.packageName}:MSF".toByteArray()))
            //} else {
            //    return FileResult.success(ByteArrayFileIO(oflags, path, "${vm.envData.packageName}:MSF".toByteArray()))
            //}
        }

        if (path == "/data/data") {
            return FileResult.failed(UnixEmulator.EACCES)
        }

        if (path.contains("star_est.xml")) {
            return FileResult.success(ByteArrayFileIO(oflags, path, """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="id">NS23gm77vjYiyYK554L4aY0SYG5Xgjje</string>
            </map>
            """.trimIndent().toByteArray()))
        }

        if (path == "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq") {
            return FileResult.success(ByteArrayFileIO(oflags, path, "1804800".toByteArray()))
        }

        if (path == "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq") {
            return FileResult.success(ByteArrayFileIO(oflags, path, "300000".toByteArray()))
        }

        if (path == "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq") {
            return FileResult.success(ByteArrayFileIO(oflags, path, "1804800".toByteArray()))
        }

        if (path == "/sys/devices/soc0/serial_number") {
            return FileResult.success(ByteArrayFileIO(oflags, path, CommonConfig.serialNumber.toByteArray()))
        }

        if (path == "/proc") {
            return FileResult.success(DirectoryFileIO(oflags, path,
                DirectoryFileIO.DirectoryEntry(false, emulator.pid.toString()),
                DirectoryFileIO.DirectoryEntry(false, "self"),
                DirectoryFileIO.DirectoryEntry(true, "cpuinfo"),
                DirectoryFileIO.DirectoryEntry(true, "meminfo"),
                DirectoryFileIO.DirectoryEntry(true, "stat"),
                DirectoryFileIO.DirectoryEntry(true, "version"),
            ))
        }

        val appInstallFolder = getAppInstallFolder(vm.envData.packageName)

        if (path == "$appInstallFolder/lib/arm64") {
            val fileList = (tmpFilePath.listFiles { it -> it.name.endsWith(".so") }?.map {
                DirectoryFileIO.DirectoryEntry(true, it.name)
            } ?: emptyList()).toTypedArray()
            return FileResult.success(DirectoryFileIO(oflags, path, *fileList))
        }

        if(path == "$appInstallFolder/base.apk") {
            val f = tmpFilePath.resolve("QQ.apk")
            if (f.exists()) {
                return FileResult.success(SimpleFileIO(oflags, tmpFilePath.resolve("QQ.apk").also {
                    if (!it.exists()) it.createNewFile()
                }, path))
            } else {
                return FileResult.failed(UnixEmulator.ENOENT)
            }
        }

        if (path == "$appInstallFolder/lib/arm64/libfekit.so") {
            return FileResult.success(SimpleFileIO(oflags, tmpFilePath.resolve("libfekit.so"), path))
        }

        if (path == "$appInstallFolder/lib/arm64/libwtecdh.so") {
            tmpFilePath.resolve("libwtecdh.so").let {
                if (it.exists()) {
                    return FileResult.success(SimpleFileIO(oflags, it, path))
                }
            }
        }

        if (path == "/system/bin/sh" || path == "/system/bin/ls" || path == "/system/lib/libc.so") {
            return FileResult.success(ByteArrayFileIO(oflags, path, byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01, 0x01, 0x00)))
        }

        if (path.startsWith("/data/user/")) {
            if (path != "/data/user/0" && path != "/data/user/999") {
                return FileResult.failed(UnixEmulator.ENOENT)
            } else {
                return FileResult.failed(UnixEmulator.EACCES)
            }
        }

        // 位置: /storage/emulated/0/Android/.android_lq
        if (path.contains("system_android_l2") || path.contains("android_lq")) {
            val newPath = if (path.startsWith("C:")) path.substring(2) else path
            val file = tmpFilePath.resolve(".system_android_l2")
            if (!file.exists()) {
                file.writeBytes("613E7F36143E459381A20F92C04C71E7DF53D0197863ACD2CFC31D7D87D34CB96E1CB97AC6530FE75E779465CF0D682420DEC56A368C8D25FFA22C4E005AD7DB04".hex2ByteArray())
            }
            return FileResult.success(SimpleFileIO(oflags, file, newPath))
        }


        logger.warn("Couldn't find file: $path")
        return def
    }
}