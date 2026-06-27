package com.devicespooflab.hooks.hooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;
import android.view.KeyEvent;
import android.webkit.ClientCertRequest;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class WebViewHooks {

    private static final String TAG = "DeviceSpoofLab-WebView";

    public static void hook(LoadPackageParam lpparam) {
        try {
            hookWebSettings(lpparam);
            hookWebViewConstructor(lpparam);
            hookSetWebViewClient(lpparam);
            hookGetWebViewClient(lpparam);
            hookLoadUrl(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": init failed: " + t);
        }
    }

    private static void hookWebSettings(LoadPackageParam lpparam) {
        Class<?> webViewClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader);
        if (webViewClass == null) return;

        try {
            XposedHelpers.findAndHookMethod(webViewClass, "getSettings",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object settings = param.getResult();
                            if (settings == null) return;
                            String ua = ConfigManager.getWebViewUserAgent();
                            if (ua != null) {
                                try {
                                    XposedHelpers.callMethod(settings, "setUserAgentString", ua);
                                } catch (Throwable ignored) {}
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook WebView.getSettings: " + t);
        }
    }

    private static void hookWebViewConstructor(LoadPackageParam lpparam) {
        Class<?> webViewClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader);
        if (webViewClass == null) return;

        XC_MethodHook setUaHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object webView = param.thisObject;
                    Object settings = XposedHelpers.callMethod(webView, "getSettings");
                    String ua = ConfigManager.getWebViewUserAgent();
                    if (ua != null) {
                        XposedHelpers.callMethod(settings, "setUserAgentString", ua);
                    }
                    if (webView instanceof WebView) {
                        ((WebView) webView).setWebViewClient(new SpoofingWebViewClient(null));
                    }
                } catch (Throwable ignored) {}
            }
        };

        try {
            XposedHelpers.findAndHookConstructor(webViewClass,
                    Context.class, setUaHook);
        } catch (Throwable t) { logFail("WebView(Context)", t); }

        try {
            XposedHelpers.findAndHookConstructor(webViewClass,
                    Context.class, android.util.AttributeSet.class, setUaHook);
        } catch (Throwable t) { logFail("WebView(Context,AttributeSet)", t); }

        try {
            XposedHelpers.findAndHookConstructor(webViewClass,
                    Context.class, android.util.AttributeSet.class, int.class, setUaHook);
        } catch (Throwable t) { logFail("WebView(Context,AttributeSet,int)", t); }
    }

    private static void hookSetWebViewClient(LoadPackageParam lpparam) {
        Class<?> webViewClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader);
        if (webViewClass == null) return;

        try {
            XposedHelpers.findAndHookMethod(webViewClass, "setWebViewClient",
                    WebViewClient.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            WebViewClient orig = (WebViewClient) param.args[0];
                            if (orig instanceof SpoofingWebViewClient) return;
                            param.args[0] = new SpoofingWebViewClient(orig);
                        }
                    });
        } catch (Throwable t) { logFail("WebView.setWebViewClient", t); }
    }

    private static void hookGetWebViewClient(LoadPackageParam lpparam) {
        Class<?> webViewClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader);
        if (webViewClass == null) return;

        try {
            XposedHelpers.findAndHookMethod(webViewClass, "getWebViewClient",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object res = param.getResult();
                            if (res instanceof SpoofingWebViewClient) {
                                WebViewClient delegate = ((SpoofingWebViewClient) res).delegate;
                                param.setResult(delegate != null ? delegate : new WebViewClient());
                            }
                        }
                    });
        } catch (Throwable t) {
            // getWebViewClient() only exists on API 26+; ignore on older platforms.
        }
    }

    private static void hookLoadUrl(LoadPackageParam lpparam) {
        Class<?> webViewClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader);
        if (webViewClass == null) return;

        XC_MethodHook injectHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                injectAsync(param.thisObject);
            }
        };

        try {
            XposedHelpers.findAndHookMethod(webViewClass, "loadUrl",
                    String.class, injectHook);
        } catch (Throwable t) { logFail("WebView.loadUrl(String)", t); }

        try {
            XposedHelpers.findAndHookMethod(webViewClass, "loadUrl",
                    String.class, java.util.Map.class, injectHook);
        } catch (Throwable t) { /* overload */ }

        try {
            XposedHelpers.findAndHookMethod(webViewClass, "loadDataWithBaseURL",
                    String.class, String.class, String.class, String.class, String.class,
                    injectHook);
        } catch (Throwable t) { /* overload */ }
    }

    private static void injectAsync(Object webView) {
        if (!(webView instanceof WebView)) return;
        final WebView wv = (WebView) webView;
        try {
            wv.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        wv.evaluateJavascript(buildScript(), null);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void logFail(String what, Throwable t) {
        XposedBridge.log(TAG + ": failed to hook " + what + ": " + t);
    }

    // Forwards every WebViewClient callback to the original (or the platform
    // default) and injects the spoof script in onPageStarted/onPageFinished.
    static class SpoofingWebViewClient extends WebViewClient {
        private final WebViewClient delegate;

        SpoofingWebViewClient(WebViewClient delegate) {
            this.delegate = delegate;
        }

        // ---- Page lifecycle (also injects our spoof script) ----

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            inject(view);
            if (delegate != null) delegate.onPageStarted(view, url, favicon);
            else super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            inject(view);
            if (delegate != null) delegate.onPageFinished(view, url);
            else super.onPageFinished(view, url);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (delegate != null) delegate.onPageCommitVisible(view, url);
            else super.onPageCommitVisible(view, url);
        }

        // ---- Navigation ----

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return delegate != null
                    ? delegate.shouldOverrideUrlLoading(view, url)
                    : super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return delegate != null
                    ? delegate.shouldOverrideUrlLoading(view, request)
                    : super.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (delegate != null) delegate.doUpdateVisitedHistory(view, url, isReload);
            else super.doUpdateVisitedHistory(view, url, isReload);
        }

        @Override
        public void onFormResubmission(WebView view, Message dontResend, Message resend) {
            if (delegate != null) delegate.onFormResubmission(view, dontResend, resend);
            else super.onFormResubmission(view, dontResend, resend);
        }

        // ---- Resource loading / interception ----

        @Override
        public void onLoadResource(WebView view, String url) {
            if (delegate != null) delegate.onLoadResource(view, url);
            else super.onLoadResource(view, url);
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return delegate != null
                    ? delegate.shouldInterceptRequest(view, url)
                    : super.shouldInterceptRequest(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return delegate != null
                    ? delegate.shouldInterceptRequest(view, request)
                    : super.shouldInterceptRequest(view, request);
        }

        // ---- Error reporting ----

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (delegate != null) delegate.onReceivedError(view, errorCode, description, failingUrl);
            else super.onReceivedError(view, errorCode, description, failingUrl);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (delegate != null) delegate.onReceivedError(view, request, error);
            else super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (delegate != null) delegate.onReceivedHttpError(view, request, errorResponse);
            else super.onReceivedHttpError(view, request, errorResponse);
        }

        // ---- Security / authentication ----

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (delegate != null) delegate.onReceivedSslError(view, handler, error);
            else super.onReceivedSslError(view, handler, error);
        }

        @Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
            if (delegate != null) delegate.onReceivedClientCertRequest(view, request);
            else super.onReceivedClientCertRequest(view, request);
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            if (delegate != null) delegate.onReceivedHttpAuthRequest(view, handler, host, realm);
            else super.onReceivedHttpAuthRequest(view, handler, host, realm);
        }

        @Override
        public void onReceivedLoginRequest(WebView view, String realm, String account, String args) {
            if (delegate != null) delegate.onReceivedLoginRequest(view, realm, account, args);
            else super.onReceivedLoginRequest(view, realm, account, args);
        }

        @Override
        public void onSafeBrowsingHit(WebView view, WebResourceRequest request,
                                      int threatType, SafeBrowsingResponse callback) {
            if (delegate != null) delegate.onSafeBrowsingHit(view, request, threatType, callback);
            else super.onSafeBrowsingHit(view, request, threatType, callback);
        }

        // ---- Renderer / input / scale ----

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            return delegate != null
                    ? delegate.onRenderProcessGone(view, detail)
                    : super.onRenderProcessGone(view, detail);
        }

        @Override
        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            return delegate != null
                    ? delegate.shouldOverrideKeyEvent(view, event)
                    : super.shouldOverrideKeyEvent(view, event);
        }

        @Override
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            if (delegate != null) delegate.onUnhandledKeyEvent(view, event);
            else super.onUnhandledKeyEvent(view, event);
        }

        @Override
        public void onScaleChanged(WebView view, float oldScale, float newScale) {
            if (delegate != null) delegate.onScaleChanged(view, oldScale, newScale);
            else super.onScaleChanged(view, oldScale, newScale);
        }

        private void inject(WebView view) {
            try {
                view.evaluateJavascript(buildScript(), null);
            } catch (Throwable ignored) {}
        }
    }

    private static String buildScript() {
        long seed = ConfigManager.getFingerprintSeed();
        int seedLow = (int) (seed & 0xffffffffL);
        if (seedLow == 0) seedLow = 0x9e3779b9;
        String release = ConfigManager.getBuildVersionRelease();
        if (release == null || release.isEmpty()) release = "15";
        return SCRIPT_TEMPLATE
                .replace("__DS_SEED__", Integer.toString(seedLow))
                .replace("__DS_GPU_VENDOR__", esc(ConfigManager.getGpuVendor()))
                .replace("__DS_GPU_RENDERER__", esc(ConfigManager.getGpuRenderer()))
                .replace("__DS_UACH_MODEL__", esc(ConfigManager.getBuildModel()))
                .replace("__DS_UACH_PLATFORM_VERSION__", esc(release + ".0.0"))
                .replace("__DS_SCREEN_W__", Integer.toString(ConfigManager.getScreenWidth()))
                .replace("__DS_SCREEN_H__", Integer.toString(ConfigManager.getScreenHeight()))
                .replace("__DS_DPR__",
                        Float.toString(ConfigManager.getScreenDensity() / 160.0f));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Idempotent — gated by __ds_installed so re-injection is safe.
    private static final String SCRIPT_TEMPLATE =
            "(function(){\n" +
            "  if (window.__ds_installed) return;\n" +
            "  Object.defineProperty(window, '__ds_installed', {value:true, configurable:false, writable:false});\n" +
            "  var SEED = __DS_SEED__|0;\n" +
            "  var GPU_VENDOR = \"__DS_GPU_VENDOR__\";\n" +
            "  var GPU_RENDERER = \"__DS_GPU_RENDERER__\";\n" +
            "  var SCREEN_W = __DS_SCREEN_W__;\n" +
            "  var SCREEN_H = __DS_SCREEN_H__;\n" +
            "  var DPR = __DS_DPR__;\n" +
            "\n" +
            "  function makeRng(s){ s = s|0; if(s===0) s = 0x9e3779b9|0; return function(){ s ^= s<<13; s ^= s>>17; s ^= s<<5; return ((s>>>0)/4294967296); }; }\n" +
            "\n" +
            "  // ---- Canvas: deterministic per-pixel noise ----\n" +
            "  try {\n" +
            "    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;\n" +
            "    var origToBlob    = HTMLCanvasElement.prototype.toBlob;\n" +
            "    var origGetImage  = CanvasRenderingContext2D.prototype.getImageData;\n" +
            "    function noisyCopy(canvas){\n" +
            "      var w = canvas.width, h = canvas.height;\n" +
            "      if (!w || !h) return null;\n" +
            "      var ctx = canvas.getContext('2d');\n" +
            "      if (!ctx) return null;\n" +
            "      var img;\n" +
            "      try { img = origGetImage.call(ctx, 0, 0, w, h); } catch(e){ return null; }\n" +
            "      var d = img.data, rng = makeRng(SEED ^ (w*131) ^ (h*17));\n" +
            "      for (var i=0; i<d.length; i+=4){\n" +
            "        var n = ((rng()*3)|0) - 1;\n" +
            "        d[i]   = Math.max(0, Math.min(255, d[i]   + n));\n" +
            "        d[i+1] = Math.max(0, Math.min(255, d[i+1] + n));\n" +
            "        d[i+2] = Math.max(0, Math.min(255, d[i+2] + n));\n" +
            "      }\n" +
            "      try {\n" +
            "        var copy = document.createElement('canvas');\n" +
            "        copy.width = w; copy.height = h;\n" +
            "        copy.getContext('2d').putImageData(img, 0, 0);\n" +
            "        return copy;\n" +
            "      } catch(e){ return null; }\n" +
            "    }\n" +
            "    HTMLCanvasElement.prototype.toDataURL = function(){ var c=null; try { c = noisyCopy(this); } catch(e){} return origToDataURL.apply(c || this, arguments); };\n" +
            "    HTMLCanvasElement.prototype.toBlob    = function(){ var c=null; try { c = noisyCopy(this); } catch(e){} return origToBlob.apply(c || this, arguments); };\n" +
            "    CanvasRenderingContext2D.prototype.getImageData = function(x,y,w,h){\n" +
            "      var img = origGetImage.apply(this, arguments);\n" +
            "      try {\n" +
            "        var d = img.data, rng = makeRng(SEED ^ (w*131) ^ (h*17) ^ (x*7) ^ (y*23));\n" +
            "        for (var i=0; i<d.length; i+=4){\n" +
            "          var n = ((rng()*3)|0) - 1;\n" +
            "          d[i]   = Math.max(0, Math.min(255, d[i]   + n));\n" +
            "          d[i+1] = Math.max(0, Math.min(255, d[i+1] + n));\n" +
            "          d[i+2] = Math.max(0, Math.min(255, d[i+2] + n));\n" +
            "        }\n" +
            "      } catch(e){}\n" +
            "      return img;\n" +
            "    };\n" +
            "  } catch(e){}\n" +
            "\n" +
            "  // ---- WebGL renderer / vendor ----\n" +
            "  try {\n" +
            "    function patchGL(proto){\n" +
            "      var orig = proto.getParameter;\n" +
            "      proto.getParameter = function(p){\n" +
            "        if (p === 37445) return GPU_VENDOR;\n" +
            "        if (p === 37446) return GPU_RENDERER;\n" +
            "        if (p === 7936)  return GPU_VENDOR;\n" +
            "        if (p === 7937)  return GPU_RENDERER;\n" +
            "        return orig.apply(this, arguments);\n" +
            "      };\n" +
            "    }\n" +
            "    if (window.WebGLRenderingContext)  patchGL(WebGLRenderingContext.prototype);\n" +
            "    if (window.WebGL2RenderingContext) patchGL(WebGL2RenderingContext.prototype);\n" +
            "  } catch(e){}\n" +
            "\n" +
            "  // ---- AudioContext: tiny offsets in channel data ----\n" +
            "  try {\n" +
            "    if (window.AudioBuffer && AudioBuffer.prototype.getChannelData) {\n" +
            "      var origGCD = AudioBuffer.prototype.getChannelData;\n" +
            "      AudioBuffer.prototype.getChannelData = function(){\n" +
            "        var data = origGCD.apply(this, arguments);\n" +
            "        try {\n" +
            "          var rng = makeRng(SEED ^ (data.length|0));\n" +
            "          for (var i=0; i<data.length; i+=500) data[i] += (rng()-0.5) * 1e-7;\n" +
            "        } catch(e){}\n" +
            "        return data;\n" +
            "      };\n" +
            "    }\n" +
            "  } catch(e){}\n" +
            "\n" +
            "  // ---- navigator overrides ----\n" +
            "  try {\n" +
            "    function defineNav(name, value){\n" +
            "      try { Object.defineProperty(navigator, name, { get: function(){ return value; }, configurable: true }); } catch(e){}\n" +
            "    }\n" +
            "    defineNav('hardwareConcurrency', 8);\n" +
            "    defineNav('deviceMemory', 8);\n" +
            "    defineNav('maxTouchPoints', 5);\n" +
            "    defineNav('platform', 'Linux armv8l');\n" +
            "    defineNav('language', 'en-US');\n" +
            "    defineNav('languages', ['en-US','en']);\n" +
            "    defineNav('vendor', 'Google Inc.');\n" +
            "    if (navigator.userAgentData) {\n" +
            "      var brands = [\n" +
            "        { brand: 'Google Chrome', version: '131' },\n" +
            "        { brand: 'Chromium', version: '131' },\n" +
            "        { brand: 'Not_A Brand', version: '24' }\n" +
            "      ];\n" +
            "      var fakeUAD = {\n" +
            "        brands: brands,\n" +
            "        mobile: true,\n" +
            "        platform: 'Android',\n" +
            "        getHighEntropyValues: function(hints){ return Promise.resolve({\n" +
            "          architecture: 'arm', bitness: '64', brands: brands, mobile: true,\n" +
            "          model: \"__DS_UACH_MODEL__\", platform: 'Android', platformVersion: \"__DS_UACH_PLATFORM_VERSION__\",\n" +
            "          uaFullVersion: '131.0.6778.135',\n" +
            "          fullVersionList: [\n" +
            "            { brand: 'Google Chrome', version: '131.0.6778.135' },\n" +
            "            { brand: 'Chromium', version: '131.0.6778.135' },\n" +
            "            { brand: 'Not_A Brand', version: '24.0.0.0' }\n" +
            "          ]\n" +
            "        }); },\n" +
            "        toJSON: function(){ return { brands: brands, mobile: true, platform: 'Android' }; }\n" +
            "      };\n" +
            "      try { Object.defineProperty(navigator, 'userAgentData', { get: function(){ return fakeUAD; }, configurable: true }); } catch(e){}\n" +
            "    }\n" +
            "  } catch(e){}\n" +
            "\n" +
            "  // ---- screen + devicePixelRatio ----\n" +
            "  try {\n" +
            "    var statusH = 80;\n" +
            "    function defineScr(name, value){\n" +
            "      try { Object.defineProperty(screen, name, { get: function(){ return value; }, configurable: true }); } catch(e){}\n" +
            "    }\n" +
            "    defineScr('width',  Math.floor(SCREEN_W / DPR));\n" +
            "    defineScr('height', Math.floor(SCREEN_H / DPR));\n" +
            "    defineScr('availWidth',  Math.floor(SCREEN_W / DPR));\n" +
            "    defineScr('availHeight', Math.floor((SCREEN_H - statusH) / DPR));\n" +
            "    defineScr('colorDepth', 24);\n" +
            "    defineScr('pixelDepth', 24);\n" +
            "    try { Object.defineProperty(window, 'devicePixelRatio', { get: function(){ return DPR; }, configurable: true }); } catch(e){}\n" +
            "  } catch(e){}\n" +
            "})();";
}
