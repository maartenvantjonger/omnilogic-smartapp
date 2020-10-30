/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral, EmptyMethod, FactoryMethodName, GetterMethodCouldBeProperty, ImplicitClosureParameter, Instanceof, LineLength, MethodCount, MethodParameterTypeRequired, MethodReturnTypeRequired, MethodSize, NoDef, UnnecessaryGetter, VariableTypeRequired */

definition(
        name: 'Omnilogic',
        namespace: 'mvtjonger',
        author: 'Maarten van Tjonger',
        description: 'Integrate Omnilogic pool equipment',
        category: 'Convenience',
        iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
        iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
        iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)

preferences {
    page(name: 'mainPage', title: 'Omnilogic app settings')
    page(name: 'loginPage', title: 'Omnilogic account')
    page(name: 'loginResultPage', title: 'Omnilogic account')
    page(name: 'devicesPage', title: 'Omnilogic devices')
}

def installed() {
    log.debug('App installed')
}

def updated() {
    logDebug('Changes saved')
    unschedule()

    // TODO: Assign sensorDevices
    if (state.sensorDevices) {
        refreshAll()
        runEvery30Minutes(refreshAll)
    }
}

def uninstall() {
    logDebug('Removing devices')

    childDevices.each {
        try {
            deleteChildDevice(it.deviceNetworkId, true)
            logDebug("Deleted device ${it.deviceNetworkId}")
        }
        catch (e) {
            logDebug("Error deleting device ${it.deviceNetworkId}: ${e}")
        }
    }
}

def uninstalled() {
    log.debug('App uninstalled')
}

def logDebug(message) {
    if (!enableLogging || !message) {
        return
    }

    log.debug(groovy.xml.XmlUtil.escapeXml(message))
}

def logDebug(groovy.util.slurpersupport.GPathResult xmlNode) {
    def message = groovy.xml.XmlUtil.serialize(xmlNode)
    logDebug(message)
}

def mainPage() {
    if (!settings.username) {
        return loginPage()
    }

    requestTelemetryData()

    dynamicPage(name: 'mainPage', install: true) {
        section('Omnilogic Account') {
            paragraph "Username: ${settings.username} ${state.session?.name}"
            href 'loginPage', title: '', description: 'Update account credentials'
        }

        section('Connected Devices') {
            paragraph childDevices.size() ? childDevices.join('\n') : 'No devices connected'
            paragraph state.deviceState ?: ''
            href 'devicesPage', title: '', description: 'See devices'
        }

        section('Debug logging') {
            input name: 'enableLogging', type: 'bool', title: 'Enable debug logging', defaultValue: true
        }
    }
}

def loginPage() {
    return dynamicPage(name: 'loginPage', title: 'Connect to Omnilogic', nextPage: 'loginResultPage', submitOnChange: true) {
        section('Login credentials') {
            input('username', 'email', title: 'Username', description: '')
            input('password', 'password', title: 'Password', description: '')
            input('mspId', 'text', title: 'MSP ID', description: '')
        }
    }
}

def loginResultPage() {
    login { result ->
        def resultText = result ? 'Login succeeded' : 'Login failed'

        return dynamicPage(name: 'loginResultPage', title: resultText) {
            section('') {
                paragraph resultText
            }
        }
    }
}

def devicesPage() {
    login { result ->
        getMyQDevices()

        state.doorList = [:]
        state.lightList = [:]

        state.MyQDataPending.each { id, device ->
            if (device.typeName == 'door') {
                state.doorList[id] = device.name
            } else if (device.typeName == 'light') {
                state.lightList[id] = device.name
            }
        }

        if ((state.doorList) || (state.lightList)) {
            def nextPage = 'sensorPage'

            return dynamicPage(name: 'devicesPage', title: 'Devices', nextPage: nextPage) {
                if (state.doorList) {
                    section('Select which garage door/gate to use') {
                        input(name: 'doors', type: 'enum', required: false, multiple: true, options: state.doorList)
                    }
                }
                if (state.lightList) {
                    section('Select which lights to use') {
                        input(name: 'lights', type: 'enum', required: false, multiple: true, options: state.lightList)
                    }
                }
            }
        }

        return dynamicPage(name: 'devicesPage', title: 'No devices') {
            section('') {
                paragraph 'No supported devices found: ' + state.unsupportedList
            }
        }
    }
}

