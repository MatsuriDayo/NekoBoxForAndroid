package io.nekohasekai.sagernet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.google.zxing.Result
import com.king.zxing.CameraScan
import com.king.zxing.DefaultCameraScan
import com.king.zxing.analyze.QRCodeAnalyzer
import com.king.zxing.util.CodeUtils
import com.king.zxing.util.LogUtils
import com.king.zxing.util.PermissionUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.databinding.LayoutScannerBinding
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


class ScannerActivity : ThemedActivity(),
    CameraScan.OnScanResultCallback {

    lateinit var binding: LayoutScannerBinding
    lateinit var cameraScan: CameraScan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 25) getSystemService<ShortcutManager>()!!.reportShortcutUsed("scan")
        binding = LayoutScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        // 二维码库
        initCameraScan()
        startCamera()
        binding.ivFlashlight.setOnClickListener { toggleTorchState() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scanner_menu, menu)
        return true
    }

    val importCodeFile = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        runOnDefaultDispatcher {
            try {
                it.forEachTry { uri ->
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(
                                contentResolver, uri
                            )
                        ) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(
                            contentResolver, uri
                        )
                    }
                    val result = CodeUtils.parseCodeResult(bitmap)
                    onMainDispatcher {
                        onScanResultCallback(result, true)
                    }
                }
                finish()
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    Toast.makeText(app, e.readableMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_import_file) {
            startFilesForResult(importCodeFile, "image/*")
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    var finished = AtomicBoolean(false)
    var importedN = AtomicInteger(0)

    /**
     * 接收扫码结果回调
     * @param result 扫码结果
     * @return 返回true表示拦截，将不自动执行后续逻辑，为false表示不拦截，默认不拦截
     */
    override fun onScanResultCallback(result: Result?): Boolean {
        return onScanResultCallback(result, false)
    }

    fun onScanResultCallback(result: Result?, multi: Boolean): Boolean {
        if (!multi && finished.getAndSet(true)) return true
        if (!multi) finish()
        runOnDefaultDispatcher {
            try {
                val text = result?.text ?: throw Exception("QR code not found")
                val results = RawUpdater.parseRaw(text)
                if (!results.isNullOrEmpty()) {
                    val currentGroupId = DataStore.selectedGroupForImport()
                    if (DataStore.selectedGroup != currentGroupId) {
                        DataStore.selectedGroup = currentGroupId
                    }

                    for (profile in results) {
                        ProfileManager.createProfile(currentGroupId, profile)
                        importedN.addAndGet(1)
                    }
                } else {
                    onMainDispatcher {
                        Toast.makeText(app, R.string.action_import_err, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SubscriptionFoundException) {
                startActivity(Intent(this@ScannerActivity, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = e.link.toUri()
                })
            } catch (e: Throwable) {
                Logs.w(e)
                onMainDispatcher {
                    var text = getString(R.string.action_import_err)
                    text += "\n" + e.readableMessage
                    Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    /**
     * 初始化CameraScan
     */
    fun initCameraScan() {
        cameraScan = DefaultCameraScan(this, binding.previewView)
        cameraScan.setAnalyzer(QRCodeAnalyzer())
        cameraScan.setOnScanResultCallback(this)
        cameraScan.setNeedAutoZoom(true)
    }

    /**
     * 启动相机预览
     */
    fun startCamera() {
        if (PermissionUtils.checkPermission(this, Manifest.permission.CAMERA)) {
            cameraScan.startCamera()
        } else {
            LogUtils.d("checkPermissionResult != PERMISSION_GRANTED")
            PermissionUtils.requestPermission(
                this, Manifest.permission.CAMERA, CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * 释放相机
     */
    private fun releaseCamera() {
        cameraScan.release()
    }

    /**
     * 切换闪光灯状态（开启/关闭）
     */
    protected fun toggleTorchState() {
        val isTorch = cameraScan.isTorchEnabled
        cameraScan.enableTorch(!isTorch)
        binding.ivFlashlight.isSelected = !isTorch
    }

    val CAMERA_PERMISSION_REQUEST_CODE = 0X86

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            requestCameraPermissionResult(permissions, grantResults)
        }
    }

    /**
     * 请求Camera权限回调结果
     * @param permissions
     * @param grantResults
     */
    fun requestCameraPermissionResult(permissions: Array<String>, grantResults: IntArray) {
        if (PermissionUtils.requestPermissionsResult(
                Manifest.permission.CAMERA, permissions, grantResults
            )
        ) {
            startCamera()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        releaseCamera()
        super.onDestroy()
        if (importedN.get() > 0) {
            var text = getString(R.string.action_import_msg)
            text += "\n" + importedN.get() + " profile(s)"
            Toast.makeText(app, text, Toast.LENGTH_LONG).show()
        }
    }
}