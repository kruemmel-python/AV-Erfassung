QT += core gui widgets printsupport
CONFIG += c++17 release
TEMPLATE = app
TARGET = AV-Schichtreport
AV_SCHICHTREPORT_VERSION = 1.1.0
DEFINES += AV_SCHICHTREPORT_VERSION=\\\"$$AV_SCHICHTREPORT_VERSION\\\"

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
QMAKE_CXXFLAGS += -Wall -Wextra -Werror
