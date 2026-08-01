#include "av_module_host.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace {
using GetApiFunction = const AvModuleApiV1* (*)();

void* open_library(const std::filesystem::path& path) {
#ifdef _WIN32
    return reinterpret_cast<void*>(LoadLibraryW(path.wstring().c_str()));
#else
    return dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
#endif
}

void close_library(void* handle) {
#ifdef _WIN32
    if (handle) FreeLibrary(reinterpret_cast<HMODULE>(handle));
#else
    if (handle) dlclose(handle);
#endif
}

void* load_symbol(void* handle, const char* name) {
#ifdef _WIN32
    return reinterpret_cast<void*>(GetProcAddress(reinterpret_cast<HMODULE>(handle), name));
#else
    return dlsym(handle, name);
#endif
}
}

AvModuleHost::~AvModuleHost() { close(); }

void AvModuleHost::load(const std::filesystem::path& library, const std::string& expected_module_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (handle_ != nullptr) throw AvModuleError("Es ist bereits ein Modul geladen");
    if (!std::filesystem::is_regular_file(library)) throw AvModuleError("Moduldatei fehlt");
    handle_ = open_library(std::filesystem::absolute(library));
    if (handle_ == nullptr) throw AvModuleError("Modulbibliothek konnte nicht geladen werden");
    auto* function = reinterpret_cast<GetApiFunction>(load_symbol(handle_, "av_module_get_api_v1"));
    if (function == nullptr) { close_library(handle_); handle_ = nullptr; throw AvModuleError("ABI-Einstiegspunkt fehlt"); }
    api_ = function();
    if (api_ == nullptr || api_->abi_version != AV_MODULE_ABI_VERSION_1 || api_->module_id == nullptr || api_->module_version == nullptr) {
        close_library(handle_); handle_ = nullptr; api_ = nullptr; throw AvModuleError("ABI-Version oder Modulmetadaten ungültig");
    }
    if (!expected_module_id.empty() && expected_module_id != api_->module_id) {
        close_library(handle_); handle_ = nullptr; api_ = nullptr; throw AvModuleError("Modul-ID stimmt nicht mit dem Paketmanifest überein");
    }
    if (!api_->initialize || !api_->validate_record || !api_->process_event || !api_->free_result || !api_->shutdown) {
        close_library(handle_); handle_ = nullptr; api_ = nullptr; throw AvModuleError("ABI-Funktionstabelle ist unvollständig");
    }
    module_id_ = api_->module_id;
    module_version_ = api_->module_version;
}

void AvModuleHost::initialize(const std::string& configuration_json) {
    std::lock_guard<std::mutex> lock(mutex_);
    ensure_loaded();
    const auto code = api_->initialize(configuration_json.c_str());
    if (code != 0) throw AvModuleError("Modulinitialisierung fehlgeschlagen: " + std::to_string(code));
    initialized_ = true;
}

void AvModuleHost::validate_record(const std::string& record_json) const {
    std::lock_guard<std::mutex> lock(mutex_);
    ensure_loaded();
    if (!initialized_) throw AvModuleError("Modul wurde nicht initialisiert");
    const auto code = api_->validate_record(record_json.c_str());
    if (code != 0) throw AvModuleError("Datensatzprüfung fehlgeschlagen: " + std::to_string(code));
}

std::string AvModuleHost::process_event(const std::string& event_json) const {
    std::lock_guard<std::mutex> lock(mutex_);
    ensure_loaded();
    if (!initialized_) throw AvModuleError("Modul wurde nicht initialisiert");
    char* raw = nullptr;
    const auto code = api_->process_event(event_json.c_str(), &raw);
    if (code != 0 || raw == nullptr) throw AvModuleError("Ereignisverarbeitung fehlgeschlagen: " + std::to_string(code));
    const std::string result(raw);
    api_->free_result(raw);
    return result;
}

void AvModuleHost::close() noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    if (api_ && initialized_) api_->shutdown();
    close_library(handle_);
    handle_ = nullptr;
    api_ = nullptr;
    module_id_.clear();
    module_version_.clear();
    initialized_ = false;
}

void AvModuleHost::ensure_loaded() const {
    if (handle_ == nullptr || api_ == nullptr) throw AvModuleError("Kein Modul geladen");
}
