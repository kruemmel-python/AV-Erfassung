#include "reportdata.h"

#include <QFileInfo>
#include <QLocale>
#include <QtMath>
#include <algorithm>

bool ActivityRecord::isBox() const {
    return processType.compare(QStringLiteral("Kistenbearbeitung"), Qt::CaseInsensitive) == 0 && !boxId.isEmpty();
}

bool ActivityRecord::isDeleted() const {
    return changeLog.contains(QStringLiteral("gelöscht"), Qt::CaseInsensitive)
        || changeLog.contains(QStringLiteral("geloescht"), Qt::CaseInsensitive);
}

QString ActivityRecord::key() const {
    return QStringList{personnelNumber, shiftDate, shiftType, shiftId, processType,
                       boxId, processId, startDate, startTime}.join(QLatin1Char('|'));
}

void ReportData::clear() {
    records_.clear();
    sources_.clear();
}

void ReportData::merge(const QVector<ActivityRecord> &records, const QString &sourceFile) {
    QMap<QString, int> positions;
    for (int i = 0; i < records_.size(); ++i) positions.insert(records_.at(i).key(), i);
    for (const ActivityRecord &record : records) {
        const auto found = positions.constFind(record.key());
        if (found == positions.constEnd()) {
            positions.insert(record.key(), records_.size());
            records_ << record;
        } else {
            records_[found.value()] = record; // Der zuletzt importierte Export ist maßgeblich.
        }
    }
    const QString absolute = QFileInfo(sourceFile).absoluteFilePath();
    if (!sources_.contains(absolute, Qt::CaseInsensitive)) sources_ << absolute;
}

QStringList ReportData::employees() const {
    QSet<QString> values;
    for (const auto &record : records_) if (!record.personnelNumber.isEmpty()) values.insert(record.personnelNumber);
    QStringList result(values.begin(), values.end());
    std::sort(result.begin(), result.end(), [](const QString &a, const QString &b) {
        return a.toLongLong() < b.toLongLong();
    });
    return result;
}

QStringList ReportData::sources() const { return sources_; }

QVector<ActivityRecord> ReportData::recordsFor(const QString &personnelNumber) const {
    QVector<ActivityRecord> result;
    for (const auto &record : records_) {
        if (personnelNumber.isEmpty() || record.personnelNumber == personnelNumber) result << record;
    }
    std::sort(result.begin(), result.end(), [](const ActivityRecord &a, const ActivityRecord &b) {
        const QString left = a.startDate + a.startTime + a.personnelNumber;
        const QString right = b.startDate + b.startTime + b.personnelNumber;
        return left < right;
    });
    return result;
}

QVector<ActivityRecord> ReportData::changesFor(const QString &personnelNumber) const {
    QVector<ActivityRecord> result;
    for (const auto &record : recordsFor(personnelNumber)) {
        if (record.manuallyChanged || !record.changeLog.isEmpty()) result << record;
    }
    return result;
}