def initialize() {
    logDebug('Initializing...')
    state.data = state.MyQDataPending
    state.lastSuccessfulStep = ''
    unsubscribe()

    // Check existing installed devices against server data
    verifyChildDeviceIds()

    // Mark sensors onto state door data
    def doorSensorCounter = 1
    state.validatedDoors.each { door ->
        if (settings["door${doorSensorCounter}Sensor"]) {
            state.data[door].sensor = "door${doorSensorCounter}Sensor"
            doorSensorCounter++
        }
    }
    state.lastSuccessfulStep = 'Sensor Indexing'

    // Create door devices
    def doorCounter = 1
    state.validatedDoors.each { door ->
        createChildDevices(door, settings[state.data[door].sensor], state.data[door].name, settings["prefDoor${doorCounter}PushButtons"])
        doorCounter++
    }
    state.lastSuccessfulStep = 'Door device creation'

    // Create light devices
    if (lights) {
        state.validatedLights = []
        if (lights instanceof List && lights.size() > 1) {
            lights.each { lightId ->
                if (state.data[lightId] != null) {
                    state.validatedLights.add(lightId)
                }
            }
        } else {
            state.validatedLights = lights
        }
        state.validatedLights.each { light ->
            if (light) {
                def myQDeviceId = state.data[light].myQDeviceId
                def deviceNetworkId = [app.id, 'LightController', myQDeviceId].join('|')
                def lightName = state.data[light].name
                def childLight = getChildDevice(state.data[light].child)

                if (!childLight) {
                    logDebug('Creating child light device: ' + light)

                    try {
                        childLight = addChildDevice('brbeaird', 'MyQ Light Controller', deviceNetworkId, getHubID(), ['name': lightName])
                        state.data[myQDeviceId].child = deviceNetworkId
                        state.installMsg = state.installMsg + lightName + ': created light device. \r\n\r\n'
                    }
                    catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
                        logDebug('Error! ' + e)
                        state.installMsg = state.installMsg + lightName + ': problem creating light device. Check your IDE to make sure the brbeaird : MyQ Light Controller device handler is installed and published. \r\n\r\n'
                    }
                } else {
                    logDebug('Light device already exists: ' + lightName)
                    state.installMsg = state.installMsg + lightName + ': light device already exists. \r\n\r\n'
                }

                logDebug("Setting ${lightName} status to ${state.data[light].status}")
                childLight.updateDeviceStatus(state.data[light].status)
            }
        }
        state.lastSuccessfulStep = 'Light device creation'
    }

    // Remove unselected devices
    getChildDevices().each { child ->
        logDebug("Checking ${child} for deletion")
        def myQDeviceId = child.getMyQDeviceId()
        if (myQDeviceId) {
            if (!(myQDeviceId in state.validatedDoors) && !(myQDeviceId in state.validatedLights)) {
                try {
                    logDebug("Child ${child} with ID ${myQDeviceId} not found in selected list. Deleting.")
                    deleteChildDevice(child.deviceNetworkId, true)
                    logDebug("Removed old device: ${child}")
                    state.installMsg = state.installMsg + "Removed old device: ${child} \r\n\r\n"
                }
                catch (e) {
                    logDebug("Error trying to delete device: ${child} - ${e}")
                    logDebug('Device is likely in use in a Routine, or SmartApp (make sure and check Alexa, ActionTiles, etc.).')
                }
            }
        }
    }
    state.lastSuccessfulStep = 'Old device removal'

    // Set initial values
    if (state.validatedDoors) {
        syncDoorsWithSensors()
    }
    state.lastSuccessfulStep = 'Setting initial values'

    // Subscribe to sensor events
    settings.each { key, val ->
        if (key.contains('Sensor')) {
            subscribe(val, 'contact', sensorHandler)
        }
    }
}

def verifyChildDeviceIds() {
    // Try to match existing child devices with latest server data
    childDevices.each { child ->
        def matchingId
        if (child.typeName != 'Momentary Button Tile') {
            // Look for a matching entry in MyQ
            state.data.each { myQId, myQData ->
                if (child.getMyQDeviceId() == myQId) {
                    logDebug("Found matching ID for ${child}")
                    matchingId = myQId
                }

                // If no matching ID, try to match on name
                else if (child.name == myQData.name || child.label == myQData.name) {
                    logDebug("Found matching ID (via name) for ${child}")
                    child.updateMyQDeviceId(myQId)    // Update child to new ID
                    matchingId = myQId
                }
            }

            logDebug("final matchingid for ${child.name} ${matchingId}")
            if (matchingId) {
                state.data[matchingId].child = child.deviceNetworkId
            } else {
                logDebug("WARNING: Existing child ${child} does not seem to have a valid MyQID")
            }
        }
    }
}

