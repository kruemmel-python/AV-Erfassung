# AVM Native Plugin ABI 1

Das C-ABI in `av_module_api_v1.h` ist normativ. Plugins MUST die exakt angegebene ABI-Version melden und dürfen ausschließlich POD-Typen, feste Integerbreiten und C-Aufrufkonventionen an der Grenze verwenden. Eigentum und Lebensdauer jedes Puffers MUST der Headerdefinition folgen. Host und Plugin MUST inkompatible ABI-Versionen vor einem Funktionsaufruf ablehnen.

Der Host MUST Ladefehler, fehlende Symbole, Nullzeiger, übergroße Längen und ungültiges UTF-8 kontrolliert behandeln. Plugins dürfen Ausnahmen niemals über die ABI-Grenze propagieren. Referenz: `enterprise/native-host`; unabhängige Prüfung: `conformance/avm-contracts-cpp`.
