package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val CHANNEL_ID = "ma_traders_notifications"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A3A5F))
                        .safeDrawingPadding()
                ) {
                    AppWebView()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "M.A TRADERS Alerts"
            val descriptionText = "Notifications for stock, invoices, and khata updates"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun AppWebView() {
        val context = this@MainActivity

        BackHandler(enabled = true) {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            } else {
                finish()
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus(View.FOCUS_DOWN)
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                                if (!v.hasFocus()) {
                                    v.requestFocus()
                                }
                            }
                        }
                        false
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    // Android Native Interface
                    addJavascriptInterface(WebAppInterface(context, this), "AndroidApp")

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("https://wa.me/") ||
                                url.startsWith("https://api.whatsapp.com/") ||
                                url.startsWith("whatsapp://")
                            ) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                    return true
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "WhatsApp not installed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return true
                                }
                            } else if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                    return true
                                } catch (e: Exception) {
                                    return false
                                }
                            }
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                    loadUrl("file:///android_asset/index.html")
                    webView = this
                }
            },
            update = { view ->
                webView = view
            }
        )
    }

    inner class WebAppInterface(private val context: Context, private val webView: WebView) {
        @JavascriptInterface
        fun printDocument(title: String) {
            runOnUiThread {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter(title)
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
                printManager?.print(title, printAdapter, printAttributes)
            }
        }

        @JavascriptInterface
        fun shareWhatsApp(phone: String, message: String) {
            runOnUiThread {
                try {
                    val cleanPhone = phone.replace("+", "").replace("-", "").replace(" ", "").trim()
                    val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun sendNativeNotification(title: String, message: String) {
            runOnUiThread {
                try {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent: PendingIntent = PendingIntent.getActivity(
                        context, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)

                    with(NotificationManagerCompat.from(context)) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        ) {
                            notify(System.currentTimeMillis().toInt(), builder.build())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