def createChildDevices(door, sensor, doorName, prefPushButtons) {
    def sensorTypeName = 'MyQ Garage Door Opener'
    def noSensorTypeName = 'MyQ Garage Door Opener-NoSensor'
    def lockTypeName = 'MyQ Lock Door'

    if (door) {
        def myQDeviceId = state.data[door].myQDeviceId
        def deviceNetworkId = [app.id, 'GarageDoorOpener', myQDeviceId].join('|')

        // Has door's child device already been created?
        def existingDev = getChildDevice(state.data[door].child)
        def existingType = existingDev?.typeName

        if (existingDev) {
            logDebug('Child already exists for ' + doorName + '. Sensor name is: ' + sensor)
            state.installMsg = state.installMsg + doorName + ': door device already exists. \r\n\r\n'

            if (prefUseLockType && existingType != lockTypeName) {
                try {
                    logDebug('Type needs updating to Lock version')
                    existingDev.deviceType = lockTypeName
                    state.installMsg = state.installMsg + doorName + ': changed door device to lock version.' + '\r\n\r\n'
                }
                catch (hubitat.exception.NotFoundException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem changing door to no-sensor type. Check your IDE to make sure the brbeaird : ' + lockTypeName + ' device handler is installed and published. \r\n\r\n'
                }
            } else if ((!sensor) && existingType != noSensorTypeName) {
                try {
                    logDebug('Type needs updating to no-sensor version')
                    existingDev.deviceType = noSensorTypeName
                    state.installMsg = state.installMsg + doorName + ': changed door device to No-sensor version.' + '\r\n\r\n'
                }
                catch (hubitat.exception.NotFoundException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem changing door to no-sensor type. Check your IDE to make sure the brbeaird : ' + noSensorTypeName + ' device handler is installed and published. \r\n\r\n'
                }
            } else if (sensor && existingType != sensorTypeName && !prefUseLockType) {
                try {
                    logDebug('Type needs updating to sensor version')
                    existingDev.deviceType = sensorTypeName
                    state.installMsg = state.installMsg + doorName + ': changed door device to sensor version.' + '\r\n\r\n'
                }
                catch (hubitat.exception.NotFoundException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem changing door to sensor type. Check your IDE to make sure the brbeaird : ' + sensorTypeName + ' device handler is installed and published. \r\n\r\n'
                }
            }
        } else {
            logDebug('Creating child door device ' + door)
            def childDoor

            if (prefUseLockType) {
                try {
                    logDebug('Creating door with lock type')
                    childDoor = addChildDevice('brbeaird', lockTypeName, deviceNetworkId, getHubID(), ['name': doorName])
                    childDoor.updateMyQDeviceId(myQDeviceId)
                    state.installMsg = state.installMsg + doorName + ': created lock device \r\n\r\n'
                }
                catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem creating door device (lock type). Check your IDE to make sure the brbeaird : ' + sensorTypeName + ' device handler is installed and published. \r\n\r\n'
                }
            } else if (sensor) {
                try {
                    logDebug('Creating door with sensor')
                    childDoor = addChildDevice('brbeaird', sensorTypeName, deviceNetworkId, getHubID(), ['name': doorName])
                    childDoor.updateMyQDeviceId(myQDeviceId)
                    state.installMsg = state.installMsg + doorName + ': created door device (sensor version) \r\n\r\n'
                }
                catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem creating door device (sensor type). Check your IDE to make sure the brbeaird : ' + sensorTypeName + ' device handler is installed and published. \r\n\r\n'
                }
            } else {
                try {
                    logDebug('Creating door with no sensor')
                    childDoor = addChildDevice('brbeaird', noSensorTypeName, deviceNetworkId, getHubID(), ['name': doorName])
                    childDoor.updateMyQDeviceId(myQDeviceId)
                    state.installMsg = state.installMsg + doorName + ': created door device (no-sensor version) \r\n\r\n'
                }
                catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem creating door device (no-sensor type). Check your IDE to make sure the brbeaird : ' + noSensorTypeName + ' device handler is installed and published. \r\n\r\n'
                }
            }
            state.data[door].child = childDoor.deviceNetworkId
        }

        // Create push button devices
        if (prefPushButtons) {
            def existingOpenButtonDev = getChildDevice(door + ' Opener')
            def existingCloseButtonDev = getChildDevice(door + ' Closer')

            if (!existingOpenButtonDev) {
                try {
                    def openButton = addChildDevice('brbeaird', 'Momentary Button Tile', door + ' Opener', getHubID(), [name: doorName + ' Opener', label: doorName + ' Opener'])
                    state.installMsg = state.installMsg + doorName + ': created push button device. \r\n\r\n'
                    subscribe(openButton, 'momentary.pushed', doorButtonOpenHandler)
                }
                catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
                    logDebug('Error! ' + e)
                    state.installMsg = state.installMsg + doorName + ': problem creating push button device. Check your IDE to make sure the smartthings : Momentary Button Tile device handler is installed and published. \r\n\r\n'
                }
            } else {
                subscribe(existingOpenButtonDev, 'momentary.pushed', doorButtonOpenHandler)
                state.installMsg = state.installMsg + doorName + ': push button device already exists. Subscription recreated. \r\n\r\n'
                logDebug('subscribed to button: ' + existingOpenButtonDev)
            }

            if (!existingCloseButtonDev) {
                try {
                    def closeButton = addChildDevice('brbeaird', 'Momentary Button Tile', door + ' Closer', getHubID(), [name: doorName + ' Closer', label: doorName + ' Closer'])
                    subscribe(closeButton, 'momentary.pushed', doorButtonCloseHandler)
                }
                catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
                    logDebug('Error! ' + e)
                }
            } else {
                subscribe(existingCloseButtonDev, 'momentary.pushed', doorButtonCloseHandler)
            }
        }

        // Cleanup defunct push button devices if no longer wanted
        else {
            def pushButtonIDs = [door + ' Opener', door + ' Closer']
            def devsToDelete = getChildDevices().findAll { pushButtonIDs.contains(it.deviceNetworkId) }
            logDebug('button devices to delete: ' + devsToDelete)
            devsToDelete.each {
                logDebug('deleting button: ' + it)
                try {
                    deleteChildDevice(it.deviceNetworkId, true)
                } catch (e) {
                    state.installMsg = state.installMsg + "Warning: unable to delete virtual on/off push button - you'll need to manually remove it. \r\n\r\n"
                    logDebug('Error trying to delete button ' + it + ' - ' + e)
                    logDebug('Button  is likely in use in a Routine, or SmartApp (make sure and check SmarTiles!).')
                }
            }
        }
    }
}

