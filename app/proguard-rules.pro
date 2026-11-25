# Supprime tous les appels de log en release (aucune fuite d'information dans logcat).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}
# Retire les noms de fichiers source des traces de pile.
-renamesourcefileattribute SourceFile
