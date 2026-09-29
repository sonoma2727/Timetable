# keep kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.sonoma2727.timetable.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.sonoma2727.timetable.** {
    kotlinx.serialization.KSerializer serializer(...);
}