def refresh(child) {
    def door = child.device.deviceNetworkId
    def doorName = state.data[child.getMyQDeviceId()].name
    child.log('refresh called from ' + doorName + ' (' + door + ')')
    syncDoorsWithSensors(child)
}

def refreshAll() {
    syncDoorsWithSensors()
}

def sensorHandler(evt) {
    if (enableLogging) {
        log.debug 'Sensor change detected: Event name  ' + evt.name + ' value: ' + evt.value + ' deviceID: ' + evt.deviceId
    }

    state.validatedDoors.each { door ->
        if (settings[state.data[door].sensor]?.id?.toInteger() == evt.deviceId) {
            updateDoorStatus(state.data[door].child, settings[state.data[door].sensor], null)
        }
    }
}

def doorButtonOpenHandler(evt) {
    try {
        logDebug('Door open button push detected: Event name  ' + evt.name + ' value: ' + evt.value + ' deviceID: ' + evt.deviceId + ' deviceNetworkId: ' + evt.getDevice().deviceNetworkId)
        def myQDeviceId = evt.getDevice().deviceNetworkId.replace(' Opener', '')
        def doorDevice = getChildDevice(state.data[myQDeviceId].child)
        logDebug('Opening door.')
        doorDevice.openPrep()
        sendCommand(myQDeviceId, 'open')
    } catch (e) {
        log.error("Warning: MyQ Open button command failed - ${e}")
    }
}

def doorButtonCloseHandler(evt) {
    try {
        logDebug('Door close button push detected: Event name  ' + evt.name + ' value: ' + evt.value + ' deviceID: ' + evt.deviceId + ' deviceNetworkId: ' + evt.getDevice().deviceNetworkId)
        def myQDeviceId = evt.getDevice().deviceNetworkId.replace(' Closer', '')
        def doorDevice = getChildDevice(state.data[myQDeviceId].child)
        logDebug('Closing door.')
        doorDevice.closePrep()
        sendCommand(myQDeviceId, 'close')
    } catch (e) {
        log.error("Warning: MyQ Close button command failed - ${e}")
    }
}

