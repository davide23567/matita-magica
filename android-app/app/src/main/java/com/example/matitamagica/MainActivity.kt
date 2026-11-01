package com.example.matitamagica

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraOutputUri
        filePathCallback?.onReceiveValue(if (success && uri != null) arrayOf(uri) else null)
        filePathCallback = null
        cameraOutputUri = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            val photoFile = File.createTempFile("drawing_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            cameraOutputUri = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    // Se la pagina richiede la fotocamera (capture), apri direttamente lo scatto
                    if (fileChooserParams?.isCaptureEnabled == true) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        return true
                    }

                    // Altrimenti selettore file / galleria
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                    }
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        return false
                    }
                    return true
                }
            }
        }

        setContentView(webView)

        // Load the bundled HTML app
        webView.loadUrl("file:///android_asset/index.html")

        // Handle Android Back Navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function() { " +
                    "  const viewer = document.getElementById('photoViewerModal'); " +
                    "  if (viewer && viewer.classList.contains('active')) { " +
                    "    closeFullPhoto(); return true; " +
                    "  } " +
                    "  const source = document.getElementById('photoSourceModal'); " +
                    "  if (source && source.classList.contains('active')) { " +
                    "    closePhotoSource(); return true; " +
                    "  } " +
                    "  const settings = document.getElementById('settingsModal'); " +
                    "  if (settings && settings.classList.contains('active')) { " +
                    "    closeSettingsModal(); return true; " +
                    "  } " +
                    "  const congrats = document.getElementById('congratsModal'); " +
                    "  if (congrats && congrats.classList.contains('active')) { " +
                    "    closeCelebrationModal(); return true; " +
                    "  } " +
                    "  const player = document.getElementById('viewPlayer'); " +
                    "  if (player && player.classList.contains('active')) { " +
                    "    switchView('home'); return true; " +
                    "  } " +
                    "  const gallery = document.getElementById('viewGallery'); " +
                    "  if (gallery && gallery.classList.contains('active')) { " +
                    "    switchView('home'); return true; " +
                    "  } " +
                    "  return false; " +
                    "})()"
                ) { result ->
                    if (result != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
