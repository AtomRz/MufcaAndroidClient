# minifyEnabled true для release. Явных -keep правил не требуется:
# - OkHttp и Firebase подтягивают свои consumer-proguard-rules автоматически из AAR;
# - в приложении нет @JavascriptInterface (WebView вызывается только в одну сторону,
#   через evaluateJavascript), поэтому нет риска, что R8 переименует метод-мост
#   и JS перестанет его находить;
# - нет Gson/Moshi/Kotlin-сериализации моделей по рефлексии — только org.json.JSONObject,
#   которому обфускация имён полей не мешает.
#
# Если в будущем появится @JavascriptInterface-класс, добавь сюда:
#   -keepclassmembers class com.mufcabot.app.YourBridge { public *; }
