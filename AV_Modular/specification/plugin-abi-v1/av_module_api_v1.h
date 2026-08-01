#ifndef AV_MODULE_API_V1_H
#define AV_MODULE_API_V1_H

#include <stdint.h>

#if defined(_WIN32)
#  if defined(AV_MODULE_BUILD)
#    define AV_MODULE_EXPORT __declspec(dllexport)
#  else
#    define AV_MODULE_EXPORT __declspec(dllimport)
#  endif
#else
#  define AV_MODULE_EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define AV_MODULE_ABI_VERSION_1 1u

typedef struct AvModuleApiV1 {
    uint32_t abi_version;
    const char* module_id;
    const char* module_version;

    int32_t (*initialize)(const char* configuration_json);
    int32_t (*validate_record)(const char* record_json);
    int32_t (*process_event)(const char* event_json, char** result_json);
    void (*free_result)(char* result_json);
    void (*shutdown)(void);
} AvModuleApiV1;

AV_MODULE_EXPORT const AvModuleApiV1* av_module_get_api_v1(void);

#ifdef __cplusplus
}
#endif

#endif
