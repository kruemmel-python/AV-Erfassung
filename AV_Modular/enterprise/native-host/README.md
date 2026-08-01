# AV Native Module SDK

Die C-ABI ist für seltene Erweiterungen vorgesehen, die sich nicht durch Konfiguration oder Regeln abbilden lassen. Der Plattformkern prüft vor dem Laden die ABI-Version, Modul-ID und Modulversion.

## Speichergrenze

Speicher, den ein Modul über `process_event` bereitstellt, muss durch dessen eigene Funktion `free_result` wieder freigegeben werden. Dadurch wird kein Speicher zwischen unterschiedlichen C/C++-Laufzeitbibliotheken freigegeben.

## Beispiel bauen

```powershell
cmake -S . -B build
cmake --build build --config Release
ctest --test-dir build -C Release --output-on-failure
```

Auf Android werden keine beliebigen DLL- oder SO-Plugins nachgeladen. Dort dienen die JSON-Konfigurationen als dynamische Erweiterung; komplexe Komponenten werden kontrolliert beim App-Build eingebunden.

`AvModuleHost` lädt die Bibliothek mit lokaler Symbolsichtbarkeit, prüft ABI-Version, Modul-ID und alle Pflichtfunktionen und kapselt Initialisierung, Datensatzprüfung, Ereignisverarbeitung und Speicherfreigabe. Die kryptografische Paketprüfung erfolgt vor dem nativen Laden durch den Paketdienst des Plattformkerns.
