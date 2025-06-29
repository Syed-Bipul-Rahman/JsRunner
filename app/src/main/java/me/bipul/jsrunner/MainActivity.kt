package me.bipul.jsrunner

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.amrdeveloper.codeview.CodeView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var codeView: CodeView
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

        codeView = findViewById(R.id.editScript)
        tvOutput = findViewById(R.id.tvOutput)
        progressBar = findViewById(R.id.progressBar)
        val btnRun: Button = findViewById(R.id.btnRun)
        val btnClear: Button = findViewById(R.id.btnClear)

        timeoutExecutor = Executors.newSingleThreadScheduledExecutor()

        setupCodeView()
        initializeWebView()

        btnRun.setOnClickListener {
            if (!isExecuting) {
                val script = codeView.text.toString().trim()
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
            codeView.setText("")
        }
    }

    private fun setupCodeView() {
        // Set up JavaScript syntax highlighting
        codeView.apply {
            // Enable syntax highlighting
            setEnableLineNumber(true)
            setLineNumberTextColor(Color.GRAY)
            setLineNumberTextSize(12f)

            // Set colors for syntax highlighting
            setBackgroundColor(Color.parseColor("#282c34"))
            setTextColor(Color.parseColor("#abb2bf"))
            setTextSize(14f)

            // JavaScript keywords
            addSyntaxPattern(
                Pattern.compile("\\b(abstract|arguments|await|boolean|break|byte|case|catch|char|class|const|continue|debugger|default|delete|do|double|else|enum|eval|export|extends|false|final|finally|float|for|function|goto|if|implements|import|in|instanceof|int|interface|let|long|native|new|null|package|private|protected|public|return|short|static|super|switch|synchronized|this|throw|throws|transient|true|try|typeof|var|void|volatile|while|with|yield)\\b"),
                Color.parseColor("#c678dd")
            )

            // Numbers
            addSyntaxPattern(
                Pattern.compile("\\b\\d+(\\.\\d+)?\\b"),
                Color.parseColor("#d19a66")
            )

            // Strings
            addSyntaxPattern(
                Pattern.compile("\"([^\"\\\\]|\\\\.)*\""),
                Color.parseColor("#98c379")
            )
            addSyntaxPattern(
                Pattern.compile("'([^'\\\\]|\\\\.)*'"),
                Color.parseColor("#98c379")
            )
            addSyntaxPattern(
                Pattern.compile("`([^`\\\\]|\\\\.)*`"),
                Color.parseColor("#98c379")
            )

            // Comments
            addSyntaxPattern(
                Pattern.compile("//.*"),
                Color.parseColor("#5c6370")
            )
            addSyntaxPattern(
                Pattern.compile("/\\*[\\s\\S]*?\\*/"),
                Color.parseColor("#5c6370")
            )

            // Functions and methods
            addSyntaxPattern(
                Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()"),
                Color.parseColor("#61dafb")
            )

            // Operators
            addSyntaxPattern(
                Pattern.compile("[+\\-*/%=<>!&|^~?:]"),
                Color.parseColor("#56b6c2")
            )

            // Brackets and parentheses
            addSyntaxPattern(
                Pattern.compile("[\\[\\](){}]"),
                Color.parseColor("#abb2bf")
            )

            // Set up auto-completion
            setupAutoCompletion()

            // Set hint text
            hint = "Enter JavaScript code..."
        }
    }

    private fun setupAutoCompletion() {
        val suggestions = mutableListOf<String>()

        // JavaScript keywords
        suggestions.addAll(listOf(
            "abstract", "arguments", "await", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "debugger", "default", "delete", "do",
            "double", "else", "enum", "eval", "export", "extends", "false", "final",
            "finally", "float", "for", "function", "goto", "if", "implements", "import",
            "in", "instanceof", "int", "interface", "let", "long", "native", "new",
            "null", "package", "private", "protected", "public", "return", "short",
            "static", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "true", "try", "typeof", "var", "void", "volatile", "while",
            "with", "yield"
        ))

        // Built-in objects and methods
        suggestions.addAll(listOf(
            "console.log", "console.error", "console.warn", "console.info",
            "Array", "Object", "String", "Number", "Boolean", "Date", "Math",
            "JSON.parse", "JSON.stringify", "parseInt", "parseFloat", "isNaN",
            "setTimeout", "setInterval", "clearTimeout", "clearInterval",
            "length", "push", "pop", "shift", "unshift", "slice", "splice",
            "indexOf", "includes", "forEach", "map", "filter", "reduce",
            "toString", "valueOf", "hasOwnProperty", "constructor",
            "prototype", "call", "apply", "bind"
        ))

        // Common patterns
        suggestions.addAll(listOf(
            "function() {}", "if() {}", "for() {}", "while() {}", "try {} catch() {}",
            "switch() {}", "console.log()", "return", "document.getElementById",
            "addEventListener", "removeEventListener", "querySelector",
            "querySelectorAll", "createElement", "appendChild", "removeChild"
        ))

        // Convert to adapter and set suggestions (this may vary based on CodeView version)
        // For now, we'll just store the suggestions - the actual implementation
        // depends on the specific CodeView version you're using
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