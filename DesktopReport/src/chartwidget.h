#pragma once

#include <QMap>
#include <QWidget>

class ChartWidget : public QWidget {
    Q_OBJECT
public:
    explicit ChartWidget(QWidget *parent = nullptr);
    void setData(const QString &title, const QMap<QString, double> &values,
                 const QString &unit = QString(), double target = -1.0);
    QSize minimumSizeHint() const override;

protected:
    void paintEvent(QPaintEvent *event) override;

private:
    QString title_;
    QString unit_;
    QMap<QString, double> values_;
    double target_ = -1.0;
};
