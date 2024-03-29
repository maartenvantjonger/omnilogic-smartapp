/**
 *  OmniLogic Smartapp
 *
 *  Version: 1.0.1
 *  Copyright 2022 Maarten van Tjonger
 */
definition(
  name: "OmniLogic",
  namespace: "maartenvantjonger",
  author: "Maarten van Tjonger",
  description: "Hayward OmniLogic pool equipment integration",
  category: "Convenience",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
  page(name: "mainPage", title: "OmniLogic settings", install: true, uninstall: true)
  page(name: "loginPage", title: "OmniLogic account")
  page(name: "loginResultPage", title: "OmniLogic account")
  page(name: "devicePage", title: "OmniLogic devices")
  page(name: "deviceResultPage", title: "OmniLogic devices")
  page(name: "telemetryPage", title: "OmniLogic telemetry")
}

def installed() {
  logMethod("installed")
  initialize()
}

def uninstalled() {
  logMethod("uninstalled")
  uninitialize()
  deleteDevicesExcept(null)
}

def updated() {
  logMethod("updated")
  uninitialize()
  initialize()
}

def initialize() {
  logMethod("initialize")

  if (enableTelemetry) {
    runEvery15Minutes(updateDeviceStatuses)
  }
}

def uninitialize() {
  logMethod("uninitialize")
  unsubscribe()
  unschedule()
}

def logMethod(method, message = null, arguments = null) {
  logMethod(app, method, message, arguments)
}

def logMethod(context, method, message, arguments) {
  def logMessage = "${context.getName()}.${method}()"

  if (message) {
    logMessage += " | ${message}"
  }

  if (arguments) {
    def argumentsString = arguments.collect { argument ->
      if (argument instanceof groovy.util.slurpersupport.GPathResult) {
        // Serialize XML arguments
        return groovy.xml.XmlUtil.serialize(argument)
      }
      return argument
    }.join(", ")

    logMessage += " | ${argumentsString}"
  }

  logDebug(logMessage)
}

def logDebug(message) {
  if (!enableLogging || message == null) {
    return
  }

  // Escape XML on hubitat to ensure correct rendering in UI
  if (getPlatform() == "Hubitat") {
    log.debug(groovy.xml.XmlUtil.escapeXml(message))
  } else {
    log.debug(message)
  }
}

def mainPage() {
  if (settings.username == null) {
    return loginPage()
  }

  dynamicPage(name: "mainPage") {
    section {
      href "loginPage", title: "Account", description: "Change account settings"
      href "devicePage", title: "Devices", description: "Choose pool equipment devices"
      href "telemetryPage", title: "Telemetry", description: "View system status"
    }

    section("Settings") {
      input name: "enableLogging", type: "bool", title: "Enable debug logging", defaultValue: false
      input name: "enableTelemetry", type: "bool", title: "Enable periodic telemetry updates", defaultValue: true
    }
  }
}

def loginPage() {
  return dynamicPage(name: "loginPage", nextPage: "loginResultPage") {
    section("Enter your OmniLogic account credentials") {
      input("username", "email", title: "Username", description: "")
      input("password", "password", title: "Password", description: "")
      input("mspId", "text", title: "MSP System ID", description: "The MSP (Main System Processor) System ID of your OmniLogic pool controller")
    }
  }
}

def loginResultPage() {
  def resultText = "Login failed. Please try again."
  def nextPage = "loginPage"

  login(true) { success ->
      if (success) {
      resultText = "Login succeeded"
      nextPage = "mainPage"
      }
  }

  return dynamicPage(name: "loginResultPage", nextPage: nextPage) {
      section {
          paragraph resultText
      }
  }
}

def devicePage() {
  try {
    // Get currently installed child devices
    settings.devicesToUse = childDevices*.deviceNetworkId

    // Get available devices from OmniLogic
    getAvailableDevices()
    def availableDeviceNames = state.availableDevices.collectEntries { [it.key, "${it.value.name} (${it.value.driverName})"] }

    if (availableDeviceNames?.size() > 0) {
      return dynamicPage(name: "devicePage", nextPage: "deviceResultPage") {
        section {
          input(
            name: "devicesToUse",
            type: "enum",
            title: "Select devices to use",
            required: false,
            multiple: true,
            options: availableDeviceNames
          )
        }
      }
    }
  } catch (e) {
    logMethod("devicePage", "Error getting devices", [e])
  }

  return dynamicPage(name: "devicePage") {
    section {
      paragraph "Error getting devices"
    }
  }
}

