#pragma once

#include <QDateTime>
#include <QMap>
#include <QSet>
#include <QString>
#include <QStringList>
#include <QVector>

struct ActivityRecord {
    QString sourceFile;
    QString shiftId;
    QString shiftDate;
    QString shiftType;
    QString scheduledStart;
    QString scheduledEnd;
    QString shiftStatus;
    QString processId;
    QString processType;
    QString boxId;
    QString oldBoxId;
    QString boxType;
    QString previousBoxId;
    QString nextBoxId;
    QString personnelNumber;
    QString startDate;
    QString startTime;
    QString endDate;
    QString endTime;
    qint64 grossSeconds = 0;
    qint64 netSeconds = 0;
    qint64 pauseSeconds = 0;
    qint64 registrationSeconds = 0;
    qint64 imageSeconds = 0;
    qint64 miscSeconds = 0;
    QString note;
    bool manuallyChanged = false;
    QString changeLog;

    bool isBox() const;
    bool isDeleted() const;
    QString key() const;
};

struct ReportStats {
    int employeeCount = 0;
    int shiftCount = 0;
    int incompleteShiftCount = 0;
    int boxCount = 0;
    int manualChangeCount = 0;
    int deletedCount = 0;
    int targetReachedCount = 0;
    qint64 boxGrossSeconds = 0;
    qint64 boxNetSeconds = 0;
    qint64 pauseSeconds = 0;
    qint64 productiveSeconds = 0;
    qint64 processSeconds = 0;
    double averageBoxMinutes = 0.0;
    double medianBoxMinutes = 0.0;
    double fastestBoxMinutes = 0.0;
    double slowestBoxMinutes = 0.0;
    double standardDeviationMinutes = 0.0;
    double targetRate = 0.0;
    double boxesPerProductiveHour = 0.0;
    QMap<QString, int> boxesByType;
};

class ReportData {
public:
    void clear();
    void merge(const QVector<ActivityRecord> &records, const QString &sourceFile);
    QStringList employees() const;
    QStringList sources() const;
    QVector<ActivityRecord> recordsFor(const QString &personnelNumber = QString()) const;
    QVector<ActivityRecord> changesFor(const QString &personnelNumber = QString()) const;
    ReportStats statsFor(const QString &personnelNumber = QString()) const;
    QMap<QString, double> averageMinutesByEmployee() const;
    QMap<QString, double> averageMinutesByBoxType(const QString &personnelNumber = QString()) const;
    QString qualityReportHtml(const QString &personnelNumber = QString()) const;
    int recordCount() const { return records_.size(); }

    static QString duration(qint64 seconds);
    static QString number(double value, int decimals = 1);

private:
    QVector<ActivityRecord> records_;
    QStringList sources_;
};
