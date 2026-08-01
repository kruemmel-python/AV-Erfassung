#include <array>
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
#include <algorithm>

#include "av_module_api_v1.h"

#ifdef _WIN32
#include <windows.h>
#include <bcrypt.h>
#else
#error "The certified SHA-256 provider is currently implemented for Windows only"
#endif

namespace {
enum class RevisionDecision { separate, duplicate, newer_revision, older_revision, conflict };

RevisionDecision decide_revision(
    const std::string& existing_id,
    const std::uint64_t existing_revision,
    const std::string& existing_digest,
    const std::string& incoming_id,
    const std::uint64_t incoming_revision,
    const std::string& incoming_digest
) {
    if (existing_id != incoming_id) return RevisionDecision::separate;
    if (existing_digest == incoming_digest) return RevisionDecision::duplicate;
    if (incoming_revision > existing_revision) return RevisionDecision::newer_revision;
    if (incoming_revision < existing_revision) return RevisionDecision::older_revision;
    return RevisionDecision::conflict;
}

bool safe_package_path(const std::string& path) {
    if (path.empty() || path.front() == '/' || path.front() == '\\') return false;
    std::istringstream segments(path);
    std::string segment;
    while (std::getline(segments, segment, '/')) {
        if (segment == ".." || segment.empty()) return false;
    }
    return path.find(':') == std::string::npos;
}

bool diagnostic_fields_allowed(const std::vector<std::string>& fields) {
    const std::array<std::string, 11U> allowed{
        "contract", "generated_at_utc", "core_version", "module_id", "module_version", "module_schema_version",
        "profile_id", "environment", "timezone", "storage_schema_version", "counters",
    };
    return std::all_of(fields.begin(), fields.end(), [&allowed](const std::string& field) {
        return std::find(allowed.begin(), allowed.end(), field) != allowed.end();
    });
}

bool compatible(const std::array<unsigned int, 6U>& required, const std::array<unsigned int, 6U>& offered) {
    return required == offered;
}

std::string join_payload(const std::vector<std::string>& fields) {
    std::string result;
    for (std::size_t index = 0; index < fields.size(); ++index) {
        if (index != 0U) result.push_back('\x1f');
        result.append(fields[index]);
    }
    return result;
}

void require_status(const NTSTATUS status, const char* operation) {
    if (status < 0) throw std::runtime_error(std::string(operation) + " failed");
}

std::string sha256(const std::string& value) {
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    require_status(BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0U), "BCryptOpenAlgorithmProvider");
    try {
        DWORD object_size = 0U;
        DWORD result_size = 0U;
        require_status(BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PUCHAR>(&object_size), sizeof(object_size), &result_size, 0U), "BCryptGetProperty");
        std::vector<std::uint8_t> object(object_size);
        require_status(BCryptCreateHash(algorithm, &hash, object.data(), object_size, nullptr, 0U, 0U), "BCryptCreateHash");
        require_status(BCryptHashData(hash, reinterpret_cast<PUCHAR>(const_cast<char*>(value.data())), static_cast<ULONG>(value.size()), 0U), "BCryptHashData");
        std::array<std::uint8_t, 32U> digest{};
        require_status(BCryptFinishHash(hash, digest.data(), static_cast<ULONG>(digest.size()), 0U), "BCryptFinishHash");
        BCryptDestroyHash(hash);
        hash = nullptr;
        BCryptCloseAlgorithmProvider(algorithm, 0U);
        algorithm = nullptr;
        std::ostringstream text;
        text << std::hex << std::setfill('0');
        for (const auto byte : digest) text << std::setw(2) << static_cast<unsigned int>(byte);
        return text.str();
    } catch (...) {
        if (hash != nullptr) BCryptDestroyHash(hash);
        if (algorithm != nullptr) BCryptCloseAlgorithmProvider(algorithm, 0U);
        throw;
    }
}
}

int main() {
    try {
        static_assert(AV_MODULE_ABI_VERSION_1 == 1U);
        static_assert(sizeof(decltype(AvModuleApiV1::abi_version)) == sizeof(std::uint32_t));
        const auto payload = join_payload({
            "tenant_demo", "mail_processing", "2", "WI-001", "SHIFT-001", "10001", "tagespost",
            "2026-08-01T04:00:00Z", "2026-08-01T04:18:00Z", "completed", "{}", "false", "false", "1080", "1200",
        });
        const std::string expected = "3b41d63066d7d81b6196eeb804281558ddb10391938cb903713b9525a9507b4c";
        const auto actual = sha256(payload);
        if (actual != expected) {
            std::cerr << "AVM-CSV-2006 expected=" << expected << " actual=" << actual << '\n';
            return 1;
        }
        std::cout << "PASS CPP-CANONICAL-001 " << actual << '\n';
        if (decide_revision("WI-1", 4U, "aaa", "WI-1", 4U, "bbb") != RevisionDecision::conflict) return 3;
        if (decide_revision("WI-1", 1U, "aaa", "WI-1", 2U, "bbb") != RevisionDecision::newer_revision) return 4;
        std::cout << "PASS CPP-WORK-RECORD-001 deterministic revisions\n";
        if (safe_package_path("../outside.json") || !safe_package_path("module/processes.json")) return 5;
        std::cout << "PASS CPP-PACKAGE-001 safe paths\n";
        if (diagnostic_fields_allowed({"contract", "employee_id"}) || !diagnostic_fields_allowed({"contract", "counters"})) return 6;
        std::cout << "PASS CPP-DIAGNOSTIC-001 allowlist\n";
        if (compatible({1U, 1U, 2U, 1U, 1U, 1U}, {2U, 1U, 2U, 1U, 1U, 1U})) return 7;
        if (!compatible({1U, 1U, 2U, 1U, 1U, 1U}, {1U, 1U, 2U, 1U, 1U, 1U})) return 8;
        std::cout << "PASS CPP-COMPATIBILITY-001 major versions\n";
        std::cout << "PASS CPP-ABI-001 ABI v1 layout\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "AVM-CSV-2006 " << error.what() << '\n';
        return 2;
    }
}
