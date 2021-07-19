/**
 *  Omnilogic Smartapp
 *
 *  Copyright 2021 Maarten van Tjonger
 */
definition(
  name: 'Omnilogic',
  namespace: 'maartenvantjonger',
  author: 'Maarten van Tjonger',
  description: 'Integrate Omnilogic pool equipment',
  category: 'Convenience',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)

preferences {
  page(name: 'mainPage', title: 'Omnilogic settings', install: true, uninstall: true)
  page(name: 'loginPage', title: 'Omnilogic account')
  page(name: 'loginResultPage', title: 'Omnilogic account')
  page(name: 'devicePage', title: 'Omnilogic devices')
  page(name: 'deviceResultPage', title: 'Omnilogic devices')
  page(name: 'telemetryPage', title: 'Omnilogic telemetry')
}

def installed() {
  logMethod('installed')
  initialize()
}

def uninstalled() {
  logMethod('uninstalled')
  deleteDevicesExcept(null)
}

def updated() {
  logMethod('updated')
  unsubscribe()
  initialize()
}

def initialize() {
  logMethod('initialize')
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
    }.join(', ')

    logMessage += " | ${argumentsString}"
  }

  logDebug(logMessage)
}

def logDebug(message) {
  if (!enableLogging || message == null) {
    return
  }

  // Escape XML on hubitat to ensure correct rendering in UI
  if (getPlatform() == 'Hubitat') {
    message = groovy.xml.XmlUtil.escapeXml(message)
  }

  log.debug(message)
}

def mainPage() {
  if (settings.username == null) {
    return loginPage()
  }

  dynamicPage(name: 'mainPage') {
    section {
      href 'loginPage', title: 'Account', description: 'Change account settings'
      href 'devicePage', title: 'Devices', description: 'Choose pool equipment devices'
      href 'telemetryPage', title: 'Telemetry', description: 'View system status'
    }

    section('Logging') {
      input name: 'enableLogging', type: 'bool', title: 'Enable debug logging', defaultValue: false
    }
  }
}

def loginPage() {
  return dynamicPage(name: 'loginPage', nextPage: 'loginResultPage') {
    section('Enter your Omnilogic account credentials') {
      input('username', 'email', title: 'Username', description: '')
      input('password', 'password', title: 'Password', description: '')
      input('mspId', 'text', title: 'MSP System ID', description: 'The MSP (Main System Processor) System ID of your Omnilogic pool controller')
    }
  }
}

def loginResultPage() {
  def resultText = 'Login failed. Please try again.'
  def nextPage = 'loginPage'

  login(true) { success ->
    if (success) {
      resultText = 'Login succeeded'
      nextPage = 'mainPage'
    }
  }

  return dynamicPage(name: 'loginResultPage', nextPage: nextPage) {
    section {
      paragraph resultText
    }
  }
}

def devicePage() {
  // Get currently installed child devices
  settings.devicesToUse = childDevices.collect { it.deviceNetworkId }

  // Get available devices from Omnilogic
  getAvailableDevices()
  def availableDeviceNames = state.availableDevices.collectEntries { [it.key, "${it.value.name} (${it.value.driverName})"] }

  return dynamicPage(name: 'devicePage', nextPage: 'deviceResultPage') {
    if (availableDeviceNames?.size() > 0) {
      section {
        input(
          name: 'devicesToUse',
          type: 'enum',
          title: 'Select devices to use',
          required: false,
          multiple: true,
          options: availableDeviceNames
        )
      }
    } else {
      section {
        paragraph 'No devices found'
      }
    }
  }
}

def deviceResultPage() {
  def updated = updateDevices()

  return dynamicPage(name: 'deviceResultPage', nextPage: 'mainPage') {
    section {
      paragraph updated ? 'Updated devices' : 'Failed to create devices. Make sure all Omnilogic Device Handlers are installed'
    }
  }
}

def telemetryPage() {
  updateDeviceStatuses()

  def telemetryData = getPlatform() == 'Hubitat'
      ? groovy.xml.XmlUtil.escapeXml(state.telemetryData)
      : state.telemetryData

  return dynamicPage(name: 'telemetryPage') {
    section {
      paragraph telemetryData ?: 'No data'
    }
  }
}

