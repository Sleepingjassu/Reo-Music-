# REO Music release rules
# NewPipe/Media3 use reflection in parts of their extraction and media stacks.
-keep class org.schabi.newpipe.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn org.schabi.newpipe.**
