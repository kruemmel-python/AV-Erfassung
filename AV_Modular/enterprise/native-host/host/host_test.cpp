#include "av_module_host.h"

#include <iostream>

int main(int argc, char** argv) {
    if (argc != 2) return 10;
    try {
        AvModuleHost host;
        host.load(argv[1], "example_native");
        host.initialize("{}");
        host.validate_record("{}");
        const auto result = host.process_event("{}");
        if (result.find("accepted") == std::string::npos) return 11;
        std::cout << host.module_id() << " " << host.module_version() << " " << result << std::endl;
        host.close();
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << std::endl;
        return 12;
    }
}
