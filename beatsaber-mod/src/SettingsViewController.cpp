#include <stdlib.h>

#include "HMUI/ModalView.hpp"
#include "HMUI/ScrollView.hpp"
#include "HMUI/Touchable.hpp"
#include "ModConfig.hpp"
#include "UnityEngine/Events/UnityAction.hpp"
#include "UnityEngine/Events/UnityAction_1.hpp"
#include "UnityEngine/RectOffset.hpp"
#include "UnityEngine/RectTransform.hpp"
#include "UnityEngine/UI/Image.hpp"
#include "UnityEngine/UI/LayoutElement.hpp"
#include "UnityEngine/UI/Toggle.hpp"
#include "UnityEngine/UI/Toggle_ToggleEvent.hpp"
#include "UnityEngine/Vector2.hpp"
#include "config-utils/shared/config-utils.hpp"
#include "main.hpp"
#include "questui/shared/BeatSaberUI.hpp"
#include "questui/shared/CustomTypes/Components/Backgroundable.hpp"
#include "questui/shared/CustomTypes/Components/ExternalComponents.hpp"

using namespace QuestUI;
using namespace UnityEngine;
using namespace UnityEngine::UI;
using namespace UnityEngine::Events;
using namespace HMUI;

void DidActivate(ViewController* self, bool firstActivation,
                 bool addedToHierarchy, bool screenSystemEnabling) {
  if (firstActivation) {
    self->get_gameObject()->AddComponent<Touchable*>();
    GameObject* container =
        BeatSaberUI::CreateScrollableSettingsContainer(self->get_transform());

    AddConfigValueToggle(container->get_transform(), getModConfig().Active);
    AddConfigValueStringSetting(container->get_transform(),
                                getModConfig().CameraBaseUrl);
  }
}
