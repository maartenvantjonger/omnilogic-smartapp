/**
 *  Omnilogic Chlorinator
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: "Omnilogic Chlorinator",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger"
  ) {
    capability "Switch"
    capability "Switch Level"
    capability "Actuator"
    capability "Refresh"

    attribute "bowId", "number"
    attribute "bowType", "number"
    attribute "cellType", "number"
    attribute "omnilogicId", "number"
    attribute "level", "number"
    attribute "operatingState", "number"
    attribute "operatingMode", "number"
    attribute "status", "number"
    attribute "scMode", "number"
    attribute "instantSaltLevel", "number"
    attribute "avgSaltLevel", "number"
    attribute "chlrAlert", "number"
    attribute "chlrError", "number"
    attribute "isSuperChlorinator", "number"
  }

  tiles {
    standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, decoration: "flat") {
      state("off", label: "${name}", action: "on")
      state("on", label: "${name}", action: "off")
    }

    controlTile("level", "device.level", "slider", range: "(1..100)", height: 2, width: 2, canChangeIcon: true, decoration: "flat", inactiveLabel: false) {
      state "level", action: "setLevel"
    }

    main("switch")
    details(["switch", "level"])
  }
}

def initialize(omnilogicId, attributes) {
  logMethod("initialize", "Arguments", [omnilogicId, attributes])

  sendEvent(name: "omnilogicId", value: omnilogicId, displayed: true)
  sendEvent(name: "bowId", value: attributes["bowId"], displayed: true)
  sendEvent(name: "bowType", value: attributes["bowType"], displayed: true)
  sendEvent(name: "cellType", value: attributes["cellType"], displayed: true)
  sendEvent(name: "isSuperChlorinator", value: attributes["isSuperChlorinator"], displayed: true)

  if (!getIsSuperChlorinator()) {
    sendEvent(name: "level", value: 0, displayed: true)
  }
}

def refresh() {
	logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
	logMethod("parseStatus", "Arguments", [deviceStatus])

  def enabled = getIsSuperChlorinator() ? deviceStatus.@scMode.text() : deviceStatus.@enable.text()
  def onOff = enabled == "1" ? "on" : "off"
  sendEvent(name: "switch", value: onOff, displayed: true)

  if (getIsSuperChlorinator()) {
    def level = enabled == "1" ? 100 : 0
    sendEvent(name: "level", value: level, displayed: true)
  } else {
    def level = deviceStatus["@Timed-Percent"].text()
    sendEvent(name: "level", value: level, displayed: true)
  }

  def operatingState = deviceStatus.@operatingState.text()
  sendEvent(name: "operatingState", value: operatingState, displayed: true)

  def operatingMode = deviceStatus.@operatingMode.text()
  sendEvent(name: "operatingMode", value: operatingMode, displayed: true)

  def status = deviceStatus.@status.text()
  sendEvent(name: "status", value: status, displayed: true)

  def enable = deviceStatus.@enable.text()
  sendEvent(name: "enable", value: enable, displayed: true)

  def scMode = deviceStatus.@scMode.text()
  sendEvent(name: "scMode", value: scMode, displayed: true)

  def instantSaltLevel = deviceStatus.@instantSaltLevel.text()
  sendEvent(name: "instantSaltLevel", value: instantSaltLevel, displayed: true)

  def avgSaltLevel = deviceStatus.@avgSaltLevel.text()
  sendEvent(name: "avgSaltLevel", value: avgSaltLevel, displayed: true)

  def chlrAlert = deviceStatus.@chlrAlert.text()
  sendEvent(name: "chlrAlert", value: chlrAlert, displayed: true)

  def chlrError = deviceStatus.@chlrError.text()
  sendEvent(name: "chlrError", value: chlrError, displayed: true)
}

def on() {
	logMethod("on")

  if (getIsSuperChlorinator()) {
    enableSuperChlorinator(true)
  } else {
    enableChlorinator(true)
  }
}

def off() {
	logMethod("off")

  if (getIsSuperChlorinator()) {
    enableSuperChlorinator(false)
  } else {
    enableChlorinator(false)
  }
}

def setLevel(level) {
	logMethod("setLevel", "Arguments", [level])
  setChlorinatorLevel(level)
}

def enableChlorinator(enable) {
	logMethod("enableChlorinator", "Arguments", [enable])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "ChlorID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "Enabled", dataType: "bool", value: enable]
  ]
  parent.performApiRequest("SetCHLOREnable", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = enable ? "on" : "off"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def setChlorinatorLevel(level) {
	logMethod("setChlorinatorLevel", "Arguments", [level])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "ChlorID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "CfgState", dataType: "byte", value: 3],
    [name: "OpMode", dataType: "byte", value: 1],
    [name: "BOWType", dataType: "byte", value: device.currentValue("bowType")],
    [name: "CellType", dataType: "byte", value:  device.currentValue("cellType")],
    [name: "TimedPercent", dataType: "byte", value: level],
    [name: "SCTimeout", dataType: "byte", value: 24],
    [name: "ORPTimout", dataType: "byte", value: 24]
  ]
  parent.performApiRequest("SetCHLORParams", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = enable ? "on" : "off"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: "level", value: level, displayed: true, isStateChange: true)
    }
  }
}

def enableSuperChlorinator(enable) {
	logMethod("enableSuperChlorinator", "Arguments", [enable])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "ChlorID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "IsOn", dataType: "int", value: enable ? 1 : 0]
  ]

  parent.performApiRequest("SetUISuperCHLORCmd", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = enable ? "on" : "off"
      def level = enable ? 100 : 0
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: "level", value: level, displayed: true, isStateChange: true)
    }
  }
}

def getIsSuperChlorinator() {
  return device.currentValue("isSuperChlorinator") == 1
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