def deviceResultPage() {
  def updated = updateDevices()

  return dynamicPage(name: "deviceResultPage", nextPage: "mainPage") {
    section {
      paragraph updated ? "Updated devices" : "Failed to create devices. Make sure all OmniLogic Device Handlers are installed"
    }
  }
}

def telemetryPage() {
  return dynamicPage(name: "telemetryPage") {
    try {
      updateDeviceStatuses()

      def telemetryData = getPlatform() == "Hubitat"
          ? groovy.xml.XmlUtil.escapeXml(state.telemetryData)
          : state.telemetryData

      section {
        paragraph telemetryData ?: "No data"
      }
    } catch (e) {
      logMethod("telemetryPage", "Error getting telemetry data", [e])

      section {
        paragraph "Error getting telemetry data"
      }
    }
  }
}

def getTelemetryData(callback) {
  logMethod("getTelemetryData")

  // Cache telemetry data for 10 seconds
  if (state.telemetryTimestamp != null && state.telemetryTimestamp + 10000 > now()) {
    logMethod("getTelemetryData", "Returning cached telemetry data")

    def telemetryData = new XmlSlurper().parseText(state.telemetryData)
    callback(telemetryData)
    return
  }

  performApiRequest("RequestTelemetryData") { response ->
    if (response == null) {
      return
    }

    state.telemetryTimestamp = now()
    state.telemetryData = groovy.xml.XmlUtil.serialize(response)

    logMethod("getTelemetryData", "Returning telemetry data")
    callback(response)
  }
}

def getMspConfig(callback) {
  logMethod("getMspConfig")

  // Cache MSP Config data for 10 minutes
  if (state.mspConfigTimestamp != null && state.mspConfigTimestamp + 600000 > now()) {
    logMethod("getMspConfig", "Returning cached MSP Config data")

    def mspConfig = new XmlSlurper().parseText(state.mspConfig)
    callback(mspConfig)
    return
  }

  performApiRequest("RequestConfiguration") { response ->
    if (response == null) {
      return
    }

    state.mspConfigTimestamp = now()
    state.mspConfig = groovy.xml.XmlUtil.serialize(response)

    logMethod("getMspConfig", "Returning MSP Config data")
    callback(response)
  }
}

def getAvailableDevices() {
  logMethod("getAvailableDevices")

  getMspConfig { mspConfig ->
    if (mspConfig == null) {
      return
    }

    def availableDevices = [:]

    // Parse available devices from MSP Config
    def backyardNodes = mspConfig.Backyard
    backyardNodes.Sensor.each { addTemperatureSensor(availableDevices, it) }
    backyardNodes.Relay.each { addDevice(availableDevices, it, null, "OmniLogic Relay") }

    def bowNodes = backyardNodes."Body-of-water"
    bowNodes.Sensor.each { addTemperatureSensor(availableDevices, it) }
    bowNodes.Filter.each { addFilter(availableDevices, it) }
    bowNodes.Pump.each { addPump(availableDevices, it) }
    bowNodes.Heater.each { addHeater(availableDevices, it) }
    bowNodes.Chlorinator.each { addChlorinator(availableDevices, it) }
    bowNodes.Relay.each { addDevice(availableDevices, it, null, "OmniLogic Relay") }
    bowNodes."ColorLogic-Light".each { addDevice(availableDevices, it, null, "OmniLogic Light") }

    state.availableDevices = availableDevices

    logMethod("getAvailableDevices", "Available devices", [availableDevices])
  }
}

