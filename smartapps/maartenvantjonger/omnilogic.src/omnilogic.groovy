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
  log.debug('Executing installed')
  initialize()
}

def uninstalled() {
  log.debug('Executing uninstalled')
  deleteDevicesExcept(null)
}

def updated() {
  logDebug('Executing updated')

  unsubscribe()
  initialize()
}

def initialize() {
  logDebug('Executing initialize')

  // TODO Decide to keep or not
  // runEvery15Minutes(updateDeviceStatuses)
}

def logDebug(message) {
  if (!enableLogging || message == null) {
    return
  }

  if (getPlatform() == 'Hubitat') {
    message = groovy.xml.XmlUtil.escapeXml(message)
  }

  log.debug(message)
}

def logDebug(groovy.util.slurpersupport.GPathResult xmlNode) {
  def message = groovy.xml.XmlUtil.serialize(xmlNode)
  logDebug(message)
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
      input name: 'enableLogging', type: 'bool', title: 'Enable debug logging', defaultValue: true
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
  def availableDeviceNames = state.availableDevices.collectEntries { [it.key, it.value.name] }

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
      paragraph updated ? 'Updated devices' : 'Failed to create devices. Make sure all Device Handlers are installed'
    }
  }
}

def telemetryPage() {
  updateDeviceStatuses()

  return dynamicPage(name: 'telemetryPage') {
    section {
      paragraph state.telemetryData ?: 'No data'
    }
  }
}

def getTelemetryData(callback) {
  def parameters = [
    [name: 'Version', value: 0]
  ]

  performApiRequest('GetTelemetryData', parameters) { response ->
    if (response == null) {
      return
    }

    def serializedTelemetryData = groovy.xml.XmlUtil.serialize(response)
    state.telemetryData = groovy.xml.XmlUtil.escapeXml(serializedTelemetryData)

    if (callback != null) {
      callback(response)
    }
  }
}

def getAvailableDevices() {
  def parameters = [
    [name: 'Version', value: 0]
  ]

  performApiRequest('GetMspConfigFile', parameters) { response ->
    if (response == null) {
      return
    }

    def availableDevices = [:]

    def serializedResponse = groovy.xml.XmlUtil.serialize(response.MSPConfig)
    state.mspConfigFile = groovy.xml.XmlUtil.escapeXml(serializedResponse)


    // Parse available devices from MSP Config
    response.MSPConfig.Backyard.each {
      addAvailableTemperatureSensor(availableDevices, it)
    }

    // TODO Add relays/lights
    def bowNodes = response.MSPConfig.Backyard.'Body-of-water'
    bowNodes.each { addTemperatureSensor(availableDevices, it) }
    bowNodes.Filter.each { addPump(availableDevices, it) }
    bowNodes.Pump.each { addPump(availableDevices, it) }
    bowNodes.Chlorinator.each { addDevice(availableDevices, it, null, 'Omnilogic Chlorinator') }
    bowNodes.Heater.each { addDevice(availableDevices, it, 'Heater', 'Omnilogic Heater') }

    state.availableDevices = availableDevices
  }
}

def addTemperatureSensor(availableDevices, deviceXmlNode, name, driverName) {
  def omnilogicId = deviceXmlNode.'System-Id'.text()
  def bowId = omnilogicId

  // For Backyard Air Temperature Sensor
  if (omnilogicId == '0') {
    omnilogicId = settings.mspId
    bowId = null
  }

  def deviceId = getDeviceId(omnilogicId)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    name: deviceXmlNode.Name.text(),
    driverName: 'Omnilogic Temperature Sensor',
    attributes: [
      bowId: bowId,
      sensorType: deviceXmlNode.Sensor.Type.text(),
      unit: deviceXmlNode.Sensor.Units.text()
    ]
  ]
}

