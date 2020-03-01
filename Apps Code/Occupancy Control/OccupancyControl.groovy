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
            def selectedModeNames = modeNames.findAll { motionSensorModeNames?.contains(it) || virtualMotionSensorModeNames?.contains(it) }

            int defaultDimmerConfigCount = 1
            input "dimmerConfigCount", "number",
                title: "Number of dimmer configurations",
                submitOnChange: true, required: true,
                defaultValue: defaultDimmerConfigCount
            (0..<(dimmerConfigCount != null ? dimmerConfigCount : defaultDimmerConfigCount)).each { i ->
                def toDimmersPageDescription = "Click to set"
                def dimmers = settings["dimmers${i}"]
                if (dimmers) {
                    def dimmerNamesText = dimmers.sum { "${it}<br>" }
                    def dimmerLevelsText = selectedModeNames ? "<br>Level per mode:<br>" + selectedModeNames.sum {
                        def onlyIfDevice = settings["onlyIfDeviceForMode${it}${i}"]
                        def onlyIfText = onlyIfDevice ? " (Only if ${onlyIfDevice} is on)" : ""
                        "  <b>${it}</b>: ${settings["dimmerLevelForMode${it}${i}"] ?: "Vacancy mode"}${onlyIfText}<br>"
                    } : ""
                    def dimmersToGroupNames = settings["dimmersToGroupNames${i}"]
                    def groupDimmerText = dimmersToGroupNames ? "Control ${dimmersToGroupNames} via group dimmer ${settings["groupDimmer${i}"]}" : ""
                    toDimmersPageDescription = "<font color=\"#1a77c9\">${dimmerNamesText}${dimmerLevelsText}${groupDimmerText}</font>"
                }
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
                def toSwitchesPageDescription = "Click to set"
                def switches = settings["switches${i}"]
                if (switches) {
                    def switchNamesText = switches.sum { "${it}<br>" }
                    def switchActionsText = selectedModeNames ? "<br>Action per mode:<br>" + selectedModeNames.sum {
                        def onlyIfDevice = settings["onlyTurnOnIfDeviceForMode${it}${i}"]
                        def onlyIfText = onlyIfDevice ? " (Only if ${onlyIfDevice} is on)" : ""
                        "  <b>${it}</b>: ${settings["switchOnForMode${it}${i}"] ? "On" : "Vacancy mode"}${onlyIfText}<br>"
                    } : ""
                    def switchesToGroupNames = settings["switchesToGroupNames${i}"]
                    def groupSwitchText = switchesToGroupNames ? "Control ${switchesToGroupNames} via group switch ${settings["groupSwitch${i}"]}" : ""
                    toSwitchesPageDescription = "<font color=\"#1a77c9\">${switchNamesText}${switchActionsText}${groupSwitchText}</font>"
                }
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
    if (!disableSwitch || disableSwitch.currentValue("switch") == "off") enable()
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
    boolean subscribedToMotionSensors = false
    boolean subscribedToVirtualMotionSensor = false
    app.getSubscriptions()?.find {
        if (it.handler == "motionActiveHandler") {
            subscribedToMotionSensors = true
        } else if (it.handler == "virtualMotionLevelHandler" || it.handler == "virtualMotionActiveHandler") {
            subscribedToVirtualMotionSensor = true
        }
    }
    if (enabled) {
        def currentModeName = location.currentMode.name
        if (motionSensorModeNames?.contains(currentModeName)) {
            if (subscribedToMotionSensors) return else subscribeToMotionSensors()
        } else if (virtualMotionSensorModeNames?.contains(currentModeName)) {
            if (subscribedToVirtualMotionSensor) return else subscribeToVirtualMotionSensor()
        }
    }
    if (subscribedToMotionSensors) {
        unsubscribeFromMotionSensors()
    } else if (subscribedToVirtualMotionSensor) {
        unsubscribeFromVirtualMotionSensor()
    }
}

private void subscribeToMotionSensors() {
    subscribe(motionSensors, "motion.active", motionActiveHandler)
    subscribe(motionSensors, "motion.inactive", motionInactiveHandler)
    if (logEnable) log.debug "Subscribed to motion sensor events: ${motionSensors}"
}

private void unsubscribeFromMotionSensors() {
    unsubscribe(motionSensors)
    if (logEnable) log.debug "Unsubscribed from motion sensor events: ${motionSensors}"
}

private void subscribeToVirtualMotionSensor() {
    if (virtualMotionSensorActiveLevel && virtualMotionSensor.hasCapability("SwitchLevel")) {
        subscribe(virtualMotionSensor, "level", virtualMotionLevelHandler)
    } else {
        subscribe(virtualMotionSensor, "switch.on", virtualMotionActiveHandler)
    }
    subscribe(virtualMotionSensor, "switch.off", virtualMotionInactiveHandler)
    if (logEnable) log.debug "Subscribed to virtual motion sensor events: ${virtualMotionSensor}"
}

