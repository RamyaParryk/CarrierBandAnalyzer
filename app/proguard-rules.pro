# Androidの内部クラスやリフレクションで使うメソッドを勝手に消さないようにする
-keep class android.telephony.** { *; }
-keepclassmembers class android.telephony.** { *; }

# あなたのアプリのパッケージ内のクラスも、念のためリフレクション対象は保護
-keep class com.ratolab.carrierbandanalyzer.** { *; }