def addPump(availableDevices, deviceXmlNode) {
  def type = deviceXmlNode.'Filter-Type'.text() ?: deviceXmlNode.'Type'.text()
  def isVsp = type == 'FMT_VARIABLE_SPEED_PUMP' || type == 'PMP_VARIABLE_SPEED_PUMP'

  addAvailableDevice(availableDevices, deviceXmlNode, null, isVsp ? 'Omnilogic Variable Speed Pump' : 'Omnilogic Pump')
}

def addDevice(availableDevices, deviceXmlNode, name, driverName) {
  def omnilogicId = deviceXmlNode.'System-Id'.text()
  def deviceId = getDeviceId(omnilogicId)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    name: "${deviceXmlNode.parent().Name.text()} ${name ?: deviceXmlNode.Name.text()}",
    driverName: driverName,
    attributes: [
      bowId: deviceXmlNode.parent().'System-Id'.text()
    ]
  ]
}

def getDeviceId(omnilogicId) {
  return "omnilogic-${omnilogicId}"
}

def createDevice(omnilogicId, name, driverName, attributes) {
  logDebug("Executing createDevice for ${name}")

  def deviceId = getDeviceId(omnilogicId)
  def childDevice = getChildDevice(deviceId)

  if (childDevice == null) {
    childDevice = addChildDevice('maartenvantjonger', driverName, deviceId, null, [name: name, completedSetup: true])
    childDevice.initialize(omnilogicId, attributes)
  }

  return childDevice
}

def updateDevices() {
  logDebug('Executing updateDevices')

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
      logDebug("Error updating devices: ${e}")
      return false
    }
  }

  return true
}

def updateDeviceStatuses() {
  logDebug('Executing updateDeviceStatuses')

  getTelemetryData { telemetryData ->
    telemetryData.children().each { deviceStatus ->
      def deviceId = getDeviceId(deviceStatus.@systemId?.text())
      def childDevice = getChildDevice(deviceId)
      if (childDevice != null) {
        childDevice.parseStatus(deviceStatus)
      }
    }
  }
}

def deleteDevicesExcept(deviceIds) {
  logDebug("Executing deleteDevicesExcept for ${deviceIds}")

  childDevices
    .findAll { deviceIds == null || !deviceIds.contains(it.deviceNetworkId) }
    .each {
      try {
        deleteChildDevice(it.deviceNetworkId)
        logDebug("Deleted device ${it.deviceNetworkId}")
      } catch (e) {
        logDebug("Error deleting device ${it.deviceNetworkId}: ${e}")
      }
    }
}

def login(force, callback) {
  if (!force && state.session?.expiration > now()) {
    logDebug('Current token is still valid')
    return callback(true)
  }

  logDebug('Performing login')

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
      logDebug('Login failed')
      return callback(false)
    }

    logDebug('Login succeeded')

    state.session.token = responseParameters.find { it.@name == 'Token' }.text()
    state.session.userId = responseParameters.find { it.@name == 'UserID' }.text()
    state.session.expiration = now() + 24 * 60 * 60 * 1000 // 24 hours
    return callback(true)
  }
}

def performApiRequest(name, parameters, callback) {
  // Perform login sequence for API requests other than Login itself,
  // to make sure we have a valid token
  if (name != 'Login') {
    login(false) { success ->
      if (!success) {
        return
      }
    }

    parameters.add(0, [name: 'MspSystemID', dataType: 'int', value: settings.mspId])
  }

  // Perform API request
  def requestXml = formatApiRequest(name, parameters)

  logDebug('Omnilogic request:')
  logDebug(requestXml)

  httpPost([
    uri: 'https://www.haywardomnilogic.com/HAAPI/HomeAutomation/API.ashx',
    contentType: 'text/xml',
    headers: ['Token': state.session.token],
    body: requestXml
  ]) { response ->
    logDebug("Omnilogic response: ${response.status}")
    logDebug(response.data)

    if (response.status == 200 && response.data) {
      return callback(response.data)
    }

    return callback(null)
  }
}

def formatApiRequest(name, parameters) {
  def parameterXml = parameters?.collect {
    "<Parameter name=\"${it.name}\" dataType=\"${it.dataType ?: 'string'}\">${it.value}</Parameter>\n"
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
