/**
 *  Omnilogic Smartapp
 *
 *  Copyright 2020 Maarten van Tjonger
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

  // TODO Remove getTelemetryData call
  runEvery15Minutes(getTelemetryData)
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
      href 'loginPage', title: '', description: 'Account settings'
    }

    section {
      href 'devicesPage', title: '', description: 'Devices'
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
  // TODO Remove getTelemetryData call
  getTelemetryData()

  // TODO call GetMspConfigFile to get devices
  //getMspConfigFile()

  settings.devicesToUse = childDevices.collect { it.deviceNetworkId }
  def availableDevices = state.availableDevices.collectEntries { [it.key, it.value.name] }

  return dynamicPage(name: 'devicesPage') {
    if (availableDevices?.size() > 0) {
      section {
        input(name: 'devicesToUse', type: 'enum', title: 'Select devices to use', required: false, multiple: true, options: availableDevices)
      }
    } else {
      section {
        paragraph 'No devices found'
      }
    }

    section('Telemetry') {
      paragraph state.telemetryData ?: 'No data'
    }

    section('MSP Config') {
      paragraph state.mspConfigFile ?: 'No data'
    }
  }
}

def getTelemetryData() {
  def parameters = [
    [name: 'Version', value: 0]
  ]

  performApiRequest('GetTelemetryData', parameters) { response ->
    if (response == null) {
      return
    }

    def serializedResponse = groovy.xml.XmlUtil.serialize(response)
    state.telemetryData = groovy.xml.XmlUtil.escapeXml(serializedResponse)

    state.availableDevices = [:]

    response.children().each { deviceStatus ->
      // Add to available devices list
      def omnilogicId = deviceStatus.@systemId?.text()
      addAvailableDevice(omnilogicId, deviceStatus.name())

      // Update device status
      def childDevice = getChildDevice("omnilogic-${omnilogicId}")
      if (childDevice != null) {
        childDevice.parse(deviceStatus)
      }
    }
  }
}

def getMspConfigFile() {
  def parameters = [
    [name: 'Version', value: 0]
  ]

  performApiRequest('GetMspConfigFile', parameters) { response ->
    if (response == null) {
      return
    }

    def serializedResponse = groovy.xml.XmlUtil.serialize(response.Response)
    state.mspConfigFile = groovy.xml.XmlUtil.escapeXml(serializedResponse)
  }
}

def addAvailableDevice(omnilogicId, name) {
  switch (name) {
    case 'Backyard':
      addDevice(omnilogicId, "Omnilogic Air Temperature ${omnilogicId}", 'Omnilogic Temperature Sensor')
      break
    case 'BodyOfWater':
      addDevice(omnilogicId, "Omnilogic Water Temperature ${omnilogicId}", 'Omnilogic Temperature Sensor')
      break
    case 'Pump':
      addDevice(omnilogicId, "Omnilogic Pump ${omnilogicId}", 'Omnilogic Pump')
      break
    case 'Heater':
      addDevice(omnilogicId, "Omnilogic Heater ${omnilogicId}", 'Omnilogic Heater')
      break
    case 'VirtualHeater':
      addDevice(omnilogicId, "Omnilogic Virtual Heater ${omnilogicId}", 'Omnilogic Heater')
      break
    case 'Chlorinator':
      addDevice(omnilogicId, "Omnilogic Chlorinator ${omnilogicId}", 'Omnilogic Chlorinator')
      break
    case 'Filter':
      addDevice(omnilogicId, "Omnilogic Filter ${omnilogicId}", 'Omnilogic Filter')
      break
    default:
      break
  }
}

def addDevice(omnilogicId, name, driverName) {
  def deviceId = "omnilogic-${omnilogicId}"

  state.availableDevices[deviceId] = [
    omnilogicId: omnilogicId,
    name: name,
    driverName: driverName
  ]
}

def createDevice(omnilogicId, name, driverName) {
  logDebug("Executing createDevice for ${name}")

  def deviceId = "omnilogic-${omnilogicId}"
  def childDevice = getChildDevice(deviceId)

  if (childDevice == null) {
    def attributes = [name: name, completedSetup: true]
    childDevice = addChildDevice('maartenvantjonger', driverName, deviceId, null, attributes)
    childDevice.sendEvent(name: 'omnilogicId', value: omnilogicId)
    childDevice.sendEvent(name: 'poolId', value: 1)
    // TODO send Pool ID
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
      def availableDevice = state.availableDevices[deviceId]
      if (availableDevice != null) {
        createDevice(availableDevice.omnilogicId, availableDevice.name, availableDevice.driverName)
      }
    }
}

def deleteDevicesExcept(deviceIds) {
  logDebug("Executing deleteDevicesExcept for ${deviceIds}")

  childDevices
    .findAll { deviceIds == null || !deviceIds.contains(it.deviceNetworkId) }
    .each {
      try {
        deleteChildDevice(it.deviceNetworkId, true)
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
