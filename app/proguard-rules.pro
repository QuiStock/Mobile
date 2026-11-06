# Retrofit reads the generic return type of each API method at runtime. R8 removes DTOs whose
# values are never read (e.g. the chatbot "end" response), which turns Response<Dto> into Response<Object>.
-keep class com.quistock.quistock.data.remote.**.dto.** { *; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
