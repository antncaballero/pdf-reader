# PDFBox uses reflective COS model construction in a few code paths.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class androidx.pdf.** { *; }
-dontwarn com.gemalto.jp2.**
