QT += core gui widgets printsupport
CONFIG += c++17 release
TEMPLATE = app
TARGET = AV-Schichtreport

SOURCES += \
    src/main.cpp \
    src/csvimporter.cpp \
    src/reportdata.cpp \
    src/chartwidget.cpp \
    src/mainwindow.cpp

HEADERS += \
    src/csvimporter.h \
    src/reportdata.h \
    src/chartwidget.h \
    src/mainwindow.h

RC_ICONS =
QMAKE_CXXFLAGS += -Wall -Wextra
