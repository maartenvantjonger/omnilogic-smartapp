/**
 *  OmniLogic Pump
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: "OmniLogic Pump",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger",
    ocfDeviceType: "oic.d.watervalve"
  ) {
    capability "Switch"
    capability "Actuator"
    capability "Refresh"

    attribute "bowId", "number"
    attribute "omnilogicId", "number"
    attribute "pumpState", "number"
    attribute "pumpSpeed", "number"
    attribute "isSpillover", "number"
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
  sendEvent(name: "isSpillover", value: attributes["isSpillover"], displayed: true)
}

def refresh() {
  logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
  logMethod("parseStatus", "Arguments", [deviceStatus])

  def pumpState = deviceStatus?.@pumpState?.text() ?: deviceStatus?.@filterState?.text()
  def enabled = pumpState == "1" && (!getIsSpillover() || valvePosition == 3)
  def onOff = enabled ? "on" : "off"
  sendEvent(name: "switch", value: onOff, displayed: true)

  def pumpSpeed = deviceStatus?.@pumpSpeed?.text().toInteger()
  sendEvent(name: "pumpSpeed", value: pumpSpeed, displayed: true)

  def lastSpeed = deviceStatus?.@lastSpeed?.text().toInteger()
  sendEvent(name: "lastSpeed", value: lastSpeed, displayed: true)
}

def on() {
  logMethod("on")
  setState(true)
}

def off() {
  logMethod("off")
  setState(false)
}

def setState(enabled) {
  logMethod("setPumpState", "Arguments", [enabled])

  if (getIsSpillover()) {
    setSpilloverPumpState(enabled)
  } else {
    setPumpState(enabled)
  }
}

def setPumpState(enabled) {
    logMethod("setPumpState", "Arguments", [enabled])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "EquipmentID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "IsOn", dataType: "int", value: enabled ? 100 : 0],
    [name: "IsCountDownTimer", dataType: "bool", value: false],
    [name: "StartTimeHours", dataType: "int", value: 0],
    [name: "StartTimeMinutes", dataType: "int", value: 0],
    [name: "EndTimeHours", dataType: "int", value: 0],
    [name: "EndTimeMinutes", dataType: "int", value: 0],
    [name: "DaysActive", dataType: "int", value: 0],
    [name: "Recurring", dataType: "bool", value: false]
  ]

  parent.performApiRequest("SetUIEquipmentCmd", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = enabled ? "on" : "off"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def setSpilloverPumpState(enabled) {
  logMethod("setSpilloverPumpState", "Arguments", [enabled])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "EquipmentID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "Speed", dataType: "int", value: enabled ? 100 : 0],
    [name: "IsCountDownTimer", dataType: "bool", value: false],
    [name: "StartTimeHours", dataType: "int", value: 0],
    [name: "StartTimeMinutes", dataType: "int", value: 0],
    [name: "EndTimeHours", dataType: "int", value: 0],
    [name: "EndTimeMinutes", dataType: "int", value: 0],
    [name: "DaysActive", dataType: "int", value: 0],
    [name: "Recurring", dataType: "bool", value: false]
  ]

  parent.performApiRequest("SetUISpilloverCmd", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = enabled ? "on" : "off"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def getIsSpillover() {
  return device.currentValue("isSpillover") == 1
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
