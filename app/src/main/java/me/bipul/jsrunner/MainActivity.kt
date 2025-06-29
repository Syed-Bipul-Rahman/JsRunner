package me.bipul.jsrunner

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var editScript: EditText
    private lateinit var tvOutput: TextView
    private lateinit var progressBar: ProgressBar
    private var isExecuting = false
    private val executionTimeout = 5000L
    private lateinit var timeoutExecutor: ScheduledExecutorService
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editScript = findViewById(R.id.editScript)
        tvOutput = findViewById(R.id.tvOutput)
        progressBar = findViewById(R.id.progressBar)
        val btnRun: Button = findViewById(R.id.btnRun)
        val btnClear: Button = findViewById(R.id.btnClear)

        timeoutExecutor = Executors.newSingleThreadScheduledExecutor()

        initializeWebView()

        btnRun.setOnClickListener {
            if (!isExecuting) {
                val script = editScript.text.toString().trim()
                when {
                    script.isEmpty() -> tvOutput.append("\n[Info]: Please enter JavaScript code.")
                    script.length > 10000 -> tvOutput.append("\n[Error]: Script too large (max 10,000 characters).")
                    else -> {
                        isExecuting = true
                        progressBar.visibility = ProgressBar.VISIBLE
                        executeJS(script)
                    }
                }
            } else {
                tvOutput.append("\n[Info]: Execution in progress. Please wait.")
            }
        }

        btnClear.setOnClickListener {
            tvOutput.text = ""
            editScript.text.clear()
        }
    }

    private fun initializeWebView() {
        handler.post {
            webView = WebView(this@MainActivity).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    setSupportZoom(false)
                }
                addJavascriptInterface(JsBridge(), "JsBridge")
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        runOnUiThread {
                            tvOutput.append("\n[WebView Error]: ${error?.description}")
                            isExecuting = false
                            progressBar.visibility = ProgressBar.GONE
                        }
                    }
                }
                webChromeClient = WebChromeClient()

                loadUrl(
                    """
                    javascript:(function() {
                        var originalLog = console.log;
                        var originalError = console.error;
                        var originalWarn = console.warn;
                        console.log = function(msg) {
                            JsBridge.onConsoleLog(String(msg));
                            return originalLog.apply(console, arguments);
                        };
                        console.error = function(msg) {
                            JsBridge.onError(String(msg));
                            return originalError.apply(console, arguments);
                        };
                        console.warn = function(msg) {
                            JsBridge.onConsoleLog('[Warn]: ' + String(msg));
                            return originalWarn.apply(console, arguments);
                        };
                    })()
                    """.trimIndent()
                )
            }
        }
    }

    private fun executeJS(script: String) {
        // Enhanced escaping to handle quotes and special characters
        val cleanedScript = script
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val wrappedScript = """
            (function() {
                var startTime = Date.now();
                try {
                    var result = eval("$cleanedScript");
                    if (Date.now() - startTime > $executionTimeout) {
                        throw new Error("Execution timeout");
                    }
                    return JSON.stringify(result, (key, value) => {
                        if (typeof value === 'function') return '[Function]';
                        if (value instanceof Error) return value.toString();
                        if (value === undefined) return 'undefined';
                        return value;
                    });
                } catch (e) {
                    JsBridge.onError(e.message || String(e));
                    return undefined;
                }
            })()
        """.trimIndent()

        handler.post {
            webView.evaluateJavascript(wrappedScript) { result ->
                runOnUiThread {
                    isExecuting = false
                    progressBar.visibility = ProgressBar.GONE
                    if (result == "null" || result == "undefined") {
                        tvOutput.append("\n[Result]: undefined")
                    } else {
                        try {
                            tvOutput.append("\n[Result]: $result")
                        } catch (e: Exception) {
                            tvOutput.append("\n[Error]: Failed to parse result: ${e.message}")
                        }
                    }
                }
            }
        }

        timeoutExecutor.schedule({
            runOnUiThread {
                if (isExecuting) {
                    isExecuting = false
                    progressBar.visibility = ProgressBar.GONE
                    tvOutput.append("\n[Error]: Execution timed out after ${executionTimeout}ms")
                    webView.stopLoading()
                }
            }
        }, executionTimeout, TimeUnit.MILLISECONDS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.destroy()
        timeoutExecutor.shutdown()
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onResult(result: String?) {
            runOnUiThread {
                isExecuting = false
                progressBar.visibility = ProgressBar.GONE
                tvOutput.append("\n[Result]: ${result ?: "undefined"}")
            }
        }

        @JavascriptInterface
        fun onError(error: String?) {
            runOnUiThread {
                isExecuting = false
                progressBar.visibility = ProgressBar.GONE
                tvOutput.append("\n[Error]: ${error ?: "Unknown error"}")
            }
        }

        @JavascriptInterface
        fun onConsoleLog(message: String?) {
            runOnUiThread {
                tvOutput.append("\n[Log]: ${message ?: "undefined"}")
            }
        }
    }
}