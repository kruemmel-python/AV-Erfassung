#include "csvimporter.h"
#include "reportdata.h"

#include <QCoreApplication>
#include <QDebug>

int main(int argc, char *argv[]) {
    QCoreApplication app(argc, argv);
    if (argc != 2) {
        qCritical() << "CSV test file missing";
        return 2;
    }
    const QString file = QString::fromLocal8Bit(argv[1]);
    const ImportResult imported = CsvImporter::readFile(file);
    if (!imported.error.isEmpty()) {
        qCritical().noquote() << imported.error;
        return 3;
    }
    ReportData data;
    data.merge(imported.records, file);
    const ReportStats stats = data.statsFor(QStringLiteral("10001"));
    const QString report = data.qualityReportHtml(QStringLiteral("10001"));
    const bool valid = imported.records.size() == 8
        && data.employees() == QStringList{QStringLiteral("10001")}
        && stats.shiftCount == 1
        && stats.incompleteShiftCount == 0
        && stats.boxCount == 4
        && stats.deletedCount == 1
        && stats.manualChangeCount == 1
        && stats.boxesByType.value(QStringLiteral("Tagespost")) == 2
        && stats.boxesByType.value(QStringLiteral("Routing")) == 2
        && stats.pauseSeconds == 30 * 60
        && !report.contains(QStringLiteral("waren beim Export noch nicht abgeschlossen"))
        && !report.contains(QStringLiteral("%16"))
        && !report.contains(QStringLiteral("100%%"));
    if (!valid) {
        qCritical() << "Unexpected result"
                    << "rows" << imported.records.size()
                    << "employees" << data.employees()
                    << "shifts" << stats.shiftCount
                    << "boxes" << stats.boxCount
                    << "deleted" << stats.deletedCount
                    << "manual" << stats.manualChangeCount
                    << "types" << stats.boxesByType
                    << "pause" << stats.pauseSeconds;
        return 4;
    }
    qInfo() << "IMPORT_TEST_OK"
            << "rows" << imported.records.size()
            << "boxes" << stats.boxCount
            << "deleted" << stats.deletedCount
            << "averageMinutes" << stats.averageBoxMinutes;
    return 0;
}
