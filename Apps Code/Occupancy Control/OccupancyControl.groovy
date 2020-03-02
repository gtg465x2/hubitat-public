import com.hubitat.hub.domain.Event

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
                def dimmers = settings["dimmers${i}"]
                def toDimmersPageDescription = dimmers ?
                    {
                        def dimmerNamesText = dimmers.sum { "${it}<br>" }
                        def dimmerLevelsText = selectedModeNames ?
                            "Level per mode:<br>" + selectedModeNames.sum {
                                def levelText = settings["dimmerLevelForMode${it}${i}"] ?: "Vacancy mode"
                                def onlyIfDevice = settings["onlyIfDeviceForMode${it}${i}"]
                                def onlyIfText = onlyIfDevice ? " (Only if ${onlyIfDevice} is on)" : ""
                                "  <b>${it}</b>: ${levelText}${onlyIfText}<br>"
                            }
                            : ""
                        def dimmersToGroupNames = settings["dimmersToGroupNames${i}"]
                        def groupDimmerText = dimmersToGroupNames ?
                            "Control ${dimmersToGroupNames} via group dimmer ${settings["groupDimmer${i}"]}"
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
                def switches = settings["switches${i}"]
                def toSwitchesPageDescription = switches ?
                    {
                        def switchNamesText = switches.sum { "${it}<br>" }
                        def switchActionsText = selectedModeNames ?
                            "Action per mode:<br>" + selectedModeNames.sum {
                                def onText = settings["switchOnForMode${it}${i}"] ? "On" : "Vacancy mode"
                                def onlyIfDevice = settings["onlyTurnOnIfDeviceForMode${it}${i}"]
                                def onlyIfText = onlyIfDevice ? " (Only if ${onlyIfDevice} is on)" : ""
                                "  <b>${it}</b>: ${onText}${onlyIfText}<br>"
                            }
                            : ""
                        def switchesToGroupNames = settings["switchesToGroupNames${i}"]
                        def groupSwitchText = switchesToGroupNames ?
                            "Control ${switchesToGroupNames} via group switch ${settings["groupSwitch${i}"]}"
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
            input "dimmers${i}", "capability.switchLevel",
                title: "Dimmers",
                multiple: true, submitOnChange: true
            if (settings["dimmers${i}"]) {
                params.selectedModeNames.each {
                    input "dimmerLevelForMode${it}${i}", "number",
                        title: "Level for <b>${it}</b>",
                        description: "1..100 or empty for vacancy mode",
                        range: "1..100",
                        submitOnChange: true
                    if (settings["dimmerLevelForMode${it}${i}"]) {
                        input "onlyIfDeviceOnForMode${it}${i}", "bool",
                            title: "> Only if another device is on",
                            submitOnChange: true,
                            defaultValue: false
                        if (settings["onlyIfDeviceOnForMode${it}${i}"]) {
                            input "onlyIfDeviceForMode${it}${i}", "capability.switch",
                                title: "> Only set level for ${it} if this device is on",
                                required: true
                        }
                    }
                }
            }
        }
        def dimmers = settings["dimmers${i}"]
        if (dimmers?.size() > 1) {
            section("Control dimmers via group dimmer") {
                input "dimmersToGroupNames${i}", "enum",
                    title: "Dimmers",
                    description: "Click to set dimmers to control via group dimmer",
                    options: dimmers.collect { "${it}" },
                    multiple: true, submitOnChange: true
                if (settings["dimmersToGroupNames${i}"]) {
                    input "groupDimmer${i}", "capability.switchLevel",
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
            input "switches${i}", "capability.switch",
                title: "Switches",
                multiple: true, submitOnChange: true
            if (settings["switches${i}"]) {
                params.selectedModeNames.each {
                    input "switchOnForMode${it}${i}", "bool",
                        title: "Turn on during <b>${it}</b>",
                        submitOnChange: true
                    if (settings["switchOnForMode${it}${i}"]) {
                        input "onlyTurnOnIfDeviceOnForMode${it}${i}", "bool",
                            title: "> Only if another device is on",
                            submitOnChange: true,
                            defaultValue: false
                        if (settings["onlyTurnOnIfDeviceOnForMode${it}${i}"]) {
                            input "onlyTurnOnIfDeviceForMode${it}${i}", "capability.switch",
                                title: "> Only turn on during ${it} if this device is on",
                                required: true
                        }
                    }
                }
            }
        }
        def switches = settings["switches${i}"]
        if (switches?.size() > 1) {
            section("Control switches via group switch") {
                input "switchesToGroupNames${i}", "enum",
                    title: "Switches",
                    description: "Click to set switches to control via group switch",
                    options: switches.collect { "${it}" },
                    multiple: true, submitOnChange: true
                if (settings["switchesToGroupNames${i}"]) {
                    input "groupSwitch${i}", "capability.switch",
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
        subscribe(disableSwitch, "switch.on", disableSwitchOnHandler)
        subscribe(disableSwitch, "switch.off", disableSwitchOffHandler)
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
    subscribe(motionSensors, "motion.active", motionActiveHandler)
    subscribe(motionSensors, "motion.inactive", motionInactiveHandler)
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
    subscribe(virtualMotionSensor, "switch.off", virtualMotionInactiveHandler)
    if (virtualMotionSensorActiveLevel && virtualMotionSensor.hasCapability("SwitchLevel")) {
        subscribe(virtualMotionSensor, "level", virtualMotionLevelHandler)
        if (isAtLevel(virtualMotionSensor, virtualMotionSensorActiveLevel)) {
            motionActive()
        } else if (!isOn(virtualMotionSensor)) {
            motionInactiveOnAllSensors()
        }
    } else {
        subscribe(virtualMotionSensor, "switch.on", virtualMotionActiveHandler)
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
    List<Map> devicesToTurnOn = getDevicesToTurnOn(currentModeName, dimmerConfigCount, "dimmers",
        "dimmerLevelForMode", "onlyIfDeviceForMode",
        "dimmersToGroupNames", "groupDimmer") +
        getDevicesToTurnOn(currentModeName, switchConfigCount, "switches",
            "switchOnForMode", "onlyTurnOnIfDeviceForMode",
            "switchesToGroupNames", "groupSwitch")
    switch (devicesToTurnOn.size()) {
        case 0:
            return
        case 1:
            def device = devicesToTurnOn[0]
            if (device.value instanceof Number) {
                device.device.setLevel(device.value)
            } else {
                device.device.on()
            }
            break
        default:
            // Must use Java Stream method forEach() instead of Groovy collection method each() for parallel stream to work
            devicesToTurnOn.parallelStream().forEach {
                if (it.value instanceof Number) {
                    it.device.setLevel(it.value)
                } else {
                    it.device.on()
                }
            }
    }
    if (logEnable) log.debug "Turned on: ${devicesToTurnOn}"
}

private List<Map> getDevicesToTurnOn(modeName, deviceConfigCount, String devicesKeyPrefix,
                                     String deviceValueForModeKeyPrefix, String onlyIfDeviceForModeKeyPrefix,
                                     String devicesToGroupNamesKeyPrefix, String groupDeviceKeyPrefix) {
    List<Map> devicesToTurnOn = []
    for (i in 0..<deviceConfigCount) {
        def deviceValue = settings["${deviceValueForModeKeyPrefix}${modeName}${i}"]
        if (deviceValue) {
            def onlyIfDevice = settings["${onlyIfDeviceForModeKeyPrefix}${modeName}${i}"]
            if (onlyIfDevice == null || isOn(onlyIfDevice)) {
                def devicesToGroupNames = settings["${devicesToGroupNamesKeyPrefix}${i}"]
                boolean groupDeviceAdded = false
                for (device in settings["${devicesKeyPrefix}${i}"]) {
                    if (isOn(device)) {
                        if (logEnable) log.debug "Not turning on: already on: ${device}"
                        return []
                    } else if (devicesToGroupNames?.contains(device.displayName)) {
                        if (!groupDeviceAdded) {
                            devicesToTurnOn.add([device: settings["${groupDeviceKeyPrefix}${i}"], value: deviceValue])
                            groupDeviceAdded = true
                        }
                    } else {
                        devicesToTurnOn.add([device: device, value: deviceValue])
                    }
                }
            } else {
                if (logEnable) log.debug "Not turning on ${settings["${devicesKeyPrefix}${i}"]}: device is not on: ${onlyIfDevice}"
            }
        } else {
            if (logEnable) log.debug "Not turning on ${settings["${devicesKeyPrefix}${i}"]}: vacancy mode"
        }
    }
    return devicesToTurnOn
}

private void motionInactiveOnAllSensors() {
    List devicesToTurnOff = getDevicesToTurnOff(dimmerConfigCount, "dimmers",
        "dimmersToGroupNames", "groupDimmer") +
        getDevicesToTurnOff(switchConfigCount, "switches",
            "switchesToGroupNames", "groupSwitch")
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

private List getDevicesToTurnOff(deviceConfigCount, String devicesKeyPrefix, String devicesToGroupNamesKeyPrefix,
                                 String groupDeviceKeyPrefix) {
    List devicesToTurnOff = []
    for (i in 0..<deviceConfigCount) {
        def devicesToGroupNames = settings["${devicesToGroupNamesKeyPrefix}${i}"]
        boolean groupDeviceAdded = false
        for (device in settings["${devicesKeyPrefix}${i}"]) {
            if (isOn(device)) {
                if (devicesToGroupNames?.contains(device.displayName)) {
                    if (!groupDeviceAdded) {
                        devicesToTurnOff.add(settings["${groupDeviceKeyPrefix}${i}"])
                        groupDeviceAdded = true
                    }
                } else {
                    devicesToTurnOff.add(device)
                }
            }
        }
    }
    return devicesToTurnOff
}

private static boolean isOn(device) { device.currentValue("switch") == "on" }

private static boolean isAtLevel(device, level) { device.currentValue("level") == level }

private static boolean isMotionActive(device) { device.currentValue("motion") == "active" }
