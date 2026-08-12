package com.xmoney.payments.threeds

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import com.xmoney.payments.R
import com.xmoney.payments.config.Strings
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class ThreeDSDialog : DialogFragment() {
    private var resolved = false
    private var webView: WebView? = null
    private val popupWebViews = mutableListOf<WebView>()
    private var loadingOverlay: View? = null
    private var dotAnimators: AnimatorSet? = null

    var hostListener: ThreeDSListener? = null

    private val challengeUrl: String
        get() = requireArguments().getString(ARG_URL).orEmpty()

    private val locale: String
        get() = requireArguments().getString(ARG_LOCALE, "en")

    private val listener: ThreeDSListener?
        get() {
            hostListener?.let { return it }
            val act = activity ?: return null
            (act as? ThreeDSListener)?.let { return it }
            return act.supportFragmentManager.fragments
                .filterIsInstance<ThreeDSListener>()
                .firstOrNull()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_XMoney_ThreeDS)
        isCancelable = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.WHITE)
        }

        val challengeWebView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).also { it.topMargin = dp(HEADER_HEIGHT_DP) }
            setBackgroundColor(Color.WHITE)
            applySecureWebSettings(settings)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
            if (ThreeDSUrlAllowlist.isAllowed(challengeUrl)) {
                loadUrl(challengeUrl)
            } else {
                finish(false)
            }
        }
        webView = challengeWebView

        val overlay = buildLoadingOverlay(context).also { loadingOverlay = it }
        val header = buildHeader()

        root.addView(challengeWebView)
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            header,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(HEADER_HEIGHT_DP),
                Gravity.TOP,
            ),
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        return root
    }

    private fun buildLoadingOverlay(context: android.content.Context): View {
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(32), dp(32), dp(32), dp(24))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            this.gravity = Gravity.CENTER
        }

        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(24) }
        }

        val delays = longArrayOf(0L, 150L, 300L, 450L, 600L)
        val animators = mutableListOf<ObjectAnimator>()
        delays.forEachIndexed { index, delay ->
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(DOT_COLOR)
                }
                val params = LinearLayout.LayoutParams(dp(8), dp(8))
                if (index < delays.lastIndex) params.marginEnd = dp(8)
                layoutParams = params
            }
            dotsRow.addView(dot)
            listOf(
                ObjectAnimator.ofFloat(dot, View.ALPHA, 0.35f, 1f, 0.35f),
                ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.9f, 1f, 0.9f),
                ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.9f, 1f, 0.9f),
            ).forEach { animator ->
                animator.duration = 1200L
                animator.startDelay = delay
                animator.repeatCount = ObjectAnimator.INFINITE
                animator.interpolator = AccelerateDecelerateInterpolator()
                animators += animator
            }
        }
        AnimatorSet().apply {
            playTogether(animators.toList())
            start()
            dotAnimators = this
        }

        val text = TextView(context).apply {
            this.text = Strings.text("sheet.processingPayment", locale)
            textSize = 16f
            setTextColor(Color.parseColor("#6B6B6B"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        content.addView(dotsRow)
        content.addView(text)

        val brand = ImageView(context).apply {
            setImageResource(R.drawable.xmoney_ic_3ds_brand)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(89), dp(24)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            contentDescription = "xMoney"
        }

        overlay.addView(content)
        overlay.addView(brand)
        return overlay
    }

    private fun hideLoadingOverlay() {
        val overlay = loadingOverlay ?: return
        if (overlay.visibility != View.VISIBLE) return
        overlay.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                overlay.visibility = View.GONE
                dotAnimators?.cancel()
                dotAnimators = null
            }
            .start()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.white)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                finish(false)
                true
            } else {
                false
            }
        }
    }

    override fun onDestroyView() {
        dotAnimators?.cancel()
        dotAnimators = null
        destroyWebView(webView)
        webView = null
        popupWebViews.toList().forEach { destroyWebView(it) }
        popupWebViews.clear()
        loadingOverlay = null
        super.onDestroyView()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        finish(false)
    }

    fun dismissProgrammatically() {
        finish(true)
    }

    private fun buildHeader(): View {
        val context = requireContext()
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(HEADER_HEIGHT_DP),
            )
            setPadding(dp(16), 0, dp(16), 0)
        }

        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#16141A"))
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F2F2F2"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { finish(false) }
        }

        val title = TextView(context).apply {
            text = Strings.text("sheet.authentication", locale)
            textSize = 17f
            setTextColor(Color.parseColor("#16141A"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }

        header.addView(closeButton)
        header.addView(title)
        header.addView(spacer)
        return header
    }

    private fun createWebViewClient(): WebViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            hideLoadingOverlay()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val target = request?.url?.toString() ?: return false
            return handleNavigation(target)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null) return false
            return handleNavigation(url)
        }
    }

    private fun createWebChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(requireContext()).apply {
                applySecureWebSettings(settings)
                setBackgroundColor(Color.WHITE)
                webViewClient = createWebViewClient()
            }
            popupWebViews += popup
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }

    private fun handleNavigation(url: String): Boolean {
        if (!ThreeDSUrlAllowlist.isAllowed(url)) {
            return true
        }
        if (listener?.shouldInterceptThreeDSUrl(url) == true) {
            finish(true)
            return true
        }
        return false
    }

    private fun finish(success: Boolean) {
        if (resolved) return
        resolved = true
        listener?.onThreeDSFinished(success)
        dismissAllowingStateLoss()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "xmoney_threeds"
        private const val ARG_URL = "url"
        private const val ARG_LOCALE = "locale"
        private const val HEADER_HEIGHT_DP = 60
        private val DOT_COLOR = Color.parseColor("#7C4DFF")

        fun newInstance(url: String, locale: String): ThreeDSDialog = ThreeDSDialog().apply {
            arguments = bundleOf(ARG_URL to url, ARG_LOCALE to locale)
        }
    }
}

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
interface ThreeDSListener {
    fun shouldInterceptThreeDSUrl(url: String): Boolean
    fun onThreeDSFinished(success: Boolean)
}
