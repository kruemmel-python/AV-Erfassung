#define AV_MODULE_BUILD
#include "av_module_api_v1.h"

#include <cstdlib>
#include <cstring>
#include <string>

namespace {
bool initialized = false;

int32_t initialize(const char* configuration_json) {
    if (configuration_json == nullptr || std::strlen(configuration_json) == 0) return 10;
    initialized = true;
    return 0;
}

int32_t validate_record(const char* record_json) {
    if (!initialized) return 20;
    if (record_json == nullptr || std::strlen(record_json) == 0) return 21;
    return 0;
}

int32_t process_event(const char* event_json, char** result_json) {
    if (!initialized) return 30;
    if (event_json == nullptr || result_json == nullptr) return 31;
    const std::string result = R"({"status":"accepted","module_id":"example_native"})";
    auto* buffer = static_cast<char*>(std::malloc(result.size() + 1));
    if (buffer == nullptr) return 32;
    std::memcpy(buffer, result.c_str(), result.size() + 1);
    *result_json = buffer;
    return 0;
}

void free_result(char* result_json) { std::free(result_json); }
void shutdown() { initialized = false; }

const AvModuleApiV1 api = {
    AV_MODULE_ABI_VERSION_1,
    "example_native",
    "1.0.0-RC2",
    initialize,
    validate_record,
    process_event,
    free_result,
    shutdown,
};
}

extern "C" AV_MODULE_EXPORT const AvModuleApiV1* av_module_get_api_v1(void) {
    return &api;
}
