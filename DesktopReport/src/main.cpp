#include "mainwindow.h"

#include <QApplication>
#include <QComboBox>
#include <QFileInfo>
#include <QTabWidget>
#include <QTimer>

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("AV-Schichtreport"));
    QApplication::setApplicationVersion(QStringLiteral("1.0.0"));
    QApplication::setOrganizationName(QStringLiteral("Ralf Krümmel"));

    QStringList files;
    QString screenshotPath;
    QString selectedEmployee;
    int initialTab = 0;
    for (int i = 1; i < argc; ++i) {
        const QString path = QString::fromLocal8Bit(argv[i]);
        if (path == QStringLiteral("--screenshot") && i + 1 < argc) {
            screenshotPath = QString::fromLocal8Bit(argv[++i]);
        } else if (path == QStringLiteral("--tab") && i + 1 < argc) {
            initialTab = QString::fromLocal8Bit(argv[++i]).toInt();
        } else if (path == QStringLiteral("--employee") && i + 1 < argc) {
            selectedEmployee = QString::fromLocal8Bit(argv[++i]);
        } else if (QFileInfo(path).isFile()) {
            files << path;
        }
    }

    MainWindow window;
    window.show();
    if (!files.isEmpty()) window.importFiles(files);
    if (!selectedEmployee.isEmpty()) {
        if (auto *filter = window.findChild<QComboBox *>()) {
            const int index = filter->findData(selectedEmployee);
            if (index >= 0) filter->setCurrentIndex(index);
        }
    }
    if (auto *tabs = window.findChild<QTabWidget *>()) tabs->setCurrentIndex(qBound(0, initialTab, tabs->count() - 1));
    if (!screenshotPath.isEmpty()) {
        QTimer::singleShot(700, [&window, &app, screenshotPath]() {
            const bool saved = window.grab().save(screenshotPath, "PNG");
            app.exit(saved ? 0 : 5);
        });
    }
    return app.exec();
}
