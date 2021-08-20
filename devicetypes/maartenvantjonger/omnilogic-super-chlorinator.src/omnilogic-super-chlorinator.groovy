/**
 *  Omnilogic Super Chlorinator
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: "Omnilogic Super Chlorinator",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger"
  ) {
    capability "Switch"
    capability "Actuator"
    capability "Refresh"

    attribute "bowId", "number"
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
  }

  tiles {
    standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, decoration: "flat") {
      state("off", label: "${name}", action: "on")
      state("on", label: "${name}", action: "off")
    }

    main("switch")
    details(["switch"])
  }
}

def initialize(omnilogicId, attributes) {
  logMethod("initialize", "Arguments", [omnilogicId, attributes])

  sendEvent(name: "omnilogicId", value: omnilogicId, displayed: true)
  sendEvent(name: "bowId", value: attributes["bowId"], displayed: true)
}

def refresh() {
  logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
  logMethod("parseStatus", "Arguments", [deviceStatus])

  def onOff = deviceStatus.@scMode.text() == "1" ? "on" : "off"
  sendEvent(name: "switch", value: onOff, displayed: true)

  def operatingState = deviceStatus.@operatingState.text()
  sendEvent(name: "operatingState", value: operatingState, displayed: true)

  def operatingMode = deviceStatus.@operatingMode.text()
  sendEvent(name: "operatingMode", value: operatingMode, displayed: true)

  def status = deviceStatus.@status.text()
  sendEvent(name: "status", value: status, displayed: true)

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
  enableSuperChlorinator(true)
}

def off() {
  logMethod("off")
  enableSuperChlorinator(false)
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
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
