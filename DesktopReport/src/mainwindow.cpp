#include "mainwindow.h"

#include "csvimporter.h"

#include <QAbstractItemView>
#include <QApplication>
#include <QComboBox>
#include <QDateTime>
#include <QDesktopServices>
#include <QDragEnterEvent>
#include <QDropEvent>
#include <QFile>
#include <QFileDialog>
#include <QFileInfo>
#include <QGridLayout>
#include <QHeaderView>
#include <QHBoxLayout>
#include <QMessageBox>
#include <QMimeData>
#include <QPrinter>
#include <QPushButton>
#include <QStatusBar>
#include <QTabWidget>
#include <QTextDocument>
#include <QUrl>
#include <QVBoxLayout>

namespace {
constexpr auto Red = "#d40511";
constexpr auto DarkRed = "#9b0008";
constexpr auto Yellow = "#ffcc00";

QTableWidgetItem *item(const QString &text, Qt::Alignment alignment = Qt::AlignLeft | Qt::AlignVCenter) {
    auto *value = new QTableWidgetItem(text);
    value->setTextAlignment(alignment);
    return value;
}

QString activityLabel(const ActivityRecord &record) {
    if (record.isBox()) return record.boxType.isEmpty() ? QStringLiteral("Kistenbearbeitung") : record.boxType;
    return record.processType;
}
}

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent) {
    setWindowTitle(QStringLiteral("AV-Schichtreport – QS-Auswertung"));
    resize(1380, 860);
    setMinimumSize(1050, 680);
    setAcceptDrops(true);

    auto *central = new QWidget(this);
    auto *root = new QVBoxLayout(central);
    root->setContentsMargins(0, 0, 0, 0);
    root->setSpacing(0);

    auto *header = new QWidget;
    header->setObjectName(QStringLiteral("header"));
    auto *headerLayout = new QHBoxLayout(header);
    headerLayout->setContentsMargins(22, 13, 18, 13);
    auto *title = new QLabel(QStringLiteral("AV-SCHICHTREPORT"));
    title->setObjectName(QStringLiteral("title"));
    auto *subtitle = new QLabel(QStringLiteral("CSV-Auswertung · Mitarbeiter · Team · QS"));
    subtitle->setObjectName(QStringLiteral("subtitle"));
    auto *titles = new QVBoxLayout;
    titles->setSpacing(0);
    titles->addWidget(title);
    titles->addWidget(subtitle);
    headerLayout->addLayout(titles);
    headerLayout->addStretch();
    auto *importButton = new QPushButton(QStringLiteral("CSV-DATEIEN IMPORTIEREN"));
    importButton->setObjectName(QStringLiteral("primaryButton"));
    auto *clearButton = new QPushButton(QStringLiteral("DATEN ZURÜCKSETZEN"));
    clearButton->setObjectName(QStringLiteral("headerButton"));
    headerLayout->addWidget(importButton);
    headerLayout->addWidget(clearButton);
    root->addWidget(header);

    auto *filterBar = new QWidget;
    filterBar->setObjectName(QStringLiteral("filterBar"));
    auto *filterLayout = new QHBoxLayout(filterBar);
    filterLayout->setContentsMargins(18, 10, 18, 10);
    filterLayout->addWidget(new QLabel(QStringLiteral("AUSWERTUNG:")));
    employeeFilter_ = new QComboBox;
    employeeFilter_->setMinimumWidth(250);
    employeeFilter_->addItem(QStringLiteral("Gesamtteam"), QString());
    filterLayout->addWidget(employeeFilter_);
    scopeLabel_ = new QLabel(QStringLiteral("Noch keine Daten importiert"));
    scopeLabel_->setObjectName(QStringLiteral("scope"));
    filterLayout->addWidget(scopeLabel_);
    filterLayout->addStretch();
    sourceLabel_ = new QLabel(QStringLiteral("CSV-Dateien hier hineinziehen oder Import wählen"));
    sourceLabel_->setObjectName(QStringLiteral("sources"));
    filterLayout->addWidget(sourceLabel_);
    root->addWidget(filterBar);

    tabs_ = new QTabWidget;
    tabs_->addTab(createOverviewPage(), QStringLiteral("ÜBERSICHT"));
    tabs_->addTab(createDataPage(), QStringLiteral("SCHICHTDATEN"));
    tabs_->addTab(createChangesPage(), QStringLiteral("ÄNDERUNGEN"));
    tabs_->addTab(createQualityPage(), QStringLiteral("QS-BERICHT"));
    root->addWidget(tabs_, 1);
    setCentralWidget(central);

    setStyleSheet(QStringLiteral(R"(
        QMainWindow, QWidget { font-family: "Segoe UI"; font-size: 10pt; color: #202020; }
        QMainWindow { background: #f4f4f4; }
        QWidget#header { background: #d40511; }
        QLabel#title { color: white; font-size: 21pt; font-weight: 900; }
        QLabel#subtitle { color: #ffe5e7; font-size: 9pt; }
        QPushButton { min-height: 34px; border-radius: 5px; padding: 3px 14px; font-weight: 700; }
        QPushButton#primaryButton { background: #ffcc00; color: #202020; border: 1px solid #e0ad00; }
        QPushButton#primaryButton:hover { background: #ffd633; }
        QPushButton#headerButton { background: transparent; color: white; border: 1px solid white; }
        QPushButton#actionButton { background: #d40511; color: white; border: 1px solid #9b0008; }
        QWidget#filterBar { background: #ffcc00; border-bottom: 1px solid #d8aa00; }
        QLabel#scope { font-weight: 700; color: #9b0008; margin-left: 8px; }
        QLabel#sources { color: #594600; }
        QComboBox { min-height: 30px; background: white; border: 1px solid #b68e00; padding: 2px 8px; }
        QTabWidget::pane { border: 0; background: #f4f4f4; }
        QTabBar::tab { background: #e2e2e2; min-width: 125px; padding: 11px 18px; margin-right: 2px; font-weight: 700; }
        QTabBar::tab:selected { background: white; color: #d40511; border-top: 3px solid #d40511; }
        QLabel[kpi="true"] { background: white; border-left: 5px solid #d40511; border-radius: 4px; padding: 13px; }
        QTableWidget { background: white; alternate-background-color: #f7f7f7; gridline-color: #dddddd; }
        QHeaderView::section { background: #9b0008; color: white; font-weight: 700; padding: 7px; border: 0; }
        QTextBrowser { background: white; border: 1px solid #dddddd; }
    )"));

    connect(importButton, &QPushButton::clicked, this, &MainWindow::chooseFiles);
    connect(clearButton, &QPushButton::clicked, this, &MainWindow::clearData);
    connect(employeeFilter_, QOverload<int>::of(&QComboBox::currentIndexChanged), this, &MainWindow::refresh);
    statusBar()->showMessage(QStringLiteral("Bereit – mehrere AV-CSV-Dateien können gemeinsam importiert werden."));
}

QWidget *MainWindow::createOverviewPage() {
    auto *page = new QWidget;
    auto *layout = new QVBoxLayout(page);
    layout->setContentsMargins(18, 18, 18, 18);
    layout->setSpacing(14);
    auto *kpis = new QGridLayout;
    kpis->setSpacing(10);
    kpiBoxes_ = createKpi(QStringLiteral("KISTEN"));
    kpiAverage_ = createKpi(QStringLiteral("Ø NETTOZEIT JE KISTE"));
    kpiTarget_ = createKpi(QStringLiteral("ZIELQUOTE ≤ 20 MIN"));
    kpiProductivity_ = createKpi(QStringLiteral("KISTEN / PRODUKTIVE STUNDE"));
    kpiPause_ = createKpi(QStringLiteral("PAUSENZEIT"));
    kpiChanges_ = createKpi(QStringLiteral("MANUELLE ÄNDERUNGEN"));
    kpis->addWidget(kpiBoxes_, 0, 0);
    kpis->addWidget(kpiAverage_, 0, 1);
    kpis->addWidget(kpiTarget_, 0, 2);
    kpis->addWidget(kpiProductivity_, 1, 0);
    kpis->addWidget(kpiPause_, 1, 1);
    kpis->addWidget(kpiChanges_, 1, 2);
    layout->addLayout(kpis);

    auto *charts = new QHBoxLayout;
    charts->setSpacing(12);
    typeChart_ = new ChartWidget;
    performanceChart_ = new ChartWidget;
    charts->addWidget(typeChart_, 1);
    charts->addWidget(performanceChart_, 1);
    layout->addLayout(charts, 1);
    return page;
}

QWidget *MainWindow::createDataPage() {
    auto *page = new QWidget;
    auto *layout = new QVBoxLayout(page);
    layout->setContentsMargins(18, 18, 18, 18);
    auto *hint = new QLabel(QStringLiteral("Chronologische Detailansicht. Gelöschte Datensätze sind rot, sonstige manuelle Änderungen gelb markiert."));
    hint->setWordWrap(true);
    layout->addWidget(hint);
    dataTable_ = new QTableWidget;
    dataTable_->setColumnCount(12);
    dataTable_->setHorizontalHeaderLabels({QStringLiteral("Datum"), QStringLiteral("Schicht"), QStringLiteral("Personalnr."),
        QStringLiteral("Vorgang"), QStringLiteral("Kistenart"), QStringLiteral("Kisten-/Prozess-ID"),
        QStringLiteral("Start"), QStringLiteral("Ende"), QStringLiteral("Brutto"), QStringLiteral("Netto"),
        QStringLiteral("Pause"), QStringLiteral("Kennzeichnung")});
    styleTable(dataTable_);
    layout->addWidget(dataTable_, 1);
    return page;
}

QWidget *MainWindow::createChangesPage() {
    auto *page = new QWidget;
    auto *layout = new QVBoxLayout(page);
    layout->setContentsMargins(18, 18, 18, 18);
    auto *notice = new QLabel(QStringLiteral("PRÜFSPUR: Alle manuell veränderten oder gelöschten Erfassungen mit Änderungsgrund."));
    notice->setStyleSheet(QStringLiteral("background:#fde1e3;border-left:5px solid %1;padding:10px;font-weight:700;").arg(Red));
    layout->addWidget(notice);
    changesTable_ = new QTableWidget;
    changesTable_->setColumnCount(8);
    changesTable_->setHorizontalHeaderLabels({QStringLiteral("Datum"), QStringLiteral("Personalnr."), QStringLiteral("Schicht"),
        QStringLiteral("Datensatz"), QStringLiteral("Art"), QStringLiteral("Netto"), QStringLiteral("Status"), QStringLiteral("Änderungsprotokoll")});
    styleTable(changesTable_);
    changesTable_->horizontalHeader()->setSectionResizeMode(7, QHeaderView::Stretch);
    layout->addWidget(changesTable_, 1);
    return page;
}

QWidget *MainWindow::createQualityPage() {
    auto *page = new QWidget;
    auto *layout = new QVBoxLayout(page);
    layout->setContentsMargins(18, 18, 18, 18);
    auto *actions = new QHBoxLayout;
    auto *pdf = new QPushButton(QStringLiteral("QS-BERICHT ALS PDF"));
    auto *html = new QPushButton(QStringLiteral("QS-BERICHT ALS HTML"));
    pdf->setObjectName(QStringLiteral("actionButton"));
    html->setObjectName(QStringLiteral("actionButton"));
    actions->addWidget(pdf);
    actions->addWidget(html);
    actions->addStretch();
    layout->addLayout(actions);
    qualityReport_ = new QTextBrowser;
    qualityReport_->setOpenExternalLinks(true);
    layout->addWidget(qualityReport_, 1);
    connect(pdf, &QPushButton::clicked, this, &MainWindow::exportPdf);
    connect(html, &QPushButton::clicked, this, &MainWindow::exportHtml);
    return page;
}

QLabel *MainWindow::createKpi(const QString &caption) {
    auto *label = new QLabel(QStringLiteral("<span style='font-size:9pt;color:#666'>%1</span><br><b style='font-size:20pt'>–</b>").arg(caption));
    label->setProperty("kpi", true);
    label->setMinimumHeight(76);
    return label;
}

void MainWindow::chooseFiles() {
    const QStringList files = QFileDialog::getOpenFileNames(this, QStringLiteral("AV-Schichtexporte auswählen"), QString(),
                                                            QStringLiteral("AV CSV-Exporte (*.csv);;Alle Dateien (*.*)"));
    if (!files.isEmpty()) importFiles(files);
}

void MainWindow::importFiles(const QStringList &files) {
    int importedFiles = 0;
    int importedRows = 0;
    QStringList errors;
    for (const QString &file : files) {
        const ImportResult result = CsvImporter::readFile(file);
        if (!result.error.isEmpty()) {
            errors << QStringLiteral("%1: %2").arg(QFileInfo(file).fileName(), result.error);
            continue;
        }
        data_.merge(result.records, file);
        ++importedFiles;
        importedRows += result.records.size();
    }
    refreshEmployeeFilter();
    refresh();
    if (!errors.isEmpty()) QMessageBox::warning(this, QStringLiteral("Einige Dateien konnten nicht importiert werden"), errors.join(QStringLiteral("\n\n")));
    if (importedFiles > 0) {
        statusBar()->showMessage(QStringLiteral("%1 Datei(en), %2 Zeilen gelesen; %3 eindeutige Datensätze im Bericht.")
                                 .arg(importedFiles).arg(importedRows).arg(data_.recordCount()), 10000);
    }
}

void MainWindow::clearData() {
    if (data_.recordCount() > 0 && QMessageBox::question(this, QStringLiteral("Daten zurücksetzen"),
        QStringLiteral("Alle aktuell importierten Daten aus der Desktop-Ansicht entfernen? Die CSV-Dateien bleiben unverändert.")) != QMessageBox::Yes) return;
    data_.clear();
    refreshEmployeeFilter();
    refresh();
    statusBar()->showMessage(QStringLiteral("Importierte Daten wurden aus der Ansicht entfernt."), 5000);
}

QString MainWindow::selectedEmployee() const { return employeeFilter_->currentData().toString(); }

void MainWindow::refreshEmployeeFilter() {
    const QString previous = selectedEmployee();
    employeeFilter_->blockSignals(true);
    employeeFilter_->clear();
    employeeFilter_->addItem(QStringLiteral("Gesamtteam"), QString());
    for (const QString &employee : data_.employees()) employeeFilter_->addItem(QStringLiteral("Mitarbeiter %1").arg(employee), employee);
    const int index = employeeFilter_->findData(previous);
    employeeFilter_->setCurrentIndex(index >= 0 ? index : 0);
    employeeFilter_->blockSignals(false);
}

void MainWindow::refresh() {
    const QString employee = selectedEmployee();
    const ReportStats stats = data_.statsFor(employee);
    refreshKpis(stats);
    refreshDataTable(data_.recordsFor(employee));
    refreshChangesTable(data_.changesFor(employee));
    qualityReport_->setHtml(data_.qualityReportHtml(employee));
    scopeLabel_->setText(employee.isEmpty()
        ? QStringLiteral("%1 Mitarbeiter · %2 Schichten").arg(stats.employeeCount).arg(stats.shiftCount)
        : QStringLiteral("Personalnummer %1 · %2 Schichten").arg(employee).arg(stats.shiftCount));
    sourceLabel_->setText(data_.sources().isEmpty()
        ? QStringLiteral("CSV-Dateien hier hineinziehen oder Import wählen")
        : QStringLiteral("%1 Quelldatei(en) · %2 Datensätze").arg(data_.sources().size()).arg(data_.recordCount()));

    QMap<QString, double> boxes;
    for (auto it = stats.boxesByType.cbegin(); it != stats.boxesByType.cend(); ++it) boxes.insert(it.key(), it.value());
    typeChart_->setData(QStringLiteral("Kisten nach Kistenart"), boxes, QStringLiteral("Kisten"));
    if (employee.isEmpty()) {
        performanceChart_->setData(QStringLiteral("Ø Nettozeit je Mitarbeiter"), data_.averageMinutesByEmployee(), QStringLiteral("min"), 20.0);
    } else {
        performanceChart_->setData(QStringLiteral("Ø Nettozeit je Kistenart"), data_.averageMinutesByBoxType(employee), QStringLiteral("min"), 20.0);
    }
}

void MainWindow::refreshKpis(const ReportStats &s) {
    auto set = [](QLabel *label, const QString &caption, const QString &value, const QString &sub = QString()) {
        label->setText(QStringLiteral("<span style='font-size:9pt;color:#666'>%1</span><br><b style='font-size:20pt;color:#202020'>%2</b>%3")
                       .arg(caption, value, sub.isEmpty() ? QString() : QStringLiteral("<span style='color:#666'> %1</span>").arg(sub)));
    };
    set(kpiBoxes_, QStringLiteral("KISTEN"), QString::number(s.boxCount), QStringLiteral("aus %1 Schicht(en)").arg(s.shiftCount));
    set(kpiAverage_, QStringLiteral("Ø NETTOZEIT JE KISTE"), ReportData::number(s.averageBoxMinutes), QStringLiteral("Minuten"));
    set(kpiTarget_, QStringLiteral("ZIELQUOTE ≤ 20 MIN"), ReportData::number(s.targetRate), QStringLiteral("%"));
    set(kpiProductivity_, QStringLiteral("KISTEN / PRODUKTIVE STUNDE"), ReportData::number(s.boxesPerProductiveHour));
    set(kpiPause_, QStringLiteral("PAUSENZEIT"), ReportData::duration(s.pauseSeconds));
    set(kpiChanges_, QStringLiteral("MANUELLE ÄNDERUNGEN"), QString::number(s.manualChangeCount),
        s.deletedCount > 0 ? QStringLiteral("davon %1 gelöscht").arg(s.deletedCount) : QString());
}

void MainWindow::refreshDataTable(const QVector<ActivityRecord> &records) {
    dataTable_->setSortingEnabled(false);
    dataTable_->setRowCount(records.size());
    int row = 0;
    for (const auto &record : records) {
        const QString id = record.isBox() ? record.boxId : record.processId;
        const QString marker = record.isDeleted() ? QStringLiteral("GELÖSCHT")
            : record.manuallyChanged ? QStringLiteral("MANUELL GEÄNDERT") : QString();
        const QStringList values{record.startDate, record.shiftType, record.personnelNumber, record.processType,
            record.boxType, id, record.startTime, record.endTime, ReportData::duration(record.grossSeconds),
            ReportData::duration(record.netSeconds), ReportData::duration(record.pauseSeconds), marker};
        for (int column = 0; column < values.size(); ++column) dataTable_->setItem(row, column, item(values.at(column)));
        if (!record.note.isEmpty()) dataTable_->item(row, 3)->setToolTip(record.note);
        if (!record.changeLog.isEmpty()) dataTable_->item(row, 11)->setToolTip(record.changeLog);
        if (record.isDeleted()) {
            for (int column = 0; column < dataTable_->columnCount(); ++column) {
                dataTable_->item(row, column)->setBackground(QColor("#f8c7ca"));
                dataTable_->item(row, column)->setForeground(QColor(DarkRed));
            }
        } else if (record.manuallyChanged) {
            for (int column = 0; column < dataTable_->columnCount(); ++column) dataTable_->item(row, column)->setBackground(QColor("#fff0b3"));
        }
        ++row;
    }
    dataTable_->setSortingEnabled(true);
    dataTable_->resizeColumnsToContents();
    dataTable_->horizontalHeader()->setSectionResizeMode(3, QHeaderView::Stretch);
}

void MainWindow::refreshChangesTable(const QVector<ActivityRecord> &records) {
    changesTable_->setSortingEnabled(false);
    changesTable_->setRowCount(records.size());
    int row = 0;
    for (const auto &record : records) {
        const QString id = record.isBox() ? record.boxId : record.processId;
        const QString status = record.isDeleted() ? QStringLiteral("GELÖSCHT") : QStringLiteral("GEÄNDERT");
        const QStringList values{record.startDate, record.personnelNumber, record.shiftType, id,
            activityLabel(record), ReportData::duration(record.netSeconds), status, record.changeLog};
        for (int column = 0; column < values.size(); ++column) changesTable_->setItem(row, column, item(values.at(column)));
        const QColor background = record.isDeleted() ? QColor("#f8c7ca") : QColor("#fff0b3");
        for (int column = 0; column < changesTable_->columnCount(); ++column) changesTable_->item(row, column)->setBackground(background);
        ++row;
    }
    changesTable_->setSortingEnabled(true);
    changesTable_->resizeColumnsToContents();
    changesTable_->horizontalHeader()->setSectionResizeMode(7, QHeaderView::Stretch);
}

void MainWindow::styleTable(QTableWidget *table) {
    table->setAlternatingRowColors(true);
    table->setSelectionBehavior(QAbstractItemView::SelectRows);
    table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    table->setSortingEnabled(true);
    table->verticalHeader()->setVisible(false);
    table->horizontalHeader()->setStretchLastSection(true);
}

void MainWindow::exportPdf() {
    if (data_.recordCount() == 0) {
        QMessageBox::information(this, QStringLiteral("Keine Daten"), QStringLiteral("Bitte zuerst mindestens eine CSV-Datei importieren."));
        return;
    }
    const QString employee = selectedEmployee();
    const QString suggested = employee.isEmpty() ? QStringLiteral("AV-QS-Teambericht.pdf") : QStringLiteral("AV-QS-Mitarbeiter-%1.pdf").arg(employee);
    const QString path = QFileDialog::getSaveFileName(this, QStringLiteral("QS-Bericht als PDF speichern"), suggested, QStringLiteral("PDF-Datei (*.pdf)"));
    if (path.isEmpty()) return;
    QPrinter printer(QPrinter::HighResolution);
    printer.setOutputFormat(QPrinter::PdfFormat);
    printer.setOutputFileName(path);
    printer.setPageSize(QPageSize(QPageSize::A4));
    QTextDocument document;
    document.setHtml(data_.qualityReportHtml(employee));
    document.print(&printer);
    statusBar()->showMessage(QStringLiteral("QS-Bericht gespeichert: %1").arg(path), 8000);
}

void MainWindow::exportHtml() {
    if (data_.recordCount() == 0) {
        QMessageBox::information(this, QStringLiteral("Keine Daten"), QStringLiteral("Bitte zuerst mindestens eine CSV-Datei importieren."));
        return;
    }
    const QString employee = selectedEmployee();
    const QString suggested = employee.isEmpty() ? QStringLiteral("AV-QS-Teambericht.html") : QStringLiteral("AV-QS-Mitarbeiter-%1.html").arg(employee);
    const QString path = QFileDialog::getSaveFileName(this, QStringLiteral("QS-Bericht als HTML speichern"), suggested, QStringLiteral("HTML-Datei (*.html)"));
    if (path.isEmpty()) return;
    QFile file(path);
    if (!file.open(QIODevice::WriteOnly | QIODevice::Text)) {
        QMessageBox::critical(this, QStringLiteral("Speichern fehlgeschlagen"), file.errorString());
        return;
    }
    file.write(data_.qualityReportHtml(employee).toUtf8());
    file.close();
    statusBar()->showMessage(QStringLiteral("QS-Bericht gespeichert: %1").arg(path), 8000);
}

void MainWindow::dragEnterEvent(QDragEnterEvent *event) {
    if (event->mimeData()->hasUrls()) event->acceptProposedAction();
}

void MainWindow::dropEvent(QDropEvent *event) {
    QStringList files;
    for (const QUrl &url : event->mimeData()->urls()) {
        const QString path = url.toLocalFile();
        if (path.endsWith(QStringLiteral(".csv"), Qt::CaseInsensitive)) files << path;
    }
    if (!files.isEmpty()) {
        importFiles(files);
        event->acceptProposedAction();
    }
}
