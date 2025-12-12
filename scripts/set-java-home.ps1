# Sets JAVA_HOME to Android Studio's bundled JBR for the current PowerShell session
$androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"

if (Test-Path $androidStudioJbr) {
    $env:JAVA_HOME = $androidStudioJbr
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    Write-Host "JAVA_HOME set to $env:JAVA_HOME"
} else {
    Write-Warning "Android Studio JBR not found at $androidStudioJbr. Update the path in scripts/set-java-home.ps1 if installed elsewhere."
}