def addTemperatureSensor(availableDevices, deviceDefinition) {
  // Only add water or air temperature sensors
  def sensorType = deviceDefinition.Type.text()
  if (!sensorType.contains("SENSOR_WATER_TEMP") && !sensorType.contains("SENSOR_AIR_TEMP")) {
    return
  }

  def locationDefinition = deviceDefinition.parent()
  def locationId = locationDefinition."System-Id".text()
  def omnilogicId = locationId

  // Use MSP ID for Backyard Air Temperature Sensor so we can update it using telemetry data
  if (omnilogicId == "0") {
    omnilogicId = settings.mspId
  }

  def deviceId = getDeviceId(omnilogicId, null)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    name: locationDefinition.Name.text(),
    driverName: "OmniLogic Temperature Sensor",
    attributes: [
      bowId: locationId,
      sensorType: sensorType,
      temperatureUnit: deviceDefinition.Units.text()
    ]
  ]
}

def addFilter(availableDevices, deviceDefinition) {
  def driverName = deviceDefinition."Filter-Type".text() == "FMT_VARIABLE_SPEED_PUMP" ? "OmniLogic VSP" : "OmniLogic Pump"
  addDevice(availableDevices, deviceDefinition, "Filter", driverName)

  def bow = deviceDefinition.parent()
  if (bow.Type == "BOW_SPA" && bow."Supports-Spillover" == "yes") {
    addDevice(availableDevices, deviceDefinition, "Spillover", driverName, [isSpillover: 1, deviceIdSuffix: "s"])
  }
}

def addPump(availableDevices, deviceDefinition) {
  def driverName = deviceDefinition.Type.text() == "PMP_VARIABLE_SPEED_PUMP" ? "OmniLogic VSP" : "OmniLogic Pump"
  addDevice(availableDevices, deviceDefinition, null, driverName)
}

def addHeater(availableDevices, deviceDefinition) {
  def temperatureSensorDefinition = deviceDefinition.parent().Sensor.find { it.Type.text().contains("SENSOR_WATER_TEMP") }

  def attributes = [
    omnilogicHeaterId: deviceDefinition.Operation.find { it.name() == "Heater-Equipment" }."System-Id".text(),
    minTemperature: deviceDefinition."Min-Settable-Water-Temp".text().toInteger(),
    maxTemperature: deviceDefinition."Max-Settable-Water-Temp".text().toInteger(),
    temperatureUnit: temperatureSensorDefinition.Units.text()
  ]

  addDevice(availableDevices, deviceDefinition, "Heater", "OmniLogic Heater", attributes)
}

def addChlorinator(availableDevices, deviceDefinition) {
  def cellTypes = [
    "CELL_TYPE_T3": 1,
    "CELL_TYPE_T5": 2,
    "CELL_TYPE_T9": 3,
    "CELL_TYPE_T15": 4
  ]

  def cellType = cellTypes[deviceDefinition."Cell-Type".text()] ?: 4

  addDevice(availableDevices, deviceDefinition, "Chlorinator", "OmniLogic Chlorinator", [cellType: cellType])
  addDevice(availableDevices, deviceDefinition, "Super Chlorinator", "OmniLogic Super Chlorinator", [deviceIdSuffix: "s"])
}

def addDevice(availableDevices, deviceDefinition, name, driverName, attributes = [:]) {
  def bowDefinition = deviceDefinition.parent()

  attributes.bowId = bowDefinition."System-Id".text()
  attributes.bowType = bowDefinition.Type.text() == "BOW_SPA" ? 1 : 0

  def omnilogicId = deviceDefinition."System-Id".text()
  def deviceId = getDeviceId(omnilogicId, attributes.deviceIdSuffix)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    name: "${bowDefinition.Name.text()} ${name ?: deviceDefinition.Name.text()}",
    driverName: driverName,
    attributes: attributes
  ]

  if (attributes != null) {
    availableDevices[deviceId].attributes.putAll(attributes)
  }
}

def getDeviceId(omnilogicId, deviceIdSuffix) {
  return "omnilogic-${omnilogicId}${deviceIdSuffix ?: ""}"
}

def createDevice(omnilogicId, name, driverName, attributes) {
  logMethod("createDevice", "Attributes", [omnilogicId, name, driverName, attributes])

  def deviceId = getDeviceId(omnilogicId, attributes?.deviceIdSuffix)
  def childDevice = getChildDevice(deviceId)

  if (childDevice == null) {
    childDevice = addChildDevice("maartenvantjonger", driverName, deviceId, null, [name: name, completedSetup: true])
    childDevice.initialize(omnilogicId, attributes)
  }

  return childDevice
}

