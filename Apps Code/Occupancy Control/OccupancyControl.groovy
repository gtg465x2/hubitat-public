import com.hubitat.hub.domain.Event
import groovy.transform.Field

// Device types that can be controlled, used as settings key prefixes
@Field private static final String DIMMER = "dimmer"
@Field private static final String SWITCH = "switch"

definition(
    name: "Occupancy Control",
    namespace: "gtg465x",
    author: "Grayson Carr",
    description: "Control devices based on occupancy",
    category: "My Apps",
    parent: "gtg465x:Occupancy Control Apps",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

preferences {
    page(name: "mainPage")
    page(name: "dimmersPage")
    page(name: "switchesPage")
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
            def motionSensorModeNameOptions = modeNames - virtualMotionSensorModeNames
            if (motionSensorModeNameOptions) {
                input "motionSensors", "capability.motionSensor",
                    title: "Occupancy Sensors",
                    multiple: true, submitOnChange: true, required: !virtualMotionSensor
                if (motionSensors) {
                    input "motionSensorModeNames", "enum",
                        title: "> Occupancy sensor modes",
                        description: "Select modes in which the occupancy sensors should control devices",
                        options: motionSensorModeNameOptions,
                        multiple: true, submitOnChange: true, required: true
                }
            }

            def virtualMotionSensorModeNameOptions = modeNames - motionSensorModeNames
            if (virtualMotionSensorModeNameOptions) {
                input "virtualMotionSensor", "capability.switch",
                    title: "Virtual Occupancy Sensor",
                    description: "Click to set a switch to act as a virtual occupancy sensor",
                    submitOnChange: true
                if (virtualMotionSensor) {
                    input "virtualMotionSensorModeNames", "enum",
                        title: "> Virtual occupancy sensor modes",
                        description: "Select modes in which the virtual occupancy sensor should control devices",
                        options: virtualMotionSensorModeNameOptions,
                        multiple: true, submitOnChange: true, required: true
                    if (virtualMotionSensor.hasCapability("SwitchLevel")) {
                        input "virtualMotionSensorActiveLevel", "number",
                            title: "> Virtual occupancy sensor level to indicate occupancy",
                            description: "1..100 or empty for any on event to indicate occupancy",
                            range: "1..100"
                    }
                }
            }
        }
        section("Devices To Control") {
            def selectedModeNames = modeNames.findAll {
                motionSensorModeNames?.contains(it) || virtualMotionSensorModeNames?.contains(it)
            }

            int defaultDimmerConfigCount = 1
            input "dimmerConfigCount", "number",
                title: "Number of dimmer configurations",
                submitOnChange: true, required: true,
                defaultValue: defaultDimmerConfigCount
            (0..<(dimmerConfigCount != null ? dimmerConfigCount : defaultDimmerConfigCount)).each { i ->
                def dimmers = getDevices(DIMMER, i)
                def toDimmersPageDescription = dimmers ?
                    {
                        def dimmerNamesText = dimmers.sum { "${it}<br>" }
                        def dimmerLevelsText = selectedModeNames ?
                            "Level per mode:<br>" + selectedModeNames.sum { modeName ->
                                def levelText = getDeviceValueForMode(DIMMER, i, modeName) ?: "Vacancy mode"
                                def onlyIfDevice = getOnlyIfDeviceForMode(DIMMER, i, modeName)
                                def onlyIfText = onlyIfDevice ? " (Only if ${onlyIfDevice} is on)" : ""
                                "  <b>${modeName}</b>: ${levelText}${onlyIfText}<br>"
                            }
                            : ""
                        def dimmersToGroupNames = getDevicesToGroupNames(DIMMER, i)
                        def groupDimmerText = dimmersToGroupNames ?
                            "Control ${dimmersToGroupNames} via group dimmer ${getGroupDevice(DIMMER, i)}"
                            : ""
                        "<font color=\"#1a77c9\">${dimmerNamesText}<br>${dimmerLevelsText}${groupDimmerText}</font>"
                    }()
                    : "Click to set"
                href name: "toDimmersPage", page: "dimmersPage",
                    title: "Dimmers",
                    description: toDimmersPageDescription,
                    params: [index: i, selectedModeNames: selectedModeNames]
            }

            int defaultSwitchConfigCount = 0
            input "switchConfigCount", "number",
                title: "Number of switch configurations",
                submitOnChange: true, required: true,
                defaultValue: defaultSwitchConfigCount
            (0..<(switchConfigCount != null ? switchConfigCount : defaultSwitchConfigCount)).each { i ->
                def switches = getDevices(SWITCH, i)
                def toSwitchesPageDescription = switches ?
                    {
                        def switchNamesText = switches.sum { "${it}<br>" }
                        def switchActionsText = selectedModeNames ?
                            "Action per mode:<br>" + selectedModeNames.sum { modeName ->
                                def onText = getDeviceValueForMode(SWITCH, i, modeName) ? "On" : "Vacancy mode"
                                def onlyIfDevice = getOnlyIfDeviceForMode(SWITCH, i, modeName)
                                def onlyIfText = onlyIfDevice ? " (Only if ${onlyIfDevice} is on)" : ""
                                "  <b>${modeName}</b>: ${onText}${onlyIfText}<br>"
                            }
                            : ""
                        def switchesToGroupNames = getDevicesToGroupNames(SWITCH, i)
                        def groupSwitchText = switchesToGroupNames ?
                            "Control ${switchesToGroupNames} via group switch ${getGroupDevice(SWITCH, i)}"
                            : ""
                        "<font color=\"#1a77c9\">${switchNamesText}<br>${switchActionsText}${groupSwitchText}</font>"
                    }()
                    : "Click to set"
                href name: "toSwitchesPage", page: "switchesPage",
                    title: "Switches",
                    description: toSwitchesPageDescription,
                    params: [index: i, selectedModeNames: selectedModeNames]
            }
        }
        section {
            input "disableSwitch", "capability.switch", title: "Disable Switch"
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
        }
    }
}