def getTelemetryData(callback) {
  logMethod('getTelemetryData')

  // Cache telemetry data for 10 seconds
  if (state.telemetryTimestamp != null && state.telemetryTimestamp + 10000 > now()) {
    logMethod('getTelemetryData', 'Returning cached telemetry data', [state.telemetryTimestamp, state.telemetryData])

    def telemetryData = new XmlSlurper().parseText(state.telemetryData)
    callback(telemetryData)
    return
  }

  performApiRequest('RequestTelemetryData', null) { response ->
    if (response == null) {
      return
    }

    state.telemetryTimestamp = now()
    state.telemetryData = groovy.xml.XmlUtil.serialize(response)

    logMethod('getTelemetryData', 'Returning telemetry data', [state.telemetryTimestamp, state.telemetryData])

    if (callback != null) {
      callback(response)
    }
  }
}

def getAvailableDevices() {
  logMethod('getAvailableDevices')

  performApiRequest('RequestConfiguration', null) { response ->
    if (response == null) {
      return
    }

    def availableDevices = [:]

    def serializedResponse = groovy.xml.XmlUtil.serialize(response)
    state.mspConfig = getPlatform() == 'Hubitat'
      ? groovy.xml.XmlUtil.escapeXml(serializedResponse)
      : serializedResponse

    // Parse available devices from MSP Config
    response.Backyard.each { addTemperatureSensor(availableDevices, it) }

    def bowNodes = response.Backyard.'Body-of-water'
    bowNodes.each { addTemperatureSensor(availableDevices, it) }
    bowNodes.Filter.each { addFilter(availableDevices, it) }
    bowNodes.Pump.each { addPump(availableDevices, it) }
    bowNodes.Heater.each { addHeater(availableDevices, it) }
    bowNodes.Chlorinator.each { addChlorinator(availableDevices, it) }
    bowNodes.Relay.each { addDevice(availableDevices, it, null, 'Omnilogic Relay') }
    bowNodes.'ColorLogic-Light'.each { addDevice(availableDevices, it, null, 'Omnilogic Light') }

    state.availableDevices = availableDevices

    logMethod('getAvailableDevices', 'Available devices', [availableDevices])
  }
}

def addTemperatureSensor(availableDevices, deviceDefinition) {
  def omnilogicId = deviceDefinition.'System-Id'.text()
  def bowId = omnilogicId

  // Use MSP ID for Backyard Air Temperature Sensor so we can update it using telemetry data
  if (omnilogicId == '0') {
    omnilogicId = settings.mspId
    bowId = null
  }

  def deviceId = getDeviceId(omnilogicId, null)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    name: deviceDefinition.Name.text(),
    driverName: 'Omnilogic Temperature Sensor',
    attributes: [
      bowId: bowId,
      sensorType: deviceDefinition.Sensor.Type.text(),
      temperatureUnit: deviceDefinition.Sensor.Units.text()
    ]
  ]
}

def addFilter(availableDevices, deviceDefinition) {
  def driverName = deviceDefinition.'Filter-Type'.text() == 'FMT_VARIABLE_SPEED_PUMP' ? 'Omnilogic VSP' : 'Omnilogic Pump'
  addDevice(availableDevices, deviceDefinition, 'Filter', driverName, null)

  if (deviceDefinition.parent().'Supports-Spillover' == 'yes') {
    addDevice(availableDevices, deviceDefinition, 'Spillover', driverName, [isSpillover: 1, deviceIdSuffix: 's'])
  }
}

def addPump(availableDevices, deviceDefinition) {
  def driverName = deviceDefinition.'Type'.text() == 'PMP_VARIABLE_SPEED_PUMP' ? 'Omnilogic VSP' : 'Omnilogic Pump'
  addDevice(availableDevices, deviceDefinition, null, driverName, null)
}

def addHeater(availableDevices, deviceDefinition) {
  def attributes = [
    omnilogicHeaterId: deviceDefinition.Operation.find { it.name() == 'Heater-Equipment' }.'System-Id'.text(),
    minTemperature: deviceDefinition.'Min-Settable-Water-Temp'.text().toInteger(),
    maxTemperature: deviceDefinition.'Max-Settable-Water-Temp'.text().toInteger(),
    temperatureUnit: deviceDefinition.parent().Sensor?.Units?.text()
  ]

  addDevice(availableDevices, deviceDefinition, 'Heater', 'Omnilogic Heater', attributes)
}

def addChlorinator(availableDevices, deviceDefinition) {
  addDevice(availableDevices, deviceDefinition, 'Chlorinator', 'Omnilogic Chlorinator', null)
  addDevice(availableDevices, deviceDefinition, 'Super Chlorinator', 'Omnilogic Chlorinator', [isSuperChlorinator: 1, deviceIdSuffix: 's'])
}

