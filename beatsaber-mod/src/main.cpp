#include "main.hpp"

#include "GlobalNamespace/AudioTimeSyncController.hpp"
#include "GlobalNamespace/LevelCompletionResultsHelper.hpp"
#include "GlobalNamespace/LevelSelectionFlowCoordinator.hpp"
#include "GlobalNamespace/SoloModeSelectionViewController.hpp"
#include "GlobalNamespace/StandardLevelDetailViewController.hpp"
#include "ModConfig.hpp"
#include "beatsaber-hook/shared/utils/hooking.hpp"
#include "beatsaber-hook/shared/utils/il2cpp-type-check.hpp"
#include "beatsaber-hook/shared/utils/il2cpp-utils.hpp"
#include "beatsaber-hook/shared/utils/logging.hpp"
#include "beatsaber-hook/shared/utils/typedefs.h"
#include "beatsaber-hook/shared/utils/utils.h"
// #include "GlobalNamespace/MainMenuViewController.hpp"
#include "HMUI/CurvedTextMeshPro.hpp"
#include "SettingsViewController.hpp"
#include "UnityEngine/Networking/UnityWebRequest.hpp"
#include "UnityEngine/SceneManagement/SceneManager.hpp"
#include "config-utils/shared/config-utils.hpp"
#include "custom-types/shared/register.hpp"
#include "questui/shared/QuestUI.hpp"

using namespace GlobalNamespace;

static ModInfo modInfo;
DEFINE_CONFIG(ModConfig);

// Loads the config from disk using our modInfo, then returns it for use
Configuration &getConfig() {
  static Configuration config(modInfo);
  config.Load();
  return config;
}

// Returns a logger, useful for printing debug messages
Logger &getLogger() {
  static Logger *logger = new Logger(modInfo);
  return *logger;
}

bool isRecordingCamera = false;

void makeRequestToCamera(std::string path) {
  auto webRequest = UnityEngine::Networking::UnityWebRequest::Get(
      il2cpp_utils::newcsstr(getModConfig().CameraBaseUrl.GetValue() + path));
  // TODO: Check whether the request succeeded or not
  webRequest->SendWebRequest();
}

void startRecordingCamera() {
  if (!getModConfig().Active.GetValue() || isRecordingCamera) return;
  isRecordingCamera = true;
  getLogger().info("Starting camera recording...");
  makeRequestToCamera("/start");
  getLogger().info("Camera recording started");
}

void stopRecordingCamera() {
  if (!getModConfig().Active.GetValue() || !isRecordingCamera) return;
  isRecordingCamera = false;
  getLogger().info("Stopping camera recording...");
  makeRequestToCamera("/stop");
  getLogger().info("Camera recording stopped");
}

// std::optional<UnityEngine::GameObject*> FindComponentWithText(
//     std::string_view find_text) {
//   getLogger().info("Getting text components");
//   auto scene = UnityEngine::SceneManagement::SceneManager::GetActiveScene();
//   auto root_objs = scene.GetRootGameObjects();
//   for (int i = 0; i < root_objs->Length(); i++) {
//     auto text_components =
//         root_objs->values[i]
//             ->GetComponentsInChildren<HMUI::CurvedTextMeshPro>();
//     for (int j = 0; j < text_components->Length(); j++) {
//       auto text = to_utf8(csstrtostr(text_components->values[i].get_text()));
//       getLogger().info("Text: " + text);
//       if (text == find_text) {
//         return text_components->values[i].get_gameObject();
//       }
//     }
//   }
//   return std::nullopt;
// }

// TODO: Is there a single "level has closed" place I can hook into? (maybe
// level destroy?)
MAKE_HOOK_MATCH(LevelCompletionResultsHelper_ProcessScore,
                &LevelCompletionResultsHelper::ProcessScore, void,
                PlayerData *playerData, PlayerLevelStatsData *playerLevelStats,
                LevelCompletionResults *levelCompletionResults,
                IDifficultyBeatmap *difficultyBeatmap,
                PlatformLeaderboardsModel *platformLeaderboardsModel) {
  LevelCompletionResultsHelper_ProcessScore(
      playerData, playerLevelStats, levelCompletionResults, difficultyBeatmap,
      platformLeaderboardsModel);
  getLogger().info("LevelCompletionResultsHelper_ProcessScore");
  stopRecordingCamera();
}

MAKE_HOOK_MATCH(SoloModeSelectionViewController_DidActivate,
                &SoloModeSelectionViewController::DidActivate, void,
                SoloModeSelectionViewController *self, bool firstActivation,
                bool addedToHierarchy, bool screenSystemEnabling) {
  SoloModeSelectionViewController_DidActivate(
      self, firstActivation, addedToHierarchy, screenSystemEnabling);
  getLogger().info("SoloModeSelectionViewController_DidActivate");
  stopRecordingCamera();
}

MAKE_HOOK_MATCH(StandardLevelDetailViewController_DidActivate,
                &StandardLevelDetailViewController::DidActivate, void,
                StandardLevelDetailViewController *self, bool firstActivation,
                bool addedToHierarchy, bool screenSystemEnabling) {
  StandardLevelDetailViewController_DidActivate(
      self, firstActivation, addedToHierarchy, screenSystemEnabling);
  getLogger().info("StandardLevelDetailViewController_DidActivate");
  stopRecordingCamera();
}

MAKE_HOOK_MATCH(AudioTimeSyncController_Start, &AudioTimeSyncController::Start,
                void, AudioTimeSyncController *self) {
  AudioTimeSyncController_Start(self);
  getLogger().info("AudioTimeSyncController_Start");
  if ((strcmp(getenv("ViewingReplay"), "true") == 0)) {
    getLogger().info("Level started in replay mode");
  } else {
    startRecordingCamera();
  }
}

// MAKE_HOOK_MATCH(
//     MainMenuViewController_DidActivate,
//     &MainMenuViewController::DidActivate,
//     void,
//     MainMenuViewController* self,
//     bool firstActivation,
//     bool addedToHierarchy,
//     bool screenSystemEnabling
// ) {
//     MainMenuViewController_DidActivate(self, firstActivation,
//     addedToHierarchy, screenSystemEnabling); auto solo_button =
//     UnityEngine::GameObject::Find(il2cpp_utils::createcsstr("SoloButton"));
//     auto solo_text = solo_button->GetComponent<HMUI::CurvedTextMeshPro>();
//     solo_text.set_text(il2cpp_utils::createcsstr("Hello World"));
// }

extern "C" void setup(ModInfo &info) {
  info.id = ID;
  info.version = VERSION;
  modInfo = info;

  getLogger().info("Completed setup!");
}

extern "C" void load() {
  il2cpp_functions::Init();
  getModConfig().Init(modInfo);

  QuestUI::Init();
  QuestUI::Register::RegisterModSettingsViewController(modInfo, DidActivate);

  getLogger().info("Installing hooks...");
  INSTALL_HOOK(getLogger(), LevelCompletionResultsHelper_ProcessScore);
  INSTALL_HOOK(getLogger(), SoloModeSelectionViewController_DidActivate);
  INSTALL_HOOK(getLogger(), StandardLevelDetailViewController_DidActivate);
  INSTALL_HOOK(getLogger(), AudioTimeSyncController_Start);
  // INSTALL_HOOK(getLogger(), MainMenuViewController_DidActivate);
  getLogger().info("Installed all hooks!");
}
