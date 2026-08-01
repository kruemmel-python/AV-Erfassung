#include "av_module_api_v1.h"

#include <cstring>

int main() {
    const AvModuleApiV1* api = av_module_get_api_v1();
    if (api == nullptr || api->abi_version != AV_MODULE_ABI_VERSION_1) return 1;
    if (std::strcmp(api->module_id, "example_native") != 0) return 2;
    if (api->initialize("{}") != 0) return 3;
    if (api->validate_record("{}") != 0) return 4;

    char* result = nullptr;
    if (api->process_event("{}", &result) != 0 || result == nullptr) return 5;
    const bool accepted = std::strstr(result, "accepted") != nullptr;
    api->free_result(result);
    api->shutdown();
    return accepted ? 0 : 6;
}