def addDevice(availableDevices, deviceDefinition, name, driverName, attributes) {
  def omnilogicId = deviceDefinition.'System-Id'.text()
  def bowDefinition = deviceDefinition.parent()

  attributes = attributes ?: [:]
  attributes.bowId = bowDefinition.'System-Id'.text()

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
  deviceIdSuffix = deviceIdSuffix ?: ''
  return "omnilogic-${omnilogicId}${deviceIdSuffix}"
}

def createDevice(omnilogicId, name, driverName, attributes) {
  logMethod('createDevice', 'Attributes', [omnilogicId, name, driverName, attributes])

  def deviceId = getDeviceId(omnilogicId, attributes?.deviceIdSuffix)
  def childDevice = getChildDevice(deviceId)

  if (childDevice == null) {
    childDevice = addChildDevice('maartenvantjonger', driverName, deviceId, null, [name: name, completedSetup: true])
    childDevice.initialize(omnilogicId, attributes)
  }

  return childDevice
}

def updateDevices() {
  logMethod('updateDevices')

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
      logMethod('updateDevices', 'Error updating devices', [e])
      return false
    }
  }

  return true
}

def updateDeviceStatuses() {
  logMethod('updateDeviceStatuses')

  getTelemetryData { telemetryData ->
    childDevices.each { device ->
      def omnilogicId = device.currentValue('omnilogicId').toInteger()
      def deviceStatus = telemetryData.children().find { it.@systemId?.text().toInteger() == omnilogicId }
      device.parseStatus(deviceStatus, telemetryData)
    }
  }
}

def deleteDevicesExcept(deviceIds) {
  logMethod('deleteDevicesExcept', 'Attributes', [deviceIds])

  childDevices
    .findAll { deviceIds == null || !deviceIds.contains(it.deviceNetworkId) }
    .each {
      try {
        deleteChildDevice(it.deviceNetworkId)
        logMethod('deleteDevicesExcept', 'Deleted device', [it.deviceNetworkId])
      } catch (e) {
        logMethod('deleteDevicesExcept', 'Error deleting device', [it.deviceNetworkId, e])
      }
    }
}

def login(force, callback) {
  logMethod('login', 'Arguments', [force])

  if (!force && state.session?.expiration > now()) {
    logMethod('login', 'Current token is still valid')
    return callback(true)
  }

  state.session = [
    token: null,
    userId: null,
    expiration: 0
  ]

  def parameters = [
    [name: 'UserName', value: settings.username],
    [name: 'Password', value: settings.password]
  ]

  performApiRequest('Login', parameters) { response ->
    def responseParameters = response?.Parameters?.Parameter
    if (responseParameters?.find { it.@name == 'Status' }.text() != '0') {
      logMethod('login', 'Failed')
      return callback(false)
    }

    logMethod('login', 'Succeeded')

    state.session.token = responseParameters.find { it.@name == 'Token' }.text()
    state.session.userId = responseParameters.find { it.@name == 'UserID' }.text()
    state.session.expiration = now() + 24 * 60 * 60 * 1000 // 24 hours
    return callback(true)
  }
}

def performApiRequest(name, parameters, callback) {
  logMethod('performApiRequest', 'Arguments', [name, parameters])

  parameters = parameters ?: []

  // Perform login sequence for API requests other than Login itself,
  // to make sure we have a valid token
  if (name != 'Login') {
    login(false) { success ->
      if (!success) {
        return
      }
    }

    parameters.add(0, [name: 'Token', value: state.session.token])
    parameters.add(1, [name: 'MspSystemID', dataType: 'int', value: settings.mspId])
  }

  // Perform API request
  def requestXml = formatApiRequest(name, parameters)
  logMethod('performApiRequest', 'Request', [requestXml])

  httpPost([
    uri: 'https://www.haywardomnilogic.com/MobileInterface/MobileInterface.ashx',
    contentType: 'text/xml',
    body: requestXml,
    headers: ['Token': state.session.token],
  ]) { response ->
    logMethod('performApiRequest', 'Response', [response.status, response.data])

    if (response.status == 200 && response.data) {
      return callback(response.data)
    }

    return callback(null)
  }
}

def formatApiRequest(name, parameters) {
  def parameterXml = parameters?.collect {
    "<Parameter name=\"${it.name}\" dataType=\"${it.dataType ?: 'String'}\">${it.value}</Parameter>\n"
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
  physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat'
}