private void unsubscribeFromVirtualMotionSensor() {
    unsubscribe(virtualMotionSensor)
    if (logEnable) log.debug "Unsubscribed from virtual motion sensor events: ${virtualMotionSensor}"
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

private boolean isMotionActiveExcludingMotionSensorWithId(Long id) {
    for (motionSensor in motionSensors) {
        if (Long.valueOf(motionSensor.id) != id && motionSensor.currentValue("motion") == "active") {
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
    List devicesToTurnOn = []

    def currentModeName = location.currentMode.name
    for (i in 0..<dimmerConfigCount) {
        def dimmerLevel = settings["dimmerLevelForMode${currentModeName}${i}"]
        if (dimmerLevel != null) {
            def onlyIfDevice = settings["onlyIfDeviceForMode${currentModeName}${i}"]
            if (onlyIfDevice == null || onlyIfDevice.currentValue("switch") == "on") {
                def dimmersToGroupNames = settings["dimmersToGroupNames${i}"]
                boolean groupDimmerAdded = false
                for (dimmer in settings["dimmers${i}"]) {
                    if (dimmer.currentValue("switch") == "on") {
                        if (logEnable) log.debug "Not turning on: already on: ${dimmer}"
                        return
                    } else if (dimmersToGroupNames?.contains(dimmer.displayName)) {
                        if (!groupDimmerAdded) {
                            devicesToTurnOn.add([dimmer: settings["groupDimmer${i}"], level: dimmerLevel])
                            groupDimmerAdded = true
                        }
                    } else {
                        devicesToTurnOn.add([dimmer: dimmer, level: dimmerLevel])
                    }
                }
            } else {
                if (logEnable) log.debug "Not turning on ${settings["dimmers${i}"]}: device is not on: ${onlyIfDevice}"
            }
        } else {
            if (logEnable) log.debug "Not turning on ${settings["dimmers${i}"]}: vacancy mode"
        }
    }

    for (i in 0..<switchConfigCount) {
        def switchOn = settings["switchOnForMode${currentModeName}${i}"]
        if (switchOn != null) {
            def onlyIfDevice = settings["onlyTurnOnIfDeviceForMode${currentModeName}${i}"]
            if (onlyIfDevice == null || onlyIfDevice.currentValue("switch") == "on") {
                def switchesToGroupNames = settings["switchesToGroupNames${i}"]
                boolean groupSwitchAdded = false
                for (_switch in settings["switches${i}"]) {
                    if (_switch.currentValue("switch") == "on") {
                        if (logEnable) log.debug "Not turning on: already on: ${_switch}"
                        return
                    } else if (switchesToGroupNames?.contains(_switch.displayName)) {
                        if (!groupSwitchAdded) {
                            devicesToTurnOn.add(settings["groupSwitch${i}"])
                            groupSwitchAdded = true
                        }
                    } else {
                        devicesToTurnOn.add(_switch)
                    }
                }
            } else {
                if (logEnable) log.debug "Not turning on ${settings["switches${i}"]}: device is not on: ${onlyIfDevice}"
            }
        } else {
            if (logEnable) log.debug "Not turning on ${settings["switches${i}"]}: vacancy mode"
        }
    }

    switch (devicesToTurnOn.size()) {
        case 0:
            return
        case 1:
            def device = devicesToTurnOn[0]
            if (device instanceof Map) device.dimmer.setLevel(device.level) else device.on()
            break
        default:
            // Must use Java Stream method forEach() instead of Groovy collection method each() for parallel stream to work
            devicesToTurnOn.parallelStream().forEach { if (it instanceof Map) it.dimmer.setLevel(it.level) else it.on() }
    }
    if (logEnable) log.debug "Turned on: ${devicesToTurnOn}"
}

private void motionInactiveOnAllSensors() {
    List devicesToTurnOff = []

    for (i in 0..<dimmerConfigCount) {
        def dimmersToGroupNames = settings["dimmersToGroupNames${i}"]
        boolean groupDimmerAdded = false
        for (dimmer in settings["dimmers${i}"]) {
            if (dimmer.currentValue("switch") == "on") {
                if (dimmersToGroupNames?.contains(dimmer.displayName)) {
                    if (!groupDimmerAdded) {
                        devicesToTurnOff.add(settings["groupDimmer${i}"])
                        groupDimmerAdded = true
                    }
                } else {
                    devicesToTurnOff.add(dimmer)
                }
            }
        }
    }

    for (i in 0..<switchConfigCount) {
        def switchesToGroupNames = settings["switchesToGroupNames${i}"]
        boolean groupSwitchAdded = false
        for (_switch in settings["switches${i}"]) {
            if (_switch.currentValue("switch") == "on") {
                if (switchesToGroupNames?.contains(_switch.displayName)) {
                    if (!groupSwitchAdded) {
                        devicesToTurnOff.add(settings["groupSwitch${i}"])
                        groupSwitchAdded = true
                    }
                } else {
                    devicesToTurnOff.add(_switch)
                }
            }
        }
    }

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
