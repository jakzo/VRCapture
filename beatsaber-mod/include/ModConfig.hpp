#pragma once
#include "extern/config-utils/shared/config-utils.hpp"

DECLARE_CONFIG(ModConfig,

               CONFIG_VALUE(Active, bool, "Active", false);
               CONFIG_VALUE(CameraBaseUrl, std::string, "Camera base URL",
                            "http://192.168.1.103:8080");

               CONFIG_INIT_FUNCTION(CONFIG_INIT_VALUE(Active);
                                    CONFIG_INIT_VALUE(CameraBaseUrl);))
