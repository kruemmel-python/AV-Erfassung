#include "csvimporter.h"

#include <QFile>
#include <QFileInfo>
#include <QHash>
#include <QTextStream>

QStringList CsvImporter::parseLine(const QString &line) {
    QStringList fields;
    QString current;
    bool quoted = false;
    for (int i = 0; i < line.size(); ++i) {
        const QChar ch = line.at(i);
        if (ch == QLatin1Char('"')) {
            if (quoted && i + 1 < line.size() && line.at(i + 1) == QLatin1Char('"')) {
                current += QLatin1Char('"');
                ++i;
            } else {
                quoted = !quoted;
            }
        } else if (ch == QLatin1Char(';') && !quoted) {
            fields << current;
            current.clear();
        } else {
            current += ch;
        }
    }
    fields << current;
    return fields;
}

qint64 CsvImporter::parseDuration(const QString &value) {
    const QStringList parts = value.trimmed().split(QLatin1Char(':'));
    if (parts.size() != 3) return 0;
    bool okHours = false, okMinutes = false, okSeconds = false;
    const qint64 hours = parts.at(0).toLongLong(&okHours);
    const qint64 minutes = parts.at(1).toLongLong(&okMinutes);
    const qint64 seconds = parts.at(2).toLongLong(&okSeconds);
    if (!okHours || !okMinutes || !okSeconds) return 0;
    return hours * 3600 + minutes * 60 + seconds;
}

ImportResult CsvImporter::readFile(const QString &fileName) {
    ImportResult result;
    QFile file(fileName);
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text)) {
        result.error = QStringLiteral("Datei konnte nicht geöffnet werden: %1").arg(file.errorString());
        return result;
    }

    QTextStream stream(&file);
    stream.setCodec("UTF-8");
    QStringList header;
    QHash<QString, int> column;
    bool readingDetails = false;
    int lineNumber = 0;

    auto value = [](const QStringList &fields, const QHash<QString, int> &columns, const QString &name) {
        const int index = columns.value(name, -1);
        return index >= 0 && index < fields.size() ? fields.at(index).trimmed() : QString();
    };

    while (!stream.atEnd()) {
        QString line = stream.readLine();
        ++lineNumber;
        if (lineNumber == 1 && !line.isEmpty() && line.front() == QChar(0xFEFF)) line.remove(0, 1);
        if (line.startsWith(QStringLiteral("Schicht-ID;")) && !readingDetails) {
            header = parseLine(line);
            for (int i = 0; i < header.size(); ++i) column.insert(header.at(i).trimmed(), i);
            readingDetails = true;
            continue;
        }
        if (line.trimmed() == QStringLiteral("SCHICHTZUSAMMENFASSUNG")) break;
        if (!readingDetails || line.trimmed().isEmpty()) continue;

        const QStringList fields = parseLine(line);
        if (fields.size() < header.size() - 2) {
            ++result.ignoredLines;
            continue;
        }
        ActivityRecord record;
        record.sourceFile = QFileInfo(fileName).absoluteFilePath();
        record.shiftId = value(fields, column, QStringLiteral("Schicht-ID"));
        record.shiftDate = value(fields, column, QStringLiteral("Schichtdatum"));
        record.shiftType = value(fields, column, QStringLiteral("Schichtart"));
        record.scheduledStart = value(fields, column, QStringLiteral("Geplanter Schichtbeginn"));
        record.scheduledEnd = value(fields, column, QStringLiteral("Geplantes Schichtende"));
        record.shiftStatus = value(fields, column, QStringLiteral("Schichtstatus"));
        record.processId = value(fields, column, QStringLiteral("Prozess-ID"));
        record.processType = value(fields, column, QStringLiteral("Prozessart"));
        record.boxId = value(fields, column, QStringLiteral("Kisten-ID"));
        record.oldBoxId = value(fields, column, QStringLiteral("Alte Kisten-ID"));
        record.boxType = value(fields, column, QStringLiteral("Kistenart"));
        record.previousBoxId = value(fields, column, QStringLiteral("Vorherige Kisten-ID"));
        record.nextBoxId = value(fields, column, QStringLiteral("Nächste Kisten-ID"));
        record.personnelNumber = value(fields, column, QStringLiteral("Personalnummer"));
        record.startDate = value(fields, column, QStringLiteral("Startdatum"));
        record.startTime = value(fields, column, QStringLiteral("Startzeit"));
        record.endDate = value(fields, column, QStringLiteral("Enddatum"));
        record.endTime = value(fields, column, QStringLiteral("Endzeit"));
        record.grossSeconds = parseDuration(value(fields, column, QStringLiteral("Bruttozeit")));
        record.netSeconds = parseDuration(value(fields, column, QStringLiteral("Nettozeit")));
        record.pauseSeconds = parseDuration(value(fields, column, QStringLiteral("Pausenzeit")));
        record.registrationSeconds = parseDuration(value(fields, column, QStringLiteral("Registrierungszeit")));
        record.imageSeconds = parseDuration(value(fields, column, QStringLiteral("Image-Zeit")));
        record.miscSeconds = parseDuration(value(fields, column, QStringLiteral("Diverse-Zeit")));
        record.note = value(fields, column, QStringLiteral("Hinweis"));
        record.manuallyChanged = value(fields, column, QStringLiteral("Manuell geändert")).compare(QStringLiteral("true"), Qt::CaseInsensitive) == 0;
        record.changeLog = value(fields, column, QStringLiteral("Änderungsprotokoll"));
        if (record.personnelNumber.isEmpty() || record.processType.isEmpty()) {
            ++result.ignoredLines;
            continue;
        }
        result.records << record;
    }

    if (header.isEmpty()) {
        result.error = QStringLiteral("Keine AV-Detailtabelle erkannt. Erwartet wird eine Zeile beginnend mit „Schicht-ID“.");
    } else if (result.records.isEmpty()) {
        result.error = QStringLiteral("Die Datei enthält keine verwertbaren Detaildatensätze.");
    }
    return result;
}