ReportStats ReportData::statsFor(const QString &personnelNumber) const {
    ReportStats stats;
    QSet<QString> employeesSeen;
    QSet<QString> shiftsSeen;
    QSet<QString> incompleteShifts;
    QVector<double> minutes;
    qint64 processPause = 0;
    qint64 embeddedPause = 0;

    for (const auto &record : recordsFor(personnelNumber)) {
        employeesSeen.insert(record.personnelNumber);
        const QString shiftKey = record.personnelNumber + QLatin1Char('|') + record.shiftDate + QLatin1Char('|') + record.shiftType + QLatin1Char('|') + record.shiftId;
        shiftsSeen.insert(shiftKey);
        if (record.shiftStatus.compare(QStringLiteral("COMPLETED"), Qt::CaseInsensitive) != 0
            && record.shiftStatus.compare(QStringLiteral("ABGESCHLOSSEN"), Qt::CaseInsensitive) != 0) {
            incompleteShifts.insert(shiftKey);
        }
        if (record.manuallyChanged || !record.changeLog.isEmpty()) ++stats.manualChangeCount;
        if (record.isDeleted()) ++stats.deletedCount;

        if (record.isBox()) {
            if (record.isDeleted()) continue;
            ++stats.boxCount;
            stats.boxGrossSeconds += record.grossSeconds;
            stats.boxNetSeconds += record.netSeconds;
            embeddedPause += record.pauseSeconds;
            stats.boxesByType[record.boxType.isEmpty() ? QStringLiteral("Ohne Zuordnung") : record.boxType]++;
            const double value = record.netSeconds / 60.0;
            minutes << value;
            if (record.netSeconds <= 20 * 60) ++stats.targetReachedCount;
        } else {
            if (record.processType.compare(QStringLiteral("Pause"), Qt::CaseInsensitive) == 0) {
                processPause += record.netSeconds;
            } else {
                stats.processSeconds += record.netSeconds;
            }
        }
    }
    stats.employeeCount = employeesSeen.size();
    stats.shiftCount = shiftsSeen.size();
    stats.incompleteShiftCount = incompleteShifts.size();
    stats.pauseSeconds = processPause > 0 ? processPause : embeddedPause;
    stats.productiveSeconds = stats.boxNetSeconds + stats.processSeconds;

    if (!minutes.isEmpty()) {
        std::sort(minutes.begin(), minutes.end());
        double sum = 0.0;
        for (double value : minutes) sum += value;
        stats.averageBoxMinutes = sum / minutes.size();
        stats.medianBoxMinutes = minutes.size() % 2
            ? minutes.at(minutes.size() / 2)
            : (minutes.at(minutes.size() / 2 - 1) + minutes.at(minutes.size() / 2)) / 2.0;
        stats.fastestBoxMinutes = minutes.first();
        stats.slowestBoxMinutes = minutes.last();
        double squared = 0.0;
        for (double value : minutes) squared += qPow(value - stats.averageBoxMinutes, 2.0);
        stats.standardDeviationMinutes = qSqrt(squared / minutes.size());
        stats.targetRate = 100.0 * stats.targetReachedCount / minutes.size();
    }
    if (stats.productiveSeconds > 0) stats.boxesPerProductiveHour = stats.boxCount * 3600.0 / stats.productiveSeconds;
    return stats;
}

QMap<QString, double> ReportData::averageMinutesByEmployee() const {
    QMap<QString, double> result;
    for (const QString &employee : employees()) result.insert(employee, statsFor(employee).averageBoxMinutes);
    return result;
}

QMap<QString, double> ReportData::averageMinutesByBoxType(const QString &personnelNumber) const {
    QMap<QString, qint64> sums;
    QMap<QString, int> counts;
    for (const auto &record : recordsFor(personnelNumber)) {
        if (!record.isBox() || record.isDeleted()) continue;
        const QString type = record.boxType.isEmpty() ? QStringLiteral("Ohne Zuordnung") : record.boxType;
        sums[type] += record.netSeconds;
        counts[type]++;
    }
    QMap<QString, double> result;
    for (auto it = sums.cbegin(); it != sums.cend(); ++it) result[it.key()] = it.value() / 60.0 / counts.value(it.key());
    return result;
}