def requestTelemetryData() {
    def telemetryDataRequest = formatRequest('RequestTelemetryData', [[ name: 'Token', value: state.session.token], [name: 'MspSystemID', value: settings.mspId ]])
    postRequest(telemetryDataRequest) { response ->
        def deviceText = ''

        response.children().each { device ->
            deviceText += "${device.name()}: "

            switch (device.name()) {
                case 'Backyard':
                    deviceText += "ID: ${device.@systemId?.text()} Air temperature: ${device.@airTemp?.text()} Status: ${device.@status?.text()} State: ${device.@state?.text()}"
                    break
                case 'BodyOfWater':
                    deviceText += "ID: ${device.@systemId?.text()} Water temperature: ${device.@waterTemp?.text()} Flow: ${device.@flow?.text()}"
                    break
                case 'Pump':
                    deviceText += "ID: ${device.@systemId?.text()} State: ${device.@pumpState?.text()} Speed: ${device.@pumpSpeed?.text()} Last speed: ${device.@lastSpeed?.text()}"
                    break
                case 'Heater':
                    deviceText += "ID: ${device.@systemId?.text()} State: ${device.@heaterState?.text()} Temperature: ${device.@temp?.text()} Enable: ${device.@enable?.text()} Priority: ${device.@priority?.text()} Maintain for: ${device.@maintainFor?.text()}"
                    break
                case 'VirtualHeater':
                    deviceText += "ID: ${device.@systemId?.text()} Current setpoint: ${device.'@Current-Set-Point'?.text()} Solar setpoint: ${device.@SolarSetPoint?.text()} Enable: ${device.@enable?.text()}"
                    break
                case 'Chlorinator':
                    deviceText += "ID: ${device.@systemId?.text()} Operating mode: ${device.@operatingMode?.text()} Operating state: ${device.@operatingState?.text()} Percentage: ${device.'@Timed-Percent'?.text()} Mode: ${device.@scMode?.text()} Error: ${device.@chlrError?.text()} Alert: ${device.@chlrAlert?.text()} Average salt: ${device.@avgSaltLevel?.text()} Instant salt: ${device.@instantSaltLevel?.text()} Status: ${device.@status?.text()}"
                    break
                case 'CSAD':
                    deviceText += "ID: ${device.@systemId?.text()} Status: ${device.@status?.text()} Mode: ${device.@mode?.text()} PH: ${device.@ph?.text()} ORP: ${device.@orp?.text()}"
                    break
                case 'Filter':
                    deviceText += "ID: ${device.@systemId?.text()} Valve position: ${device.@valvePosition?.text()} Filter state: ${device.@filterState?.text()} Filter speed: ${device.@filterSpeed?.text()} Last filter speed: ${device.@lastSpeed?.text()} Why filter is on: ${device.@whyFilterIsOn?.text()} FP override: ${device.@fpOverride?.text()}"
                    break
                default:
                    deviceText += "ID: ${device.@systemId?.text()} (Unsupported)"
                    break
            }

            deviceText += '\n'
        }

        state.deviceState = deviceText
    }
}

def login(callback) {
    if ((state.session?.expiration ?: 0) > now()) {
        logDebug('Existing token is still valid')
        callback(true)
    }

    logDebug('Expired or no token. Logging in.')

    state.session = [
        token: null,
        expiration: 0,
        name: ''
    ]

    def loginRequest = formatRequest('Login', [[ name: 'Username', value: settings.username], [name: 'Password', value: settings.password ]])
    postRequest(loginRequest) { response ->
        def parameters = response.Parameters.Parameter
        if (parameters.find { it.@name == 'Status' }.text() == '0') {
            state.session.token = parameters.find { it.@name == 'Token' }.text()
            state.session.name = "${parameters.find { it.@name == 'Firstname' }.text()} ${parameters.find { it.@name == 'Lastname' }.text()}"
            state.session.expiration = now() + 30 * 60000 // 30 minutes
            callback(true)
        }

        callback(false)
    }
}

def postRequest(requestXml, callback) {
    logDebug('Request:')
    logDebug(requestXml)

    httpPost([
        uri: 'https://www.haywardomnilogic.com',
        path: '/MobileInterface/MobileInterface.ashx',
        contentType: 'application/xml',
        body: requestXml
    ]) { response ->
        logDebug("Response: ${response.status}")
        logDebug(response.data)

        if (response.status == 200 && response.data) {
            callback(response.data)
        } else {
            callback(null)
        }
    }
}

def sendCommand() {
}

def formatRequest(name, parameters) {
    def parameterXml = parameters?.collect {
        "<Parameter name=\"${it.name}\" dataType=\"${it.dataType ?: 'string'}\">${it.value}</Parameter>\n"
    }.join().trim()

    def requestXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <Request xmlns="http://nextgen.hayward.com/api">
            <Name>${name}</Name>
            <Paramenters>
                ${parameterXml}
            </Paramenters>
        </Request>
    """.trim()

    logDebug(requestXml)

    return requestXml
}
