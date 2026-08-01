#pragma once

#include "chartwidget.h"
#include "reportdata.h"

#include <QLabel>
#include <QMainWindow>
#include <QTableWidget>
#include <QTextBrowser>

class QComboBox;
class QDragEnterEvent;
class QDropEvent;
class QTabWidget;

class MainWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit MainWindow(QWidget *parent = nullptr);
    void importFiles(const QStringList &files);

protected:
    void dragEnterEvent(QDragEnterEvent *event) override;
    void dropEvent(QDropEvent *event) override;

private slots:
    void chooseFiles();
    void clearData();
    void refresh();
    void exportPdf();
    void exportHtml();

private:
    QWidget *createOverviewPage();
    QWidget *createDataPage();
    QWidget *createChangesPage();
    QWidget *createQualityPage();
    QLabel *createKpi(const QString &caption);
    QString selectedEmployee() const;
    void refreshEmployeeFilter();
    void refreshKpis(const ReportStats &stats);
    void refreshDataTable(const QVector<ActivityRecord> &records);
    void refreshChangesTable(const QVector<ActivityRecord> &records);
    void styleTable(QTableWidget *table);

    ReportData data_;
    QComboBox *employeeFilter_ = nullptr;
    QLabel *sourceLabel_ = nullptr;
    QLabel *scopeLabel_ = nullptr;
    QLabel *kpiBoxes_ = nullptr;
    QLabel *kpiAverage_ = nullptr;
    QLabel *kpiTarget_ = nullptr;
    QLabel *kpiProductivity_ = nullptr;
    QLabel *kpiPause_ = nullptr;
    QLabel *kpiChanges_ = nullptr;
    ChartWidget *typeChart_ = nullptr;
    ChartWidget *performanceChart_ = nullptr;
    QTableWidget *dataTable_ = nullptr;
    QTableWidget *changesTable_ = nullptr;
    QTextBrowser *qualityReport_ = nullptr;
    QTabWidget *tabs_ = nullptr;
};