def dimmersPage(Map params) {
    dynamicPage(name: "dimmersPage") {
        def i = params.index
        section {
            input getDevicesSettingsKey(DIMMER, i), "capability.switchLevel",
                title: "Dimmers",
                multiple: true, submitOnChange: true
            if (getDevices(DIMMER, i)) {
                params.selectedModeNames.each { modeName ->
                    input getDeviceValueForModeSettingsKey(DIMMER, i, modeName), "number",
                        title: "Level for <b>${modeName}</b>",
                        description: "1..100 or empty for vacancy mode",
                        range: "1..100",
                        submitOnChange: true
                    if (getDeviceValueForMode(DIMMER, i, modeName)) {
                        input "onlyIfDeviceOnForMode${modeName}${i}", "bool",
                            title: "> Only if another device is on",
                            submitOnChange: true,
                            defaultValue: false
                        if (settings["onlyIfDeviceOnForMode${modeName}${i}"]) {
                            input getOnlyIfDeviceForModeSettingsKey(DIMMER, i, modeName), "capability.switch",
                                title: "> Only set level for ${modeName} if this device is on",
                                required: true
                        }
                    }
                }
            }
        }
        def dimmers = getDevices(DIMMER, i)
        if (dimmers?.size() > 1) {
            section("Control dimmers via group dimmer") {
                input getDevicesToGroupNamesSettingsKey(DIMMER, i), "enum",
                    title: "Dimmers",
                    description: "Click to set dimmers to control via group dimmer",
                    options: dimmers.collect { "${it}" },
                    multiple: true, submitOnChange: true
                if (getDevicesToGroupNames(DIMMER, i)) {
                    input getGroupDeviceSettingsKey(DIMMER, i), "capability.switchLevel",
                        title: "Group Dimmer",
                        required: true
                }
            }
        }
    }
}

