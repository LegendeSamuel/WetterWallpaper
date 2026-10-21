# PC-Vorschau der Bildverarbeitung ohne Handy. Aufruf: .\run.ps1 <ordner mit background.png, depth.png, sky.png> [...]
# Testsatz aus eigenem Foto: py -I make_set.py ..\ <foto.jpg> <zielordner>   (braucht numpy, onnxruntime, Pillow)
# Ergebnisse landen im jeweiligen Ordner: java_night.png (Himmel/Kanten), veg_sheet.png, glass_sheet.png (Frost wenig | viel | Regentropfen)
$J = "C:\Program Files\Android\Android Studio\jbr\bin"
$P = "$PSScriptRoot\..\..\app\src\main\java\de\seelig\wetterwallpaper"
$AJ = "$env:LOCALAPPDATA\Android\Sdk\platforms\android-34\android.jar"
$OUT = "$env:TEMP\wetter_pc_harness"
& "$J\javac.exe" -nowarn -cp $AJ -d $OUT "$P\EdgeRefine.java" "$P\Noise.java" "$P\Frost.java" "$P\RainOnGlass.java" "$P\Scene.java" "$P\Vegetation.java" "$PSScriptRoot\Harness.java" "$PSScriptRoot\VegHarness.java" "$PSScriptRoot\GlassHarness.java"
foreach ($c in "EdgeRefine", "Frost", "RainOnGlass", "Vegetation") { & "$J\java.exe" -ea -cp $OUT "de.seelig.wetterwallpaper.$c" }
foreach ($d in $args) {
    foreach ($h in "Harness", "VegHarness") { & "$J\java.exe" -cp "$OUT;$AJ" "de.seelig.wetterwallpaper.$h" $d }
    & "$J\java.exe" -cp "$OUT;$AJ" de.seelig.wetterwallpaper.GlassHarness "$d\background.png" "$d\glass_sheet.png"
}
