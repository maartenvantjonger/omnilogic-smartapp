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
  page(name: 'devicesPage', title: 'Omnilogic devices')
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

  updateDevices()
  unsubscribe()
  initialize()
}

def initialize() {
  logDebug('Executing initialize')

  runEvery15Minutes(updateDeviceStatuses)
}

def logDebug(message) {
  if (!enableLogging || message == null) {
    return
  }

  log.debug(groovy.xml.XmlUtil.escapeXml(message))
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
      href 'devicesPage', title: 'Devices', description: 'Choose pool equipment devices'
      href 'telemetryPage', title: 'Telemetry', description: 'View system status'
    }

    section('Logging') {
      input name: 'enableLogging', type: 'bool', title: 'Enable debug logging', defaultValue: true
    }
  }
}

def loginPage() {
  return dynamicPage(name: 'loginPage', nextPage: 'loginResultPage') {
    section {
      input('username', 'email', title: 'Username', description: '')
      input('password', 'password', title: 'Password', description: '')
      input('mspId', 'text', title: 'MSP ID', description: '')
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

def devicesPage() {
  // Get currently installed child devices
  settings.devicesToUse = childDevices.collect { it.deviceNetworkId }

  // Get available devices from Omnilogic
  getAvailableDevices()
  def availableDeviceNames = state.availableDevices.collectEntries { [it.key, it.value.name] }

  // TODO Add/remove devices on next page?
  return dynamicPage(name: 'devicesPage') {
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
    response.MSPConfig.Backyard.Sensor.each {
      addAvailableBackyardDevice(availableDevices, it.parent(), 'Air Temperature Sensor', 'Omnilogic Temperature Sensor')
    }

    // TODO Add relays/lights
    def bowNodes = response.MSPConfig.Backyard.'Body-of-water'
    bowNodes.each { addAvailableBackyardDevice(availableDevices, it, 'Temperature Sensor', 'Omnilogic Temperature Sensor') }
    bowNodes.Filter.each { addAvailableBowDevice(availableDevices, it, null, 'Omnilogic Filter') }
    bowNodes.Pump.each { addAvailableBowDevice(availableDevices, it, null, 'Omnilogic Pump') }
    bowNodes.Chlorinator.each { addAvailableBowDevice(availableDevices, it, null, 'Omnilogic Chlorinator') }
    bowNodes.Heater.each { addAvailableBowDevice(availableDevices, it, 'Heater', 'Omnilogic Heater') }

    state.availableDevices = availableDevices
  }
}

def addAvailableBackyardDevice(availableDevices, deviceXmlNode, name, driverName) {
  def omnilogicId = deviceXmlNode.'System-Id'.text()
  if (omnilogicId == '0') {
    omnilogicId = settings.mspId
  }

  def deviceId = getDeviceId(omnilogicId)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    bowId: omnilogicId,
    name: "Omnilogic ${deviceXmlNode.Name.text()} ${name}",
    driverName: driverName
  ]
}

def addAvailableBowDevice(availableDevices, deviceXmlNode, name, driverName) {
  def omnilogicId = deviceXmlNode.'System-Id'.text()
  def deviceId = getDeviceId(omnilogicId)

  availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    bowId: deviceXmlNode.parent().'System-Id'.text(),
    name: "Omnilogic ${deviceXmlNode.parent().Name.text()} ${name ?: deviceXmlNode.Name.text()}",
    driverName: driverName
  ]
}

def getDeviceId(omnilogicId) {
  return "omnilogic-${omnilogicId}"
}

def createDevice(omnilogicId, bowId, name, driverName) {
  logDebug("Executing createDevice for ${name}")

  def deviceId = getDeviceId(omnilogicId)
  def childDevice = getChildDevice(deviceId)

  if (childDevice == null) {
    def attributes = [name: name, completedSetup: true]
    childDevice = addChildDevice('maartenvantjonger', driverName, deviceId, null, attributes)
    childDevice.initialize(omnilogicId, bowId)
  }

  return childDevice
}

def updateDevices() {
  logDebug('Executing updateDevices')

  if (settings.devicesToUse == null) {
    return
  }

  // Delete devices that were unselected
  deleteDevicesExcept(settings.devicesToUse)

  // Create devices that were selected
  settings.devicesToUse?.findAll { getChildDevice(it) == null }
    .each { deviceId ->
      def device = state.availableDevices[deviceId]
      if (device != null) {
        createDevice(device.omnilogicId, device.bowId, device.name, device.driverName)
      }
    }
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