def updateDevices() {
  logMethod("updateDevices")

  // Delete devices that were unselected
  deleteDevicesExcept(settings.devicesToUse)

  // Create devices that were selected
  def devicesToCreate = settings.devicesToUse?.findAll { getChildDevice(it) == null && state.availableDevices[it] != null }
  if (devicesToCreate?.size() > 0) {
    try {
      devicesToCreate.each { deviceId ->
        def device = state.availableDevices[deviceId]
        createDevice(device.omnilogicId, device.name, device.driverName, device.attributes)
      }

      updateDeviceStatuses()
    } catch (e) {
      logMethod("updateDevices", "Error updating devices", [e])
      return false
    }
  }

  return true
}

def updateDeviceStatuses() {
  logMethod("updateDeviceStatuses")

  getTelemetryData { telemetryData ->
    childDevices.each { device ->
      def omnilogicId = device.currentValue("omnilogicId").toInteger()
      def deviceStatus = telemetryData.children().find { it.@systemId?.text().toInteger() == omnilogicId }
      device.parseStatus(deviceStatus, telemetryData)
    }
  }
}

def deleteDevicesExcept(deviceIds) {
  logMethod("deleteDevicesExcept", "Attributes", [deviceIds])

  childDevices
    .findAll { deviceIds == null || !deviceIds.contains(it.deviceNetworkId) }
    .each {
      try {
        deleteChildDevice(it.deviceNetworkId)
        logMethod("deleteDevicesExcept", "Deleted device", [it.deviceNetworkId])
      } catch (e) {
        logMethod("deleteDevicesExcept", "Error deleting device", [it.deviceNetworkId, e])
      }
    }
}

def login(force, callback) {
  logMethod("login", "Arguments", [force])

  if (!force && state.session?.expiration > now()) {
    logMethod("login", "Current token is still valid")
    return callback(true)
  }

  state.session = [
    token: null,
    userId: null,
    expiration: 0
  ]

  def parameters = [
    [name: "UserName", value: settings.username],
    [name: "Password", value: settings.password]
  ]

  try {
    performApiRequest("Login", parameters) { response ->
      def responseParameters = response?.Parameters?.Parameter
      if (responseParameters?.find { it.@name == "Status" }.text() != "0") {
        logMethod("login", "Failed")
        return callback(false)
      }

      logMethod("login", "Succeeded")

      state.session.token = responseParameters.find { it.@name == "Token" }.text()
      state.session.userId = responseParameters.find { it.@name == "UserID" }.text()
      state.session.expiration = now() + 12 * 60 * 60 * 1000 // 12 hours
      return callback(true)
    }
  } catch (e) {
    logMethod("login", "Error logging in", [e])
    return callback(false)
  }
}

def performApiRequest(name, parameters = [], callback) {
  logMethod("performApiRequest", "Arguments", [name, parameters])

  // Perform login sequence for API requests other than Login itself,
  // to make sure we have a valid token
  if (name != "Login") {
    login(false) { success ->
      if (!success) {
        return
      }
    }

    parameters.add(0, [name: "Token", value: state.session.token])
    parameters.add(1, [name: "MspSystemID", dataType: "int", value: settings.mspId])
  }

  // Perform API request
  def requestXml = formatApiRequest(name, parameters)
  logMethod("performApiRequest", "Request", [requestXml])

  httpPost([
    uri: "https://www.haywardomnilogic.com/MobileInterface/MobileInterface.ashx",
    contentType: "text/xml",
    body: requestXml
  ]) { response ->
    logMethod("performApiRequest", "Response", [response.status, response.data])

    if (response.status == 200 && response.data) {
      return callback(response.data)
    }

    return callback(null)
  }
}

def formatApiRequest(name, parameters) {
  def parameterXml = parameters?.collect {
    "<Parameter name=\"${it.name}\" dataType=\"${it.dataType ?: "String"}\">${it.value}</Parameter>\n"
  }.join().trim()

  return """
        <?xml version="1.0" encoding="utf-8"?>
        <Request>
            <Name>${name}</Name>
            <Parameters>
                ${parameterXml}
            </Parameters>
        </Request>
        """.trim()
}

def getPlatform() {
  physicalgraph?.device?.HubAction ? "SmartThings" : "Hubitat"
}
