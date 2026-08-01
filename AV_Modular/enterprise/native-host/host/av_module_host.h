#ifndef AV_MODULE_HOST_H
#define AV_MODULE_HOST_H

#include "av_module_api_v1.h"

#include <filesystem>
#include <mutex>
#include <stdexcept>
#include <string>

class AvModuleError : public std::runtime_error {
public:
    using std::runtime_error::runtime_error;
};

class AvModuleHost final {
public:
    AvModuleHost() = default;
    ~AvModuleHost();
    AvModuleHost(const AvModuleHost&) = delete;
    AvModuleHost& operator=(const AvModuleHost&) = delete;

    void load(const std::filesystem::path& library, const std::string& expected_module_id);
    void initialize(const std::string& configuration_json);
    void validate_record(const std::string& record_json) const;
    std::string process_event(const std::string& event_json) const;
    void close() noexcept;

    const std::string& module_id() const noexcept { return module_id_; }
    const std::string& module_version() const noexcept { return module_version_; }

private:
    void ensure_loaded() const;
    mutable std::mutex mutex_;
    void* handle_ = nullptr;
    const AvModuleApiV1* api_ = nullptr;
    std::string module_id_;
    std::string module_version_;
    bool initialized_ = false;
};

#endif