def switchesPage(Map params) {
    dynamicPage(name: "switchesPage") {
        def i = params.index
        section {
            input getDevicesSettingsKey(SWITCH, i), "capability.switch",
                title: "Switches",
                multiple: true, submitOnChange: true
            if (getDevices(SWITCH, i)) {
                params.selectedModeNames.each { modeName ->
                    input getDeviceValueForModeSettingsKey(SWITCH, i, modeName), "bool",
                        title: "Turn on during <b>${modeName}</b>",
                        submitOnChange: true
                    if (getDeviceValueForMode(SWITCH, i, modeName)) {
                        input "onlyTurnOnIfDeviceOnForMode${modeName}${i}", "bool",
                            title: "> Only if another device is on",
                            submitOnChange: true,
                            defaultValue: false
                        if (settings["onlyTurnOnIfDeviceOnForMode${modeName}${i}"]) {
                            input getOnlyIfDeviceForModeSettingsKey(SWITCH, i, modeName), "capability.switch",
                                title: "> Only turn on during ${modeName} if this device is on",
                                required: true
                        }
                    }
                }
            }
        }
        def switches = getDevices(SWITCH, i)
        if (switches?.size() > 1) {
            section("Control switches via group switch") {
                input getDevicesToGroupNamesSettingsKey(SWITCH, i), "enum",
                    title: "Switches",
                    description: "Click to set switches to control via group switch",
                    options: switches.collect { "${it}" },
                    multiple: true, submitOnChange: true
                if (getDevicesToGroupNames(SWITCH, i)) {
                    input getGroupDeviceSettingsKey(SWITCH, i), "capability.switch",
                        title: "Group Switch",
                        required: true
                }
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
        motionInactiveOnAllSensors()
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
        if (isAtLevel(virtualMotionSensor, virtualMotionSensorActiveLevel)) {
            motionActive()
        } else if (!isOn(virtualMotionSensor)) {
            motionInactiveOnAllSensors()
        }
    } else {
        subscribe(virtualMotionSensor, "switch.on", virtualMotionActiveHandler, [filterEvents: false])
        if (isOn(virtualMotionSensor)) {
            motionActive()
        } else {
            motionInactiveOnAllSensors()
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

private void motionActive() {
    def currentModeName = location.currentMode.name
    def dimmerOnCommands = getOnCommands(DIMMER, dimmerConfigCount, currentModeName, "setLevel", true)
    if (dimmerOnCommands == null) {
        // A dimmer is already on
        return
    }
    def switchOnCommands = getOnCommands(SWITCH, switchConfigCount, currentModeName, "on", false)
    if (switchOnCommands == null) {
        // A switch is already on
        return
    }
    def onCommands = dimmerOnCommands + switchOnCommands
    switch (onCommands.size()) {
        case 0:
            // All devices in vacancy mode or disabled by only if condition
            break
        case 1:
            onCommands[0]()
            break
        default:
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
    List devicesToTurnOff = getDevicesToTurnOff(DIMMER, dimmerConfigCount) +
        getDevicesToTurnOff(SWITCH, switchConfigCount)
    switch (devicesToTurnOff.size()) {
        case 0:
            if (logEnable) log.debug "Not turning off: already off"
            return
        case 1:
            devicesToTurnOff[0].off()
            break
        default:
            // Must use Java Stream method forEach() instead of Groovy collection method each() for parallel stream to work
            devicesToTurnOff.parallelStream().forEach { it.off() }
    }
    if (logEnable) log.debug "Turned off: ${devicesToTurnOff}"
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

private static boolean isOn(device) {
    return device.currentValue("switch") == "on"
}

private static boolean isAtLevel(device, level) {
    return device.currentValue("level") == level
}

private static boolean isMotionActive(device) {
    return device.currentValue("motion") == "active"
}