QString ReportData::qualityReportHtml(const QString &personnelNumber) const {
    const ReportStats s = statsFor(personnelNumber);
    const QString scope = personnelNumber.isEmpty()
        ? QStringLiteral("Gesamtteam")
        : QStringLiteral("Mitarbeiter %1").arg(personnelNumber.toHtmlEscaped());
    QString assessment;
    if (s.boxCount == 0) {
        assessment = QStringLiteral("Für die Auswahl liegen keine auswertbaren Kisten vor.");
    } else if (s.averageBoxMinutes <= 20.0 && s.targetRate >= 75.0) {
        assessment = QStringLiteral("Die Bearbeitungsleistung liegt im betrachteten Zeitraum insgesamt im Zielbereich. Die Zielquote ist stabil.");
    } else if (s.averageBoxMinutes <= 22.0) {
        assessment = QStringLiteral("Die durchschnittliche Bearbeitungszeit liegt nahe am Zielwert. Einzelne längere Vorgänge sollten anhand der Chronologie geprüft werden.");
    } else {
        assessment = QStringLiteral("Die durchschnittliche Bearbeitungszeit liegt über dem Zielwert von 20 Minuten. Kistenart, Pausenanteile und Ausreißer sollten gemeinsam betrachtet werden.");
    }
    if (s.incompleteShiftCount > 0) {
        assessment.prepend(QStringLiteral("Datenhinweis: %1 Schicht(en) waren beim Export noch nicht abgeschlossen. ").arg(s.incompleteShiftCount));
    }

    QString changeAssessment;
    if (s.manualChangeCount == 0) {
        changeAssessment = QStringLiteral("Es wurden keine manuellen Änderungen erkannt.");
    } else {
        changeAssessment = QStringLiteral("%1 manuell geänderte Datensätze wurden erkannt, davon %2 als gelöscht gekennzeichnet. Die Begründungen sind im Reiter „Änderungen“ nachvollziehbar.")
            .arg(s.manualChangeCount).arg(s.deletedCount);
    }

    QStringList mix;
    for (auto it = s.boxesByType.cbegin(); it != s.boxesByType.cend(); ++it) {
        mix << QStringLiteral("%1: %2").arg(it.key().toHtmlEscaped()).arg(it.value());
    }

    return QStringLiteral(
        "<html><head><meta charset='utf-8'><style>"
        "body{font-family:'Segoe UI',Arial;color:#202020;margin:28px;}"
        "h1{color:#d40511;font-size:26px;margin-bottom:2px;} h2{color:#9b0008;margin-top:24px;}"
        ".meta{color:#666;margin-bottom:22px}.kpis{border-collapse:separate;border-spacing:6px;width:100%;}"
        ".kpis td{background:#fff3b0;border-left:5px solid #d40511;padding:10px 16px;text-align:left;font-weight:400;}"
        "table{border-collapse:collapse;width:100%}td{padding:8px;border-bottom:1px solid #ddd}td:last-child{text-align:right;font-weight:700}"
        ".notice{background:#fff3cd;padding:12px;border:1px solid #f2c200}.change{background:#fde1e3;padding:12px;border-left:5px solid #d40511}"
        "</style></head><body>"
        "<h1>QS-Bericht AV-Erfassung</h1><div class='meta'>Auswertung: %1 · %2 Schicht(en) · %3 Mitarbeiter</div>"
        "<table class='kpis'><tr><td><b>%4</b><br>Kisten</td><td><b>%5 min</b><br>Ø Nettozeit</td>"
        "<td><b>%6 %</b><br>Zielquote ≤ 20 min</td><td><b>%7</b><br>Kisten je produktiver Stunde</td></tr></table>"
        "<h2>Management Summary</h2><p>%8</p>"
        "<h2>Leistungskennzahlen</h2><table>"
        "<tr><td>Median je Kiste</td><td>%9 min</td></tr><tr><td>Schnellste Kiste</td><td>%10 min</td></tr>"
        "<tr><td>Langsamste Kiste</td><td>%11 min</td></tr><tr><td>Standardabweichung</td><td>%12 min</td></tr>"
        "<tr><td>Produktive Gesamtzeit</td><td>%13</td></tr><tr><td>Pausenzeit</td><td>%14</td></tr></table>"
        "<h2>Kistenmix</h2><p>%15</p>"
        "<h2>Datenqualität und Änderungen</h2><div class='change'>%16</div>"
        "<h2>QS-Hinweise</h2><div class='notice'>Die Kennzahlen dienen der sachlichen Prozessanalyse. Unterschiede können durch Kistenart, Volumen, Störungen, Einarbeitung und organisatorische Rahmenbedingungen entstehen. Für Personalentscheidungen ist stets eine fachliche Einzelfallprüfung erforderlich.</div>"
        "</body></html>")
        .arg(scope)
        .arg(s.shiftCount)
        .arg(s.employeeCount)
        .arg(s.boxCount)
        .arg(number(s.averageBoxMinutes))
        .arg(number(s.targetRate))
        .arg(number(s.boxesPerProductiveHour))
        .arg(assessment)
        .arg(number(s.medianBoxMinutes))
        .arg(number(s.fastestBoxMinutes))
        .arg(number(s.slowestBoxMinutes))
        .arg(number(s.standardDeviationMinutes))
        .arg(duration(s.productiveSeconds))
        .arg(duration(s.pauseSeconds))
        .arg(mix.isEmpty() ? QStringLiteral("Keine Daten") : mix.join(QStringLiteral(" · ")))
        .arg(changeAssessment);
}

QString ReportData::duration(qint64 seconds) {
    const qint64 hours = seconds / 3600;
    const qint64 minutes = (seconds % 3600) / 60;
    const qint64 rest = seconds % 60;
    return QStringLiteral("%1:%2:%3")
        .arg(hours, 2, 10, QLatin1Char('0'))
        .arg(minutes, 2, 10, QLatin1Char('0'))
        .arg(rest, 2, 10, QLatin1Char('0'));
}

QString ReportData::number(double value, int decimals) {
    return QLocale(QLocale::German).toString(value, 'f', decimals);
}
