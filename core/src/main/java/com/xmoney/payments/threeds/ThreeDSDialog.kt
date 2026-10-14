package com.xmoney.payments.threeds

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.xmoney.payments.R
import com.xmoney.payments.config.Strings
import com.xmoney.payments.engine.ThreeDSChallengeEnd
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class ThreeDSDialog : Fragment() {
    private var resolved = false
    private var notifiedShown = false
    /** Restored by the system after recreation. No host listens to it, so it removes itself. */
    private var orphaned = false
    private var handedOff = false
    private var backgrounded = false
    private var securedWindow = false
    private var hadSecureFlag = false
    private var webView: WebView? = null
    private var rootView: FrameLayout? = null
    private val popupWebViews = mutableListOf<WebView>()
    private var loadingOverlay: View? = null
    private var handoffBanner: View? = null
    private var dotAnimators: AnimatorSet? = null
    private val hiddenDialogs = mutableListOf<DialogFragment>()
    private val openDialogs = mutableListOf<Dialog>()

    var hostListener: ThreeDSListener? = null

    private val challengeUrl: String
        get() = requireArguments().getString(ARG_URL).orEmpty()

    private val locale: String
        get() = requireArguments().getString(ARG_LOCALE, "en")

    private val formMethod: String
        get() = requireArguments().getString(ARG_METHOD, "GET") ?: "GET"

    private val formParams: Map<String, String>
        get() = ThreeDSFormBody.fromBundle(requireArguments().getBundle(ARG_PARAMS))

    private val listener: ThreeDSListener?
        get() {
            hostListener?.let { return it }
            val act = activity ?: return null
            (act as? ThreeDSListener)?.let { return it }
            return act.supportFragmentManager.fragments
                .filterIsInstance<ThreeDSListener>()
                .firstOrNull()
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        if (savedInstanceState != null) {
            orphaned = true
            resolved = true
            return FrameLayout(context)
        }
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.WHITE)
            elevation = 64f
            isClickable = true
            isFocusable = true
        }

        val challengeWebView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).also { it.topMargin = dp(HEADER_HEIGHT_DP) }
            setBackgroundColor(Color.WHITE)
            applySecureWebSettings(settings)
            applyChallengeUserAgent(settings, context)
            acceptChallengeCookies(this)
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
            if (ThreeDSUrlAllowlist.isHttpsChallenge(challengeUrl)) {
                ThreeDSFormBody.load(this, challengeUrl, formMethod, formParams)
            } else {
                finish(ThreeDSChallengeEnd.RejectedRedirect)
            }
        }
        webView = challengeWebView
        rootView = root

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
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(top = systemBars.top, bottom = maxOf(systemBars.bottom, keyboard.bottom))
            insets
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (orphaned) {
            view.post { removeSelf() }
            return
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish(cancelEnd())
                }
            },
        )
        notifyShown()
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
        if (orphaned) return
        hideOtherDialogs()
        secureWindow()
    }

    /** Keeps one-time codes out of screenshots and the recent-apps preview. */
    private fun secureWindow() {
        if (securedWindow) return
        val window = activity?.window ?: return
        hadSecureFlag = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        securedWindow = true
    }

    private fun releaseWindow() {
        if (!securedWindow) return
        securedWindow = false
        if (!hadSecureFlag) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        if (!resolved && notifiedShown && activity?.isChangingConfigurations != true) {
            backgrounded = true
        }
    }

    override fun onDestroyView() {
        releaseWindow()
        dismissOpenDialogs()
        restoreHiddenDialogs()
        dotAnimators?.cancel()
        dotAnimators = null
        destroyWebView(webView)
        webView = null
        popupWebViews.toList().forEach { destroyWebView(it) }
        popupWebViews.clear()
        loadingOverlay = null
        handoffBanner = null
        rootView = null
        super.onDestroyView()
    }

    fun dismissProgrammatically() {
        finish(ThreeDSChallengeEnd.ClosedByPoll)
    }

    fun dismissAllowingStateLoss() {
        removeSelf()
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
            contentDescription = Strings.text("sheet.cancel", locale)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { finish(cancelEnd()) }
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
            flushChallengeCookies()
            hideLoadingOverlay()
            notifyShown()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val target = request?.url?.toString() ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                request.isForMainFrame &&
                request.hasGesture() &&
                !request.isRedirect &&
                request.method.equals("GET", ignoreCase = true) &&
                request.url.host != view?.url?.let { android.net.Uri.parse(it).host } &&
                openAppLink(target)
            ) {
                return true
            }
            return handleNavigation(target, request.isForMainFrame)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null) return false
            return handleNavigation(url, isForMainFrame = true)
        }

        // Load errors never end the challenge, as in Safari on iOS. The poll decides, or the shopper closes.

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            if (view == null) return true
            val wasMain = view == webView
            discardCrashedWebView(view)
            if (wasMain) finish(ThreeDSChallengeEnd.RendererGone)
            return true
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
            val root = rootView ?: return false
            val chromeClient = this
            val popup = WebView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).also { it.topMargin = dp(HEADER_HEIGHT_DP) }
                applySecureWebSettings(settings)
                applyChallengeUserAgent(settings, requireContext())
                acceptChallengeCookies(this)
                setBackgroundColor(Color.WHITE)
                webViewClient = createWebViewClient()
                webChromeClient = chromeClient
            }
            popupWebViews += popup
            root.addView(popup)
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            presentJsDialog(message, prompt = false, defaultValue = null, result = result, promptResult = null)
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            presentJsDialog(message, prompt = false, defaultValue = null, result = result, promptResult = null, cancellable = true)
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?,
        ): Boolean {
            presentJsDialog(message, prompt = true, defaultValue = defaultValue, result = null, promptResult = result, cancellable = true)
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            val popup = window ?: return
            popupWebViews.remove(popup)
            (popup.parent as? ViewGroup)?.removeView(popup)
            destroyWebView(popup)
        }
    }

    private fun handleNavigation(url: String, isForMainFrame: Boolean): Boolean =
        when (ThreeDSNavigation.hop(url, isForMainFrame)) {
            ThreeDSHop.Proceed -> false
            ThreeDSHop.OpenExternal -> {
                confirmOpenExternal(url)
                true
            }
            ThreeDSHop.Block -> true
        }

    private fun discardCrashedWebView(view: WebView) {
        if (view == webView) webView = null
        popupWebViews.remove(view)
        runCatching {
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }

    /** As on iOS, the shopper confirms before another app opens. Cancel keeps the ACS page, which is still usable here. */
    private fun confirmOpenExternal(url: String) {
        if (resolved) return
        val fallback = ThreeDSNavigation.browserFallbackUrl(url)
        val intent = ThreeDSNavigation.externalIntent(url)
        if (intent == null) {
            fallback?.let { webView?.loadUrl(it) }
            return
        }
        val act = activity ?: return
        if (act.isFinishing || openDialogs.any { it.isShowing }) return
        val dialog = AlertDialog.Builder(act)
            .setTitle(Strings.text("threeds.openBankApp.title", locale))
            .setMessage(Strings.text("threeds.openBankApp.message", locale))
            .setNegativeButton(Strings.text("sheet.cancel", locale), null)
            .setPositiveButton(Strings.text("threeds.openBankApp.open", locale)) { _, _ ->
                openExternal(intent, fallback)
            }
            .create()
        track(dialog)
        dialog.show()
    }

    private fun openExternal(intent: Intent, fallback: String?) {
        if (resolved) return
        if (launchExternal(intent)) {
            showBankHandoff()
        } else {
            fallback?.let { webView?.loadUrl(it) }
        }
    }

    /**
     * A shopper-tapped link to another host that a bank app claims opens that app, as in Chrome.
     * There is no confirm dialog, which would otherwise show on every ACS link.
     */
    private fun openAppLink(url: String): Boolean {
        if (resolved) return false
        val intent = ThreeDSNavigation.appLinkIntent(url) ?: return false
        val ctx = context ?: return false
        if (ThreeDSNavigation.claimedByOwnApp(ctx.packageManager, ctx.packageName, intent)) return false
        if (!launchExternal(intent)) return false
        showBankHandoff()
        return true
    }

    private fun launchExternal(intent: Intent): Boolean {
        val ctx = context ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val packageManager = ctx.packageManager
            val target = intent.resolveActivity(packageManager) ?: return false
            if (ThreeDSNavigation.isDefaultHttpsBrowser(packageManager, target.packageName)) return false
        }
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Some ACS pages need the shopper to come back and tap Continue, so the page stays visible under the banner. */
    private fun showBankHandoff() {
        handedOff = true
        if (handoffBanner != null || resolved) return
        val root = rootView ?: return
        hideLoadingOverlay()
        val banner = buildBankHandoffBanner(requireContext())
        root.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).also { it.topMargin = dp(HEADER_HEIGHT_DP) },
        )
        handoffBanner = banner
        banner.post {
            val main = webView ?: return@post
            val params = main.layoutParams as? FrameLayout.LayoutParams ?: return@post
            params.topMargin = dp(HEADER_HEIGHT_DP) + banner.height
            main.layoutParams = params
        }
    }

    private fun buildBankHandoffBanner(context: android.content.Context): View {
        val title = TextView(context).apply {
            text = Strings.text("threeds.bankHandoff.title", locale)
            textSize = 15f
            setTextColor(Color.parseColor("#16141A"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        val message = TextView(context).apply {
            text = Strings.text("threeds.bankHandoff.message", locale)
            textSize = 13f
            setTextColor(Color.parseColor("#6B6B6B"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = dp(4)
            layoutParams = params
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BANNER_COLOR)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            addView(title)
            addView(message)
        }
    }

    private fun cancelEnd(): ThreeDSChallengeEnd =
        if (handedOff || backgrounded) ThreeDSChallengeEnd.BankAppCanceled else ThreeDSChallengeEnd.UserCanceled

    private fun track(dialog: Dialog) {
        openDialogs += dialog
        dialog.setOnDismissListener { openDialogs.remove(dialog) }
    }

    private fun dismissOpenDialogs() {
        openDialogs.toList().forEach { runCatching { it.dismiss() } }
        openDialogs.clear()
    }

    private fun presentJsDialog(
        message: String?,
        prompt: Boolean,
        defaultValue: String?,
        result: JsResult?,
        promptResult: JsPromptResult?,
        cancellable: Boolean = false,
    ) {
        val act = activity
        if (act == null || act.isFinishing) {
            result?.cancel()
            promptResult?.cancel()
            return
        }
        var settled = false
        fun settle(block: () -> Unit) {
            if (settled) return
            settled = true
            block()
        }
        val input = if (prompt) EditText(act).apply { setText(defaultValue.orEmpty()) } else null
        val builder = AlertDialog.Builder(act)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                settle {
                    if (promptResult != null) promptResult.confirm(input?.text?.toString().orEmpty())
                    else result?.confirm()
                }
            }
        if (input != null) builder.setView(input)
        if (cancellable) {
            builder.setNegativeButton(android.R.string.cancel, null)
        }
        val dialog = builder.create()
        openDialogs += dialog
        dialog.setOnDismissListener {
            openDialogs.remove(dialog)
            settle {
                result?.cancel()
                promptResult?.cancel()
            }
        }
        dialog.show()
    }

    private fun notifyShown() {
        if (notifiedShown || resolved) return
        notifiedShown = true
        listener?.onThreeDSShown()
    }

    private fun hideOtherDialogs() {
        val manager = activity?.supportFragmentManager ?: return
        hiddenDialogs.clear()
        manager.fragments.filterIsInstance<DialogFragment>().forEach { other ->
            val otherDialog = other.dialog ?: return@forEach
            if (otherDialog.isShowing) {
                otherDialog.hide()
                hiddenDialogs += other
            }
        }
    }

    private fun restoreHiddenDialogs() {
        hiddenDialogs.forEach { other ->
            if (other.isAdded) {
                other.dialog?.show()
            }
        }
        hiddenDialogs.clear()
    }

    private fun finish(end: ThreeDSChallengeEnd) {
        if (resolved) return
        resolved = true
        dismissOpenDialogs()
        listener?.onThreeDSFinished(end)
        removeSelf()
    }

    private fun removeSelf() {
        if (!isAdded) return
        runCatching {
            parentFragmentManager.beginTransaction()
                .remove(this)
                .commitNowAllowingStateLoss()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "xmoney_threeds"
        private const val ARG_URL = "url"
        private const val ARG_LOCALE = "locale"
        private const val ARG_METHOD = "method"
        private const val ARG_PARAMS = "params"
        private const val HEADER_HEIGHT_DP = 60
        private val DOT_COLOR = Color.parseColor("#7C4DFF")
        private val BANNER_COLOR = Color.parseColor("#F4F1FF")

        fun newInstance(
            url: String,
            locale: String,
            formMethod: String = "GET",
            params: Map<String, String> = emptyMap(),
        ): ThreeDSDialog = ThreeDSDialog().apply {
            arguments = bundleOf(
                ARG_URL to url,
                ARG_LOCALE to locale,
                ARG_METHOD to formMethod,
                ARG_PARAMS to ThreeDSFormBody.toBundle(params),
            )
        }
    }
}

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
interface ThreeDSListener {
    fun onThreeDSFinished(end: ThreeDSChallengeEnd)
    fun onThreeDSShown() {}
}
