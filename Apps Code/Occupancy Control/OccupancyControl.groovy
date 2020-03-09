import com.hubitat.hub.domain.Event
import groovy.transform.Field

// Device types that can be controlled, used as settings key prefixes
@Field private static final String DIMMER = "dimmer"
@Field private static final String SWITCH = "switch"

@Field private static final Long MAX_CONFIG_COUNT_PER_DEVICE_TYPE = 10L

@Field private static final String REQUIRED_BECAUSE_DEPENDENT = " (required because dependent setting configured)"

definition(
    name: "Occupancy Control",
    namespace: "gtg465x",
    author: "Grayson Carr",
    description: "Control devices based on occupancy / motion",
    category: "My Apps",
    parent: "gtg465x:Occupancy Control Apps",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

preferences {
    page(name: "mainPage")
    page(name: "dimmersPage")
    page(name: "switchesPage")
    page(name: "offDelayPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", install: true, uninstall: true) {
        section {
            label name: "appLabel",
                title: "Name this Occupancy Control app",
                required: true
        }

        def modeNames = location.modes.collect { it.name }
        section("Sensors") {
            boolean motionSensorsRequired = !motionSensors && motionSensorModeNames
            input "motionSensors", "capability.motionSensor",
                title: "Motion Sensors${motionSensorsRequired ? REQUIRED_BECAUSE_DEPENDENT : ""}",
                multiple: true,
                submitOnChange: true,
                required: motionSensorsRequired
            if (motionSensors || motionSensorModeNames) {
                input "motionSensorModeNames", "enum",
                    title: "> Motion sensor modes",
                    description: "Select modes in which the motion sensors should control devices",
                    options: modeNames - virtualMotionSensorModeNames,
                    multiple: true,
                    submitOnChange: true,
                    required: motionSensors
            }

            boolean virtualMotionSensorRequired = !virtualMotionSensor && virtualMotionSensorModeNames
            input "virtualMotionSensor", "capability.switch",
                title: "Virtual Motion Sensor${virtualMotionSensorRequired ? REQUIRED_BECAUSE_DEPENDENT : ""}",
                description: "Click to set a switch to act as a virtual motion sensor",
                submitOnChange: true,
                required: virtualMotionSensorRequired
            if (virtualMotionSensor || virtualMotionSensorModeNames) {
                input "virtualMotionSensorModeNames", "enum",
                    title: "> Virtual motion sensor modes",
                    description: "Select modes in which the virtual motion sensor should control devices",
                    options: modeNames - motionSensorModeNames,
                    multiple: true,
                    submitOnChange: true,
                    required: virtualMotionSensor
            }
            if (virtualMotionSensor?.hasCapability("SwitchLevel")) {
                input "virtualMotionSensorActiveLevel", "number",
                    title: "> Virtual motion sensor level to indicate motion active",
                    description: "1..100 or empty for any on event to indicate motion active",
                    range: "1..100"
            }
        }

        def selectedModeNames = modeNames.findAll {
            motionSensorModeNames?.contains(it) || virtualMotionSensorModeNames?.contains(it)
        }
        section("Devices To Control") {
            deviceTypePreferences(DIMMER, "Level", selectedModeNames)
            deviceTypePreferences(SWITCH, "Action", selectedModeNames, "es")
        }

        section("Other Settings") {
            def toOffDelayPageDescription = selectedModeNames.any { getOffDelayForMode(it) } ?
                {
                    def offDelaysText = "Off delay for mode:<br>" + selectedModeNames.sum { modeName ->
                        def offDelay = getOffDelayForMode(modeName)
                        def offDelayText = offDelay ? "${offDelay} minute${offDelay > 1 ? "s" : ""}" : "None"
                        "  <b>${modeName}</b>: ${offDelayText}<br>"
                    }
                    "<font color=\"#1a77c9\">${offDelaysText}</font>"
                }()
                : "Click to set off delay per mode"
            href name: "toOffDelayPage", page: "offDelayPage",
                title: "Off Delay",
                description: toOffDelayPageDescription,
                params: [selectedModeNames: selectedModeNames]
            input "disableSwitch", "capability.switch",
                title: "Disable Switch",
                description: "Click to set a switch to disable this app when on"
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
        }
    }
}

private void deviceTypePreferences(String deviceType, String deviceValueDescription, List<String> selectedModeNames,
                                   String pluralSuffix = "s") {
    input "${deviceType}ConfigCount", "number",
        title: "Number of ${deviceType} configurations",
        submitOnChange: true,
        required: true,
        defaultValue: 0
    (0..<MAX_CONFIG_COUNT_PER_DEVICE_TYPE).each { i ->
        boolean configEnabled = i < (Long) settings["${deviceType}ConfigCount"]
        def devices = getDevices(deviceType, i)
        if (configEnabled || devices) {
            def toDevicesPageDescription = devices ?
                {
                    def deviceNamesText = devices.sum { "${it}<br>" }
                    if (configEnabled) {
                        def deviceValuesText = selectedModeNames ?
                            "<br>${deviceValueDescription} for mode:<br>" + selectedModeNames.sum { modeName ->
                                def valueText = getDeviceValueForMode(deviceType, i, modeName) ?: "None (vacancy mode)"
                                def onlyIfDevice = getOnlyIfDeviceForMode(deviceType, i, modeName)
                                def onlyIfText = onlyIfDevice ? " (only if ${onlyIfDevice} on)" : ""
                                "  <b>${modeName}</b>: ${valueText}${onlyIfText}<br>"
                            }
                            : ""
                        def devicesToGroupNames = getDevicesToGroupNames(deviceType, i)
                        def groupDeviceText = devicesToGroupNames ?
                            "<br>Control ${devicesToGroupNames} via group ${deviceType} ${getGroupDevice(deviceType, i)}"
                            : ""
                        "<font color=\"#1a77c9\">${deviceNamesText}${deviceValuesText}${groupDeviceText}</font>"
                    } else {
                        "<font color=\"#ff0000\">${deviceNamesText}</font>"
                    }
                }()
                : "Click to set"
            href name: "toDevicesPage", page: "${deviceType}${pluralSuffix}Page",
                title: "${deviceType.capitalize()}${pluralSuffix}${configEnabled ? "" : " (Disabled)"}",
                description: toDevicesPageDescription,
                params: [index: i, selectedModeNames: selectedModeNames]
        }
    }
}

def dimmersPage(Map params) {
    dynamicPage(name: "dimmersPage") {
        devicesPagePreferences(DIMMER, params.index, (List) params.selectedModeNames, "capability.switchLevel")
    }
}

def switchesPage(Map params) {
    dynamicPage(name: "switchesPage") {
        devicesPagePreferences(SWITCH, params.index, (List) params.selectedModeNames, "capability.switch", "es")
    }
}

private void devicesPagePreferences(String deviceType, configIndex, List<String> selectedModeNames,
                                    String capabilityName, String pluralSuffix = "s") {
    def devices = getDevices(deviceType, configIndex)
    def devicesToGroupNames = getDevicesToGroupNames(deviceType, configIndex)
    def groupDevice = getGroupDevice(deviceType, configIndex)
    section {
        boolean anyDeviceValueOrOnlyIfDeviceSet = selectedModeNames.any {
            getDeviceValueForMode(deviceType, configIndex, it) || getOnlyIfDeviceForMode(deviceType, configIndex, it)
        }
        boolean devicesRequired = !devices && (anyDeviceValueOrOnlyIfDeviceSet || devicesToGroupNames || groupDevice)
        input getDevicesSettingsKey(deviceType, configIndex), capabilityName,
            title: "${deviceType.capitalize()}${pluralSuffix}${devicesRequired ? REQUIRED_BECAUSE_DEPENDENT : ""}",
            multiple: true,
            submitOnChange: true,
            required: devicesRequired
        if (devices || anyDeviceValueOrOnlyIfDeviceSet) {
            selectedModeNames.each { modeName ->
                def deviceValue = getDeviceValueForMode(deviceType, configIndex, modeName)
                def onlyIfDevice = getOnlyIfDeviceForMode(deviceType, configIndex, modeName)
                boolean deviceValueRequired = !deviceValue && onlyIfDevice
                deviceValueInput(deviceType, configIndex, modeName, deviceValueRequired)
                if (deviceValue || onlyIfDevice) {
                    input getOnlyIfDeviceForModeSettingsKey(deviceType, configIndex, modeName), "capability.switch",
                        title: "> Only if this device is on",
                        submitOnChange: true
                }
            }
        }
    }
    if (devices?.size() > 1 || devicesToGroupNames || groupDevice) {
        section("Control ${deviceType}${pluralSuffix} via group ${deviceType}") {
            boolean devicesToGroupNamesRequired = !devicesToGroupNames && groupDevice
            input getDevicesToGroupNamesSettingsKey(deviceType, configIndex), "enum",
                title: "${deviceType.capitalize()}${pluralSuffix}${devicesToGroupNamesRequired ? REQUIRED_BECAUSE_DEPENDENT : ""}",
                description: "Click to set ${deviceType}${pluralSuffix} to control via group ${deviceType}",
                options: devices.collect { "${it}" },
                multiple: true,
                submitOnChange: true,
                required: devicesToGroupNamesRequired
            if (devicesToGroupNames || groupDevice) {
                input getGroupDeviceSettingsKey(deviceType, configIndex), capabilityName,
                    title: "Group ${deviceType.capitalize()}",
                    submitOnChange: true,
                    required: devicesToGroupNames
            }
        }
    }
}

private void deviceValueInput(String deviceType, configIndex, String modeName, boolean required) {
    switch (deviceType) {
        case DIMMER:
            input getDeviceValueForModeSettingsKey(deviceType, configIndex, modeName), "number",
                title: "Level for <b>${modeName}</b>${required ? REQUIRED_BECAUSE_DEPENDENT : ""}",
                description: "1..100 or empty for vacancy mode",
                range: "1..100",
                submitOnChange: true,
                required: required
            break
        case SWITCH:
            input getDeviceValueForModeSettingsKey(deviceType, configIndex, modeName), "enum",
                title: "Action for <b>${modeName}</b>${required ? REQUIRED_BECAUSE_DEPENDENT : ""}",
                description: "None (vacancy mode)",
                options: ["On"],
                submitOnChange: true,
                required: required
            break
        default:
            log.error "Unable to create device value input for unknown device type ${deviceType}"
    }
}

def offDelayPage(Map params) {
    dynamicPage(name: "offDelayPage") {
        section {
            params.selectedModeNames.each { modeName ->
                input getOffDelayForModeSettingsKey(modeName), "number",
                    title: "Off delay for <b>${modeName}</b>",
                    description: "Minutes to delay turning off devices after motion inactive, or empty for no delay",
                    range: "1..*"
            }
        }
    }
}

void installed() {
    if (logEnable) log.debug "Installed with settings: ${settings}"
    initialize()
}

void updated() {
    if (logEnable) log.debug "Updated with settings: ${settings}"
    disable()
    unsubscribe()
    initialize()
}

private void initialize() {
    if (disableSwitch) {
        subscribe(disableSwitch, "switch.on", disableSwitchOnHandler, [filterEvents: false])
        subscribe(disableSwitch, "switch.off", disableSwitchOffHandler, [filterEvents: false])
    }
    if (!disableSwitch || !isOn(disableSwitch)) {
        enable()
    }
}

void disableSwitchOnHandler(Event evt) {
    disable()
}

void disableSwitchOffHandler(Event evt) {
    enable()
}

private void enable() {
    subscribe(location, "mode", modeChangeHandler)
    updateSensorSubscriptions()
    if (logEnable) log.debug "Enabled"
}

private void disable() {
    unsubscribe(location)
    updateSensorSubscriptions(false)
    if (logEnable) log.debug "Disabled"
}

void modeChangeHandler(Event evt) {
    updateSensorSubscriptions()
}

private void updateSensorSubscriptions(boolean enabled = true) {
    boolean shouldSubscribeToMotionSensors = false
    boolean shouldSubscribeToVirtualMotionSensor = false
    if (enabled) {
        def currentModeName = location.currentMode.name
        if (motionSensorModeNames?.contains(currentModeName)) {
            shouldSubscribeToMotionSensors = true
        } else if (virtualMotionSensorModeNames?.contains(currentModeName)) {
            shouldSubscribeToVirtualMotionSensor = true
        }
    }
    for (subscription in app.getSubscriptions()) {
        if (subscription.handler == "motionActiveHandler") {
            if (shouldSubscribeToMotionSensors) {
                return
            }
            unsubscribeFromMotionSensors()
            break
        } else if (subscription.handler == "virtualMotionLevelHandler" || subscription.handler == "virtualMotionActiveHandler") {
            if (shouldSubscribeToVirtualMotionSensor) {
                return
            }
            unsubscribeFromVirtualMotionSensor()
            break
        }
    }
    if (shouldSubscribeToMotionSensors) {
        subscribeToMotionSensors()
    } else if (shouldSubscribeToVirtualMotionSensor) {
        subscribeToVirtualMotionSensor()
    }
}

private void subscribeToMotionSensors() {
    if (logEnable) log.debug "Subscribing to motion sensor events: ${motionSensors}"
    subscribe(motionSensors, "motion.active", motionActiveHandler, [filterEvents: false])
    subscribe(motionSensors, "motion.inactive", motionInactiveHandler, [filterEvents: false])
    if (isMotionActiveExcludingMotionSensorWithId()) {
        motionActive()
    } else {
        turnOffDevices()
    }
}

private void unsubscribeFromMotionSensors() {
    if (logEnable) log.debug "Unsubscribing from motion sensor events: ${motionSensors}"
    unsubscribe(motionSensors)
}

private void subscribeToVirtualMotionSensor() {
    if (logEnable) log.debug "Subscribing to virtual motion sensor events: ${virtualMotionSensor}"
    subscribe(virtualMotionSensor, "switch.off", virtualMotionInactiveHandler, [filterEvents: false])
    if (virtualMotionSensorActiveLevel && virtualMotionSensor.hasCapability("SwitchLevel")) {
        subscribe(virtualMotionSensor, "level", virtualMotionLevelHandler, [filterEvents: false])
        if (isOn(virtualMotionSensor)) {
            if (isAtLevel(virtualMotionSensor, virtualMotionSensorActiveLevel)) {
                motionActive()
            }
        } else {
            turnOffDevices()
        }
    } else {
        subscribe(virtualMotionSensor, "switch.on", virtualMotionActiveHandler, [filterEvents: false])
        if (isOn(virtualMotionSensor)) {
            motionActive()
        } else {
            turnOffDevices()
        }
    }
}

private void unsubscribeFromVirtualMotionSensor() {
    if (logEnable) log.debug "Unsubscribing from virtual motion sensor events: ${virtualMotionSensor}"
    unsubscribe(virtualMotionSensor)
}

void motionActiveHandler(Event evt) {
    if (logEnable) log.debug "Motion active: ${evt.displayName}"
    if (isMotionActiveExcludingMotionSensorWithId(evt.deviceId)) {
        if (logEnable) log.debug "Not turning on: motion already active"
        return
    }
    motionActive()
}

void motionInactiveHandler(Event evt) {
    if (logEnable) log.debug "Motion inactive: ${evt.displayName}"
    if (isMotionActiveExcludingMotionSensorWithId(evt.deviceId)) {
        if (logEnable) log.debug "Not turning off: motion still active"
        return
    }
    motionInactiveOnAllSensors()
}

private boolean isMotionActiveExcludingMotionSensorWithId(Long id = -1L) {
    for (motionSensor in motionSensors) {
        if (Long.valueOf(motionSensor.id) != id && isMotionActive(motionSensor)) {
            return true
        }
    }
    return false
}

void virtualMotionLevelHandler(Event evt) {
    if (logEnable) log.debug "Virtual motion active with level ${evt.integerValue}: ${evt.displayName}"
    // TODO: Watch for false activations. May need to call isOn before checking level
    if (evt.integerValue == virtualMotionSensorActiveLevel) {
        motionActive()
    } else {
        if (logEnable) log.debug "Not turning on: wrong virtual motion level"
    }
}

void virtualMotionActiveHandler(Event evt) {
    if (logEnable) log.debug "Virtual motion active: ${evt.displayName}"
    motionActive()
}

void virtualMotionInactiveHandler(Event evt) {
    if (logEnable) log.debug "Virtual motion inactive: ${evt.displayName}"
    motionInactiveOnAllSensors()
}

private boolean startOffDelayIfConfigured() {
    def offDelay = getOffDelayForMode(location.currentMode.name)
    if (offDelay) {
        runIn((Long) offDelay * 60L, "offDelayHandler")
        if (logEnable) log.debug "Off delay started: ${offDelay} minute(s)"
        state.offDelayActive = true
        return true
    }
    return false
}

private void cancelOffDelay() {
    unschedule(offDelayHandler)
    if (logEnable) log.debug "Off delay canceled"
    state.offDelayActive = false
}

void offDelayHandler() {
    if (!isMotionActiveForUnknownSensorType()) {
        if (logEnable) log.debug "Off delay ended"
        turnOffDevices()
        state.offDelayActive = false
    }
}

private boolean isMotionActiveForUnknownSensorType() {
    def currentModeName = location.currentMode.name
    if (motionSensorModeNames?.contains(currentModeName)) {
        return isMotionActiveExcludingMotionSensorWithId()
    } else if (virtualMotionSensorModeNames?.contains(currentModeName)) {
        if (virtualMotionSensorActiveLevel && virtualMotionSensor.hasCapability("SwitchLevel")) {
            return isOn(virtualMotionSensor) && isAtLevel(virtualMotionSensor, virtualMotionSensorActiveLevel)
        } else {
            return isOn(virtualMotionSensor)
        }
    }
    return false
}

private void motionActive() {
    if (state.offDelayActive) {
        if (logEnable) log.debug "Not turning on: off delay active"
        cancelOffDelay()
        return
    }

    def currentModeName = location.currentMode.name
    def onCommands = getOnCommands(DIMMER, dimmerConfigCount, currentModeName, "setLevel", true)
    if (onCommands == null) {
        // A dimmer is already on
        return
    }
    def switchOnCommands = getOnCommands(SWITCH, switchConfigCount, currentModeName, "on", false)
    if (switchOnCommands == null) {
        // A switch is already on
        return
    }
    onCommands.addAll(switchOnCommands)

    //  If onCommands is empty, all devices are in vacancy mode or are disabled by an only if condition
    if (onCommands.size() == 1) {
        onCommands[0]()
    } else {
        // Must use Java Stream method forEach() instead of Groovy collection method each() for parallel stream to work
        onCommands.parallelStream().forEach { it() }
    }
}

/**
 * Determines based on settings and device state which devices should be turned on, and returns a list of callable
 * commands to turn them on, or null if any device is already on and nothing should be turned on.
 */
private List<Closure> getOnCommands(String deviceType, Long deviceConfigCount, String modeName,
                                    String onMethod, boolean onMethodTakesValue) {
    List<Closure> onCommands = []
    for (i in 0..<deviceConfigCount) {
        def deviceValue = getDeviceValueForMode(deviceType, i, modeName)
        if (deviceValue) {
            def onlyIfDevice = getOnlyIfDeviceForMode(deviceType, i, modeName)
            if (onlyIfDevice == null || isOn(onlyIfDevice)) {
                def devicesToGroupNames = getDevicesToGroupNames(deviceType, i)
                boolean groupOnCommandAdded = false
                for (device in getDevices(deviceType, i)) {
                    if (isOn(device)) {
                        if (logEnable) log.debug "Not turning on: already on: ${device}"
                        return null
                    }
                    if (devicesToGroupNames?.contains(device.displayName)) {
                        if (groupOnCommandAdded) {
                            continue
                        }
                        device = getGroupDevice(deviceType, i)
                        groupOnCommandAdded = true
                    }
                    onCommands.add({ d, v ->
                        if (onMethodTakesValue) {
                            d."${onMethod}"(v)
                        } else {
                            d."${onMethod}"()
                        }
                        if (logEnable) log.debug "Turned on: ${d}"
                    }.curry(device, deviceValue))
                }
            } else {
                if (logEnable) log.debug "Not turning on ${getDevices(deviceType, i)}: device is not on: ${onlyIfDevice}"
            }
        } else {
            if (logEnable) log.debug "Not turning on ${getDevices(deviceType, i)}: vacancy mode"
        }
    }
    return onCommands
}

private void motionInactiveOnAllSensors() {
    if (!startOffDelayIfConfigured()) {
        turnOffDevices()
    }
}

private void turnOffDevices() {
    List devicesToTurnOff = getDevicesToTurnOff(DIMMER, dimmerConfigCount)
    devicesToTurnOff.addAll(getDevicesToTurnOff(SWITCH, switchConfigCount))
    if (devicesToTurnOff.size() == 1) {
        devicesToTurnOff[0].off()
    } else {
        // Must use Java Stream method forEach() instead of Groovy collection method each() for parallel stream to work
        devicesToTurnOff.parallelStream().forEach { it.off() }
    }
    if (logEnable) log.debug devicesToTurnOff.isEmpty() ? "Not turning off: already off" : "Turned off: ${devicesToTurnOff}"
}

private List getDevicesToTurnOff(String deviceType, Long deviceConfigCount) {
    List devicesToTurnOff = []
    for (i in 0..<deviceConfigCount) {
        def devicesToGroupNames = getDevicesToGroupNames(deviceType, i)
        boolean groupDeviceAdded = false
        for (device in getDevices(deviceType, i)) {
            if (isOn(device)) {
                if (devicesToGroupNames?.contains(device.displayName)) {
                    if (groupDeviceAdded) {
                        continue
                    }
                    device = getGroupDevice(deviceType, i)
                    groupDeviceAdded = true
                }
                devicesToTurnOff.add(device)
            }
        }
    }
    return devicesToTurnOff
}

private getDevices(String deviceType, configIndex) {
    return settings[getDevicesSettingsKey(deviceType, configIndex)]
}

private getDeviceValueForMode(String deviceType, configIndex, modeName) {
    return settings[getDeviceValueForModeSettingsKey(deviceType, configIndex, modeName)]
}

private getOnlyIfDeviceForMode(String deviceType, configIndex, modeName) {
    return settings[getOnlyIfDeviceForModeSettingsKey(deviceType, configIndex, modeName)]
}

private getDevicesToGroupNames(String deviceType, configIndex) {
    return settings[getDevicesToGroupNamesSettingsKey(deviceType, configIndex)]
}

private getGroupDevice(String deviceType, configIndex) {
    return settings[getGroupDeviceSettingsKey(deviceType, configIndex)]
}

private getOffDelayForMode(modeName) {
    return settings[getOffDelayForModeSettingsKey(modeName)]
}

private static String getDevicesSettingsKey(String deviceType, configIndex) {
    return "${deviceType}Config${configIndex}Devices"
}

private static String getDeviceValueForModeSettingsKey(String deviceType, configIndex, modeName) {
    return "${deviceType}Config${configIndex}DeviceValueForMode${modeName}"
}

private static String getOnlyIfDeviceForModeSettingsKey(String deviceType, configIndex, modeName) {
    return "${deviceType}Config${configIndex}OnlyIfDeviceForMode${modeName}"
}

private static String getDevicesToGroupNamesSettingsKey(String deviceType, configIndex) {
    return "${deviceType}Config${configIndex}DevicesToGroupNames"
}

private static String getGroupDeviceSettingsKey(String deviceType, configIndex) {
    return "${deviceType}Config${configIndex}GroupDevice"
}

private static String getOffDelayForModeSettingsKey(modeName) {
    return "OffDelayForMode${modeName}"
}

private static boolean isOn(device) {
    return device.currentValue("switch") == "on"
}

private static boolean isAtLevel(device, level) {
    return device.currentValue("level") == level
}

private static boolean isMotionActive(device) {
    return device.currentValue("motion") == "active"
}
