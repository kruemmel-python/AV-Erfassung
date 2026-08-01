#pragma once

#include "reportdata.h"

#include <QString>
#include <QVector>

struct ImportResult {
    QVector<ActivityRecord> records;
    QString error;
    int ignoredLines = 0;
};

class CsvImporter {
public:
    static ImportResult readFile(const QString &fileName);
    static QStringList parseLine(const QString &line);
    static qint64 parseDuration(const QString &value);
};
