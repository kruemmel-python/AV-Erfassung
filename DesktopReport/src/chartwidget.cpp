#include "chartwidget.h"

#include <QPainter>
#include <QPainterPath>
#include <QLocale>
#include <QtMath>

ChartWidget::ChartWidget(QWidget *parent) : QWidget(parent) {
    setMinimumHeight(285);
    setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
}

void ChartWidget::setData(const QString &title, const QMap<QString, double> &values,
                          const QString &unit, double target) {
    title_ = title;
    values_ = values;
    unit_ = unit;
    target_ = target;
    update();
}

QSize ChartWidget::minimumSizeHint() const { return QSize(390, 285); }

void ChartWidget::paintEvent(QPaintEvent *) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing);
    painter.fillRect(rect(), QColor("#ffffff"));

    painter.setPen(QColor("#222222"));
    QFont titleFont = painter.font();
    titleFont.setPointSize(12);
    titleFont.setBold(true);
    painter.setFont(titleFont);
    painter.drawText(QRect(18, 12, width() - 36, 28), Qt::AlignLeft | Qt::AlignVCenter, title_);

    if (values_.isEmpty()) {
        painter.setPen(QColor("#777777"));
        painter.drawText(rect().adjusted(18, 45, -18, -18), Qt::AlignCenter, QStringLiteral("Keine Daten für die Auswahl"));
        return;
    }

    const int left = 120;
    const int right = 42;
    const int top = 52;
    const int bottom = 24;
    const int plotWidth = qMax(10, width() - left - right);
    const int rowHeight = qMax(25, (height() - top - bottom) / values_.size());
    double maximum = target_ > 0 ? target_ : 0.0;
    for (double value : values_) maximum = qMax(maximum, value);
    maximum = qMax(1.0, maximum * 1.12);

    QFont labelFont = painter.font();
    labelFont.setPointSize(9);
    labelFont.setBold(false);
    painter.setFont(labelFont);

    if (target_ > 0) {
        const int x = left + qRound(plotWidth * target_ / maximum);
        painter.setPen(QPen(QColor("#9b0008"), 2, Qt::DashLine));
        painter.drawLine(x, top - 4, x, top + rowHeight * values_.size() - 5);
        painter.drawText(QRect(x - 55, top - 23, 110, 18), Qt::AlignCenter,
                         QStringLiteral("Ziel %1 %2").arg(QLocale(QLocale::German).toString(target_, 'f', 0), unit_));
    }

    int row = 0;
    const QColor colors[] = {QColor("#d40511"), QColor("#ffcc00"), QColor("#9b0008"),
                             QColor("#f08a00"), QColor("#555555"), QColor("#e55b64")};
    for (auto it = values_.cbegin(); it != values_.cend(); ++it, ++row) {
        const int y = top + row * rowHeight;
        const int barHeight = qMax(12, rowHeight - 10);
        const int barWidth = qRound(plotWidth * it.value() / maximum);
        painter.setPen(QColor("#333333"));
        const QString label = painter.fontMetrics().elidedText(it.key(), Qt::ElideRight, left - 24);
        painter.drawText(QRect(12, y, left - 22, barHeight), Qt::AlignRight | Qt::AlignVCenter, label);

        QPainterPath path;
        path.addRoundedRect(QRectF(left, y, qMax(2, barWidth), barHeight), 4, 4);
        painter.fillPath(path, colors[row % 6]);
        painter.setPen(QColor("#222222"));
        const QString valueText = QStringLiteral("%1 %2")
            .arg(QLocale(QLocale::German).toString(it.value(), 'f', it.value() < 10 ? 1 : 0), unit_);
        painter.drawText(QRect(left + barWidth + 7, y, right + 75, barHeight), Qt::AlignLeft | Qt::AlignVCenter, valueText);
    }
}
