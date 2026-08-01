QT += core
CONFIG += console c++17
CONFIG -= app_bundle
TEMPLATE = app
TARGET = import_test

INCLUDEPATH += ../src
SOURCES += import_test.cpp ../src/csvimporter.cpp ../src/reportdata.cpp
HEADERS += ../src/csvimporter.h ../src/reportdata.h
