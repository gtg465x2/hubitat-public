definition(
    name: "Occupancy Control Apps",
    namespace: "gtg465x",
    author: "Grayson Carr",
    description: "Control devices based on occupancy",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true)

preferences {
    page(name: "mainPage", install: true, uninstall: true) {
        section {
            app name: "occupancyControlApps",
                appName: "Occupancy Control",
                namespace: "gtg465x",
                title: "Create New Occupancy Control App...",
                multiple: true
        }
    }
}